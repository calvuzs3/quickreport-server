package net.calvuz.qreport.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Manifest entry returned by GET /photos/manifest.
 * Contains only the identity and content fingerprint — no metadata.
 *
 * Unlike documents, the hash is computed on the fly from the stored bytes
 * (not persisted in PostgreSQL) — photos are small enough that re-hashing
 * on every manifest request is cheap, and it avoids a schema column.
 */
@Serializable
data class PhotoManifestEntry(
    val id: String,
    @SerialName("file_hash") val fileHash: String,
    @SerialName("file_size") val fileSize: Long
)
