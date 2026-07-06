package net.calvuz.qreport.storage

import java.io.File

// ── Result type ───────────────────────────────────────────────────────────────

sealed class StorageResult {
    data class Success(val path: String) : StorageResult()
    data class Failure(val reason: String) : StorageResult()
}

// ── Interface ─────────────────────────────────────────────────────────────────

/**
 * Abstracts file storage on the server.
 *
 * Today: [LocalFileStorageProvider] — files on the VM filesystem.
 * Future: S3StorageProvider, MinioStorageProvider — same interface, new binding.
 *
 * Switching storage backend requires only:
 *  1. A new implementation of this interface.
 *  2. Changing the binding in Application.kt (or a DI module if added later).
 *  Zero changes to routes or repository.
 */
interface DocumentStorageProvider {

    /**
     * Stores [bytes] under the given document [id].
     * Overwrites any existing file with the same id.
     */
    fun store(id: String, bytes: ByteArray, mimeType: String): StorageResult

    /**
     * Returns the raw bytes for [id], or null if not found.
     */
    fun retrieve(id: String): ByteArray?

    /**
     * Deletes the file for [id].
     * Returns true if the file was deleted, false if it did not exist.
     */
    fun delete(id: String): Boolean

    /**
     * Returns true if a file exists for [id].
     */
    fun exists(id: String): Boolean
}

// ── Local implementation ──────────────────────────────────────────────────────

/**
 * Stores documents on the VM filesystem under [basePath].
 *
 * Files are stored flat by document id:
 *   /opt/qreport/documents/{id}
 *
 * No sub-directories by scope or entity — the scope/FK fields are in
 * PostgreSQL, not in the path. This keeps the storage layout simple
 * and independent of the domain hierarchy.
 */
class LocalFileStorageProvider(
    private val basePath: String = "/opt/qreport/documents"
) : DocumentStorageProvider {

    init {
        File(basePath).mkdirs()
    }

    override fun store(id: String, bytes: ByteArray, mimeType: String): StorageResult {
        return try {
            val file = File(basePath, id)
            file.writeBytes(bytes)
            StorageResult.Success(file.absolutePath)
        } catch (e: Exception) {
            StorageResult.Failure(e.message ?: "Write failed for id=$id")
        }
    }

    override fun retrieve(id: String): ByteArray? {
        val file = File(basePath, id)
        return if (file.exists()) file.readBytes() else null
    }

    override fun delete(id: String): Boolean {
        return File(basePath, id).delete()
    }

    override fun exists(id: String): Boolean {
        return File(basePath, id).exists()
    }
}