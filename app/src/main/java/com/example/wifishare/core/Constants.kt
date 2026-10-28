package com.example.wifishare.core

object Constants {
    const val TRANSFER_PORT: Int = 8988
    const val SOCKET_TIMEOUT_MS: Long = 60_000L
    const val CONNECT_TIMEOUT_MS: Long = 15_000L
    const val SERVER_ACCEPT_TIMEOUT_MS: Long = 120_000L
    const val CHUNK_SIZE: Int = 64 * 1024
    const val NOTIFICATION_ID_TRANSFER: Int = 1001
    const val DOWNLOAD_SUBDIR: String = "WifiShare"
    const val QR_SESSION_TTL_MS: Long = 5 * 60 * 1000L
    const val PROVIDER_AUTHORITY: String = "com.example.wifishare.fileprovider"
}
