package net.calvuz.qreport.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.calvuz.qreport.repository.DocumentServerRepository
import net.calvuz.qreport.repository.PhotoServerRepository
import net.calvuz.qreport.repository.SyncServerRepository
import net.calvuz.qreport.routes.authRoutes
import net.calvuz.qreport.routes.documentRoutes
import net.calvuz.qreport.routes.photoRoutes
import net.calvuz.qreport.routes.syncRoutes
import net.calvuz.qreport.storage.DocumentStorageProvider

private const val APP_VERSION = "1.0.0"

fun Application.configureRouting(
    syncRepository: SyncServerRepository,
    documentRepository: DocumentServerRepository,
    documentStorage: DocumentStorageProvider,
    photoRepository: PhotoServerRepository,
    photoStorage: DocumentStorageProvider

) {
    routing {
        get("/api/version") {
            call.respond(mapOf("name" to "quickreport-server", "version" to APP_VERSION))
        }

        authRoutes()
        syncRoutes(syncRepository)

        // Protected routes (JWT required)
        authenticate("jwt-auth") {
            syncRoutes(syncRepository)
            documentRoutes(documentRepository, documentStorage)   // ← ADD
            photoRoutes(photoRepository, photoStorage)
        }

    }
}

