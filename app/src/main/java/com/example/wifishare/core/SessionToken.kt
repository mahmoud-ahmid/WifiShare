package com.example.wifishare.core

import java.security.SecureRandom

/** توكن جلسة عشوائي 128-bit يُعرض داخل QR ويُطلب من المرسل. */
object SessionToken {
    private val rnd = SecureRandom()
    @Volatile private var value: String = generate()
    @Volatile private var createdAt: Long = System.currentTimeMillis()

    val current: String get() = value

    fun regenerate(): String {
        value = generate()
        createdAt = System.currentTimeMillis()
        return value
    }

    fun isExpired(): Boolean =
        System.currentTimeMillis() - createdAt > Constants.QR_SESSION_TTL_MS

    private fun generate(): String {
        val b = ByteArray(16)
        rnd.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
