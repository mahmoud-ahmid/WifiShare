package com.example.wifishare.data.transfer

import com.example.wifishare.domain.model.FileMeta
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * بروتوكول النقل (ثابت عبر الإصدارات):
 *
 *   FILE  := writeUTF(name) + writeLong(size) + body(size bytes)
 *   AFTER := recv confirm(1 byte)            // 1=OK, 0=FAIL
 *   END   := writeUTF("__END__") + writeLong(0)
 *
 * كل التدفقات Data Streams (big-endian) — portable بين كل الأجهزة.
 */
object TransferProtocol {
    const val END_MARKER = "__END__"
    const val CONFIRM_OK: Int = 1
    const val CONFIRM_FAIL: Int = 0

    fun wrapOut(raw: OutputStream): DataOutputStream =
        DataOutputStream(BufferedOutputStream(raw, 32 * 1024))

    fun wrapIn(raw: InputStream): DataInputStream =
        DataInputStream(BufferedInputStream(raw, 32 * 1024))

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
