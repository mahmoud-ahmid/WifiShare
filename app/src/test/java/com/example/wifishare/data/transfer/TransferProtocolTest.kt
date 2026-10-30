package com.example.wifishare.data.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class TransferProtocolTest {

    private fun pair(): Pair<Socket, Socket> {
        val ss = ServerSocket(0)
        val c = Socket("127.0.0.1", ss.localPort)
        val s = ss.accept()
        ss.close()
        c.soTimeout = 5000
        s.soTimeout = 5000
        return c to s
    }

    @Test
    fun encryptedRoundTrip() {
        val (c, s) = pair()
        var server: TransferProtocol.Session? = null
        val t = Thread {
            server = TransferProtocol.serverHandshake(s.getInputStream(), s.getOutputStream(), listOf("tok"))
        }
        t.start()
        val client = TransferProtocol.clientHandshake(c.getInputStream(), c.getOutputStream(), "tok")
        t.join()
        val srv = server
        assertNotNull(srv)

        val data = ByteArray(300_000) { (it * 31).toByte() }
        client.out.write(data); client.out.flush()
        val got = ByteArray(data.size)
        srv!!.input.readFully(got)
        assertArrayEquals(data, got)

        srv.out.writeByte(7); srv.out.flush()
        assertEquals(7, client.input.readByte().toInt())
        c.close(); s.close()
    }

    @Test
    fun wrongTokenIsRejected() {
        val (c, s) = pair()
        var server: TransferProtocol.Session? = null
        val t = Thread {
            server = try {
                TransferProtocol.serverHandshake(s.getInputStream(), s.getOutputStream(), listOf("right"))
            } catch (_: Exception) { null }
        }
        t.start()
        assertThrows(IOException::class.java) {
            TransferProtocol.clientHandshake(c.getInputStream(), c.getOutputStream(), "wrong")
        }
        t.join()
        assertNull(server)
        c.close(); s.close()
    }

    @Test
    fun p2pSecretAcceptedWhenListed() {
        val (c, s) = pair()
        var server: TransferProtocol.Session? = null
        val t = Thread {
            server = TransferProtocol.serverHandshake(
                s.getInputStream(), s.getOutputStream(), listOf("tok", TransferProtocol.P2P_PSK)
            )
        }
        t.start()
        TransferProtocol.clientHandshake(c.getInputStream(), c.getOutputStream(), TransferProtocol.P2P_PSK)
        t.join()
        assertNotNull(server)
        c.close(); s.close()
    }
}
