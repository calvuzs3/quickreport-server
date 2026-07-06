package net.calvuz.qreport.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Manifest entry returned by GET /documents/manifest.
 * Contains only the identity and content fingerprint — no metadata.
 */
@Serializable
data class DocumentManifestEntry(
    val id: String,
    @SerialName("file_hash") val fileHash: String,
    @SerialName("file_size") val fileSize: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// Also add DocumentDto to SyncDto.kt in the model package:
//
// @Serializable
// data class DocumentDto(
//     val id: String,
//     val scope: String,
//     @SerialName("island_id")   val islandId: String?   = null,
//     @SerialName("facility_id") val facilityId: String? = null,
//     @SerialName("client_id")   val clientId: String?   = null,
//     @SerialName("file_name")   val fileName: String,
//     @SerialName("file_size")   val fileSize: Long,
//     @SerialName("mime_type")   val mimeType: String,
//     @SerialName("file_hash")   val fileHash: String?   = null,
//     val title: String,
//     val category: String,
//     val notes: String?         = null,
//     @SerialName("created_at")  val createdAt: Long,
//     @SerialName("updated_at")  val updatedAt: Long,
//     @SerialName("is_active")   val isActive: Boolean,
//     @SerialName("is_deleted")  val isDeleted: Boolean,
//     @SerialName("synced_at")   val syncedAt: Long?     = null
//     // filePath intentionally absent — local to each device
// )
//
// And add to SyncPayload:
//     val documents: List<DocumentDto> = emptyList()
// ─────────────────────────────────────────────────────────────────────────────