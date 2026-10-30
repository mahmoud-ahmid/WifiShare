package com.example.wifishare.data.qr

import com.example.wifishare.core.Constants
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * WSQR:<name>|<ip>|<port>|<mac?>|<token>|<ssid?>|<pass?>
 * ssid/pass (URL-encoded) اختياريان: إن وُجدا يتصل المرسل بالنقطة تلقائيًا.
 */
data class QrPayload(
    val name: String,
    val ip: String,
    val port: Int,
    val mac: String? = null,
    val token: String = "",
    val ssid: String = "",
    val pass: String = ""
) {
    fun encode(): String = buildString {
        append(PREFIX)
        append(name.replace("|", "_").replace("\n", " "))
        append('|'); append(ip)
        append('|'); append(port)
        append('|'); append(mac ?: "")
        append('|'); append(token)
        append('|'); append(URLEncoder.encode(ssid, "UTF-8"))
        append('|'); append(URLEncoder.encode(pass, "UTF-8"))
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
                val a = InetAddress.getByName(ip)
                if (!(a.isSiteLocalAddress || a.isLinkLocalAddress)) return null
                val ssid = URLDecoder.decode(p.getOrNull(5).orEmpty(), "UTF-8")
                val pass = URLDecoder.decode(p.getOrNull(6).orEmpty(), "UTF-8")
                if (ssid.length > 32 || pass.length > 63) return null
                if (ssid.isNotEmpty() && pass.length < 8) return null
                QrPayload(
                    name = p[0].trim().ifBlank { "Android" }.take(40),
                    ip = ip,
                    port = port,
                    mac = p[3].trim().takeIf { it.isNotBlank() },
                    token = token,
                    ssid = ssid,
                    pass = pass
                )
            } catch (_: Exception) { null }
        }
    }
}
