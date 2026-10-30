package com.example.wifishare.data.transfer

import com.example.wifishare.domain.model.FileMeta
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * WSHARE2: مصافحة HMAC متبادلة (لا يُرسَل التوكن أبدًا) ثم AES-256-CTR بمفاتيح لكل اتجاه،
 * وكل ملف يليه SHA-256. السر = توكن الـ QR، أو P2P_PSK على رابط Wi-Fi Direct.
 */
object TransferProtocol {
    const val END_MARKER = "__END__"
    const val CONFIRM_OK: Int = 1
    const val CONFIRM_FAIL: Int = 0
    const val MAGIC = "WSHARE2"
    const val P2P_PSK = "wifishare-p2p-link"
    const val NONCE_LEN = 16
    const val HASH_LEN = 32
    private const val BUF = 256 * 1024
    private val rnd = SecureRandom()

    class Session(val input: DataInputStream, val out: DataOutputStream)

    fun newDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun newNonce(): ByteArray = ByteArray(NONCE_LEN).also { rnd.nextBytes(it) }

    private fun hmac(key: String, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        for (p in parts) mac.update(p)
        return mac.doFinal()
    }

    private fun cipherFor(psk: String, label: String, nc: ByteArray, ns: ByteArray, mode: Int): Cipher {
        val key = hmac(psk, "key-$label".toByteArray(), nc, ns)
        val iv = hmac(psk, "iv-$label".toByteArray(), nc, ns).copyOf(16)
        return Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
    }

    private class CtrIn(private val src: InputStream, private val c: Cipher) : InputStream() {
        private var tmp = ByteArray(0)
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else (b[0].toInt() and 0xff)
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = src.read(b, off, len)
            if (n <= 0) return n
            if (tmp.size < n) tmp = ByteArray(n)
            val m = c.update(b, off, n, tmp, 0)
            System.arraycopy(tmp, 0, b, off, m)
            return m
        }
    }

    private class CtrOut(private val dst: OutputStream, private val c: Cipher) : OutputStream() {
        override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            val enc = c.update(b, off, len)
            if (enc != null) dst.write(enc)
        }
        override fun flush() { dst.flush() }
        override fun close() { dst.close() }
    }

    private fun wrap(base: InputStream, baseOut: OutputStream, inC: Cipher, outC: Cipher): Session =
        Session(
            DataInputStream(BufferedInputStream(CtrIn(base, inC), BUF)),
            DataOutputStream(BufferedOutputStream(CtrOut(baseOut, outC), BUF))
        )

    fun clientHandshake(rawIn: InputStream, rawOut: OutputStream, psk: String): Session {
        val base = BufferedInputStream(rawIn, 8192)
        val baseOut = BufferedOutputStream(rawOut, 8192)
        val hi = DataInputStream(base)
        val ho = DataOutputStream(baseOut)

        val nc = newNonce()
        ho.writeUTF(MAGIC); ho.write(nc); ho.flush()
        val ns = ByteArray(NONCE_LEN); hi.readFully(ns)
        ho.write(hmac(psk, "c-auth".toByteArray(), nc, ns)); ho.flush()
        if (hi.readByte().toInt() != 1) {
            throw IOException("Receiver refused the connection (invalid or expired code)")
        }
        val proof = ByteArray(HASH_LEN); hi.readFully(proof)
        if (!MessageDigest.isEqual(proof, hmac(psk, "s-auth".toByteArray(), nc, ns))) {
            throw IOException("Receiver authentication failed")
        }
        return wrap(
            base, baseOut,
            cipherFor(psk, "s2c", nc, ns, Cipher.DECRYPT_MODE),
            cipherFor(psk, "c2s", nc, ns, Cipher.ENCRYPT_MODE)
        )
    }

    fun serverHandshake(rawIn: InputStream, rawOut: OutputStream, candidates: List<String>): Session? {
        val base = BufferedInputStream(rawIn, 8192)
        val baseOut = BufferedOutputStream(rawOut, 8192)
        val hi = DataInputStream(base)
        val ho = DataOutputStream(baseOut)

        if (hi.readUTF() != MAGIC) return null
        val nc = ByteArray(NONCE_LEN); hi.readFully(nc)
        val ns = newNonce()
        ho.write(ns); ho.flush()
        val proof = ByteArray(HASH_LEN); hi.readFully(proof)
        val psk = candidates.firstOrNull {
            MessageDigest.isEqual(proof, hmac(it, "c-auth".toByteArray(), nc, ns))
        }
        if (psk == null) { ho.writeByte(0); ho.flush(); return null }
        ho.writeByte(1)
        ho.write(hmac(psk, "s-auth".toByteArray(), nc, ns)); ho.flush()
        return wrap(
            base, baseOut,
            cipherFor(psk, "c2s", nc, ns, Cipher.DECRYPT_MODE),
            cipherFor(psk, "s2c", nc, ns, Cipher.ENCRYPT_MODE)
        )
    }

    fun writeHeader(out: DataOutputStream, meta: FileMeta) {
        out.writeUTF(meta.name)
        out.writeLong(meta.size)
        out.flush()
    }

    fun readHeader(input: DataInputStream): FileMeta {
        val name = input.readUTF()
        val size = input.readLong()
        return FileMeta(name = name, size = size)
    }

    fun writeEnd(out: DataOutputStream) {
        out.writeUTF(END_MARKER)
        out.writeLong(0L)
        out.flush()
    }

    fun isEnd(meta: FileMeta): Boolean = meta.name == END_MARKER

    fun writeConfirm(out: DataOutputStream, ok: Boolean) {
        out.writeByte(if (ok) CONFIRM_OK else CONFIRM_FAIL)
        out.flush()
    }

    fun readConfirm(input: DataInputStream): Boolean = input.readByte().toInt() == CONFIRM_OK
}
