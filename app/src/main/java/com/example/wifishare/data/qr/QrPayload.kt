package com.example.wifishare.data.qr

import com.example.wifishare.core.Constants
import java.net.InetAddress

/**
 * البيانات المُشفّرة في QR:
 * WSQR:<name>|<ip>|<port>|<mac?>|<token>
 * يُرفض أي QR بدون توكن أو بعنوان IP غير محلي أو بمنفذ مختلف.
 */
data class QrPayload(
    val name: String,
    val ip: String,
    val port: Int,
    val mac: String? = null,
    val token: String = "" ) {
    fun encode(): String = buildString {
        append(PREFIX)
        append(name.replace("|", "_").replace("\n", " "))
        append('|'); append(ip)
        append('|'); append(port)
        append('|'); append(mac ?: "")
        append('|'); append(token)
    }

    companion object {
        const val PREFIX = "WSQR:"
        private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        private val TOKEN = Regex("^[0-9a-f]{32}$")

        fun decode(raw: String): QrPayload? {
            if (!raw.startsWith(PREFIX)) return null
            val p = raw.removePrefix(PREFIX).split('|')
            if (p.size < 5) return null
            return try {
                val ip = p[1].trim()
                val port = p[2].trim().toInt()
                val token = p[4].trim()
                if (!IPV4.matches(ip) || !TOKEN.matches(token)) return null
                if (port != Constants.TRANSFER_PORT) return null
                val a = InetAddress.getByName(ip)   // IP حرفي، لا DNS
                if (!(a.isSiteLocalAddress || a.isLinkLocalAddress)) return null
                QrPayload(
                    name = p[0].trim().ifBlank { "Android" }.take(40),
                    ip = ip,
                    port = port,
                    mac = p[3].trim().takeIf { it.isNotBlank() },
                    token = token
                )
            } catch (_: Exception) { null }
        }
    }
}
