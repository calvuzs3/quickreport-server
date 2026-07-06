package net.calvuz.qreport.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.calvuz.qreport.repository.PhotoServerRepository
import net.calvuz.qreport.storage.DocumentStorageProvider
import net.calvuz.qreport.storage.StorageResult
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("PhotoRoutes")

/**
 * Photo file sync routes.
 *
 * All routes are mounted under /photos and protected by JWT
 * (authentication is enforced by the authenticate("jwt") block in Routing.kt).
 *
 * Routes:
 *  GET  /photos/manifest       →  {id, fileHash, fileSize} list for diff
 *  POST /photos/upload/{id}    →  upload file bytes (multipart/form-data)
 *  GET  /photos/download/{id}  →  download file bytes
 *
 * The metadata record (check_item_id, caption, order_index, etc.) travels
 * through the existing /sync/push + /sync/pull JSON channel — these routes
 * handle bytes only. Mirror of documentRoutes; see DocumentRoutes.kt.
 */
fun Route.photoRoutes(
    repository: PhotoServerRepository,
    storage: DocumentStorageProvider
) {

    route("/photos") {

        // ── GET /photos/manifest ──────────────────────────────────────────────
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

        // ── POST /photos/upload/{id} ───────────────────────────────────────────
        //
        // Receives the file as multipart/form-data with a single part named "file".
        // Returns 422 if the metadata record does not exist yet (sync JSON must
        // arrive before file upload).

        post("/upload/{id}") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing photo id")

            if (!repository.exists(id)) {
                log.warn("upload: metadata not found for id=$id — sync JSON must precede upload")
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "Photo metadata not found. Run entity sync before uploading files."
                )
            }

            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    fileBytes = part.streamProvider().readBytes()
                }
                part.dispose()
            }

            val bytes = fileBytes
                ?: return@post call.respond(HttpStatusCode.BadRequest, "No 'file' part in multipart")

            // Enforce 50MB limit (same ceiling as documents; photos are far smaller in practice)
            val maxBytes = 50L * 1024 * 1024
            if (bytes.size > maxBytes) {
                return@post call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    "File exceeds 50MB limit (${bytes.size} bytes)"
                )
            }

            when (val result = storage.store(id, bytes, "image/jpeg")) {
                is StorageResult.Success -> {
                    log.info("upload: stored id=$id size=${bytes.size}")
                    call.respond(HttpStatusCode.OK)
                }
                is StorageResult.Failure -> {
                    log.error("upload: storage failed for id=$id — ${result.reason}")
                    call.respond(HttpStatusCode.InternalServerError, "Storage failed: ${result.reason}")
                }
            }
        }

        // ── GET /photos/download/{id} ──────────────────────────────────────────
        //
        // Streams the raw file bytes. Photos are always JPEG (see
        // PhotoStorageManager on the Android client).

        get("/download/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing photo id")

            val bytes = storage.retrieve(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    "File not found for id=$id"
                )

            log.info("download: serving id=$id size=${bytes.size}")
            call.respondBytes(
                bytes       = bytes,
                contentType = ContentType.Image.JPEG,
                status      = HttpStatusCode.OK
            )
        }
    }
}
