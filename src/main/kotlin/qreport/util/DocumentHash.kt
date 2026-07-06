package net.calvuz.qreport.util

import java.security.MessageDigest

/**
 * SHA-256 hash utility for document file content — server side.
 *
 * Computes the hash exclusively on raw bytes.
 * Called by DocumentRoutes after receiving a file upload to produce
 * the authoritative hash stored in PostgreSQL.
 */
object DocumentHash {

    private const val ALGORITHM = "SHA-256"

    /**
     * Computes SHA-256 over [bytes] and returns the lowercase hex string.
     */
    fun compute(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies [bytes] against [expectedHash].
     * Returns true if they match.
     */
    fun verify(bytes: ByteArray, expectedHash: String): Boolean =
        compute(bytes) == expectedHash
}