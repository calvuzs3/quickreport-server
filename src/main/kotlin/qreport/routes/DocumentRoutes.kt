package net.calvuz.qreport.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.calvuz.qreport.repository.DocumentServerRepository
import net.calvuz.qreport.storage.DocumentStorageProvider
import net.calvuz.qreport.storage.StorageResult
import net.calvuz.qreport.util.DocumentHash
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("DocumentRoutes")

/**
 * Document file sync routes.
 *
 * All routes are mounted under /documents and protected by JWT
 * (authentication is enforced by the authenticate("jwt") block in Routing.kt).
 *
 * Routes:
 *  GET  /documents/manifest          →  {id, fileHash, fileSize} list for diff
 *  POST /documents/upload/{id}       →  upload file bytes (multipart/form-data)
 *  GET  /documents/download/{id}     →  download file bytes
 *
 * The metadata record (title, category, scope FK, etc.) travels through the
 * existing /sync/push + /sync/pull JSON channel — these routes handle bytes only.
 */
fun Route.documentRoutes(
    repository: DocumentServerRepository,
    storage: DocumentStorageProvider
) {

    route("/documents") {

        // ── GET /documents/manifest ───────────────────────────────────────────
        //
        // Returns the list of {id, fileHash, fileSize} for all non-deleted
        // documents that have a stored file (file_hash IS NOT NULL).
        // The Android client uses this to compute the diff: upload/download/skip.

        get("/manifest") {
            try {
                val manifest = repository.getManifest()
                log.info("manifest: returning ${manifest.size} entries")
                call.respond(HttpStatusCode.OK, manifest)
            } catch (e: Exception) {
                log.error("manifest: failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Manifest query failed")
            }
        }

        // ── POST /documents/upload/{id} ───────────────────────────────────────
        //
        // Receives the file as multipart/form-data with a single part named "file".
        // The server computes the authoritative SHA-256 hash and stores it in
        // PostgreSQL. Returns 400 if the metadata record does not exist yet
        // (sync JSON must arrive before file upload).

        post("/upload/{id}") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing document id")

            // Verify metadata record exists (must arrive via sync JSON first)
            if (!repository.exists(id)) {
                log.warn("upload: metadata not found for id=$id — sync JSON must precede upload")
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "Document metadata not found. Run entity sync before uploading files."
                )
            }

            // Parse multipart
            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null
            var mimeType = "application/octet-stream"

            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    fileBytes = part.streamProvider().readBytes()
                    mimeType  = part.contentType?.toString() ?: mimeType
                }
                part.dispose()
            }

            val bytes = fileBytes
                ?: return@post call.respond(HttpStatusCode.BadRequest, "No 'file' part in multipart")

            // Enforce 50MB limit
            val maxBytes = 50L * 1024 * 1024
            if (bytes.size > maxBytes) {
                return@post call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    "File exceeds 50MB limit (${bytes.size} bytes)"
                )
            }

            // Compute authoritative hash on received bytes
            val hash = DocumentHash.compute(bytes)

            // Store bytes
            when (val result = storage.store(id, bytes, mimeType)) {
                is StorageResult.Success -> {
                    // Stamp hash in PostgreSQL
                    repository.updateFileHash(
                        id             = id,
                        hash           = hash,
                        sizeBytes      = bytes.size.toLong(),
                        storageBackend = "local"
                    )
                    log.info("upload: stored id=$id hash=$hash size=${bytes.size}")
                    call.respond(HttpStatusCode.OK)
                }
                is StorageResult.Failure -> {
                    log.error("upload: storage failed for id=$id — ${result.reason}")
                    call.respond(HttpStatusCode.InternalServerError, "Storage failed: ${result.reason}")
                }
            }
        }

        // ── GET /documents/download/{id} ──────────────────────────────────────
        //
        // Streams the raw file bytes. Content-Type is set from the MIME type
        // stored in PostgreSQL at import time.
        // Returns 404 if either the metadata or the file bytes are missing.

        get("/download/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing document id")

            val bytes = storage.retrieve(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    "File not found for id=$id"
                )

            val mimeType = repository.getMimeType(id) ?: "application/octet-stream"

            log.info("download: serving id=$id mimeType=$mimeType size=${bytes.size}")
            call.respondBytes(
                bytes       = bytes,
                contentType = ContentType.parse(mimeType),
                status      = HttpStatusCode.OK
            )
        }
    }
}