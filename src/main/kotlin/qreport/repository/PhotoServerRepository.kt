package net.calvuz.qreport.repository

import net.calvuz.qreport.model.PhotoManifestEntry
import net.calvuz.qreport.storage.DocumentStorageProvider
import net.calvuz.qreport.util.DocumentHash
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Server-side repository for photo file sync operations.
 *
 * Handles only what [net.calvuz.qreport.routes.photoRoutes] needs:
 *  - [getManifest] — list {id, fileHash, fileSize} for diff
 *  - [exists]      — precondition check before accepting an upload
 *
 * Metadata (check_item_id, caption, order_index...) is handled by
 * [SyncServerRepository] via the normal push/pull JSON channel — the
 * `photos` table is wholesale-replaced there per touched check item.
 *
 * [storage] is reused generically (same interface/impl as documents, just a
 * different base path) — see Application.kt wiring.
 */
class PhotoServerRepository(private val storage: DocumentStorageProvider) {

    /**
     * Returns {id, fileHash, fileSize} for every photo row that has a
     * stored file. Photos whose metadata arrived via sync but whose bytes
     * haven't been uploaded yet are excluded (storage.retrieve returns null).
     */
    fun getManifest(): List<PhotoManifestEntry> {
        val rows = transaction {
            Photos.selectAll().map { it[Photos.id] to it[Photos.fileSize] }
        }
        return rows.mapNotNull { (id, fileSize) ->
            val bytes = storage.retrieve(id) ?: return@mapNotNull null
            PhotoManifestEntry(
                id = id,
                fileHash = DocumentHash.compute(bytes),
                fileSize = fileSize
            )
        }
    }

    /**
     * Returns true if a metadata record exists for [id] (regardless of
     * whether the file bytes have been uploaded).
     */
    fun exists(id: String): Boolean {
        return transaction {
            Photos.selectAll().where { Photos.id eq id }.count() > 0
        }
    }
}
