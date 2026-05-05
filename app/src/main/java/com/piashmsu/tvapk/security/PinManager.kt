package com.piashmsu.tvapk.security

import java.security.MessageDigest

/**
 * SHA-256 hashing helper for the optional Adult / Locked-group PIN. The PIN
 * itself is never stored in plaintext; only a digest is persisted in
 * DataStore. Treat this as a small inconvenience-grade lock — it stops
 * casual snooping but is *not* a security boundary.
 */
object PinManager {

    fun isPinSet(hash: String): Boolean = hash.isNotBlank()

    fun hash(pin: String): String {
        val bytes = pin.trim().encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        return hash(pin).equals(storedHash, ignoreCase = true)
    }
}
