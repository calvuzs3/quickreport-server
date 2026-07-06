package net.calvuz.qreport

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.calvuz.qreport.database.DatabaseFactory
import net.calvuz.qreport.plugins.configureRouting
import net.calvuz.qreport.plugins.configureSecurity
import net.calvuz.qreport.plugins.configureSerialization
import net.calvuz.qreport.repository.DocumentServerRepository
import net.calvuz.qreport.repository.ExposedCrudRepository
import net.calvuz.qreport.repository.PhotoServerRepository
import net.calvuz.qreport.repository.SyncServerRepository
import net.calvuz.qreport.routes.configureAdminRoutes
import net.calvuz.qreport.routes.configureCrudRoutes
import net.calvuz.qreport.storage.LocalFileStorageProvider

fun main() {
    embeddedServer(
        factory = Netty,
        configure = {
            connector {
                host = "0.0.0.0"
                port = System.getenv("PORT")?.toInt() ?: 8080
            }
        }
    ) {
        val crudRepo = ExposedCrudRepository()

        DatabaseFactory.init()
        configureSecurity()
        configureSerialization()

        val syncRepository     = SyncServerRepository()
        val documentRepository = DocumentServerRepository()
        val documentStorage    = LocalFileStorageProvider(
            basePath = System.getenv("DOCUMENTS_PATH") ?: "/opt/qreport/documents"
        )
        val photoStorage    = LocalFileStorageProvider(
            basePath = System.getenv("PHOTOS_PATH") ?: "/opt/qreport/photos"
        )
        val photoRepository = PhotoServerRepository(photoStorage)

        configureRouting(syncRepository, documentRepository, documentStorage, photoRepository, photoStorage)
        configureCrudRoutes(crudRepo)
        configureAdminRoutes()
    }.start(wait = true)
}

