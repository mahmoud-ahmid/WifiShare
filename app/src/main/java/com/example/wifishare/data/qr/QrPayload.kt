package com.example.wifishare.data.qr

/**
 * البيانات المُشفّرة في QR. صيغة نصية بسيطة، قابلة للتوسيع.
 * WSQR:<name>|<ip>|<port>|<mac?>
 */
data class QrPayload(
    val name: String,
    val ip: String,
    val port: Int,
    val mac: String? = null
) {
    fun encode(): String = buildString {
        append(PREFIX)
        append(name.replace("|", "_").replace("\n", " "))
        append('|'); append(ip)
        append('|'); append(port)
        append('|'); append(mac ?: "")
    }

    companion object {
        const val PREFIX = "WSQR:"

        fun decode(raw: String): QrPayload? {
            if (!raw.startsWith(PREFIX)) return null
            val parts = raw.removePrefix(PREFIX).split('|')
            if (parts.size < 3) return null
            return try {
                QrPayload(
                    name = parts[0].trim().ifBlank { "Android" },
                    ip = parts[1].trim(),
                    port = parts[2].trim().toInt(),
                    mac = parts.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                )
            } catch (_: Exception) { null }
        }
    }
}
