package net.calvuz.qreport.repository

import net.calvuz.qreport.model.DocumentManifestEntry
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

// ── DTO ───────────────────────────────────────────────────────────────────────

// Reuse the model package for shared data classes
// (DocumentManifestEntry is defined in model/DocumentDto.kt)

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Server-side repository for document sync operations.
 *
 * Handles only the three operations needed by DocumentRoutes:
 *  - [getManifest]      — list {id, fileHash, fileSize} for diff
 *  - [updateFileHash]   — stamp hash + size after upload
 *  - [getMimeType]      — retrieve MIME type for download Content-Type header
 *
 * Full CRUD for island_documents metadata is handled by the existing
 * SyncServerRepository via the normal push/pull JSON channel.
 */
class DocumentServerRepository {

    /**
     * Returns {id, fileHash, fileSize} for all non-deleted documents
     * that have a stored file (file_hash IS NOT NULL).
     *
     * Documents whose metadata arrived via sync but whose file has not
     * been uploaded yet are excluded — they have file_hash = NULL.
     */
    fun getManifest(): List<DocumentManifestEntry> {
        return transaction {
            IslandDocuments
                .select {
                    IslandDocuments.isDeleted eq false
                    IslandDocuments.fileHash.isNotNull()
                }
                .map { row ->
                    DocumentManifestEntry(
                        id       = row[IslandDocuments.id],
                        fileHash = row[IslandDocuments.fileHash]!!,
                        fileSize = row[IslandDocuments.fileSize]
                    )
                }
        }
    }

    /**
     * Stamps the computed [hash] and actual [sizeBytes] after a successful upload.
     * Also records the [storageBackend] used (default "local").
     */
    fun updateFileHash(
        id: String,
        hash: String,
        sizeBytes: Long,
        storageBackend: String = "local"
    ) {
        transaction {
            IslandDocuments.update({ IslandDocuments.id eq id }) { row ->
                row[IslandDocuments.fileHash]       = hash
                row[IslandDocuments.fileSize]        = sizeBytes
                row[IslandDocuments.storageBackend]  = storageBackend
                row[IslandDocuments.updatedAt]       = System.currentTimeMillis()
            }
        }
    }

    /**
     * Returns the MIME type for [id], used as the Content-Type header on download.
     * Returns null if the document is not found.
     */
    fun getMimeType(id: String): String? {
        return transaction {
            IslandDocuments
                .select { IslandDocuments.id eq id }
                .singleOrNull()
                ?.get(IslandDocuments.mimeType)
        }
    }

    /**
     * Returns true if a metadata record exists for [id] (regardless of
     * whether the file bytes have been uploaded).
     */
    fun exists(id: String): Boolean {
        return transaction {
            IslandDocuments
                .select { IslandDocuments.id eq id }
                .count() > 0
        }
    }
}