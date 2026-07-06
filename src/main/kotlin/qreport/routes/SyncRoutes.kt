package net.calvuz.qreport.routes

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.calvuz.qreport.model.SyncPayload
import net.calvuz.qreport.model.SyncResponse
import net.calvuz.qreport.repository.SyncServerRepository

fun Route.syncRoutes(repository: SyncServerRepository) {

    authenticate("jwt-auth") {

        // Pull: returns all records updated after ?since=<timestamp>
        get("/sync/pull") {
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val payload = repository.pull(since)
            call.respond(payload)
        }

        // Push: receives local changes, upserts them, returns accepted ids + pull payload
        post("/sync/push") {
            val principal = call.principal<JWTPrincipal>()
            val isAdmin = principal?.payload?.getClaim("role")?.asString() == "ADMIN"

            val incoming = call.receive<SyncPayload>()
            val acceptedIds = repository.push(incoming, isAdmin)

            // Pull everything changed since the LAST sync of this device,
            // not since the current timestamp
            val pullSince = call.request.queryParameters["since"]?.toLongOrNull()
                ?: 0L

            val pullPayload = repository.pull(pullSince)

            call.respond(
                SyncResponse(
                    acceptedIds = acceptedIds,
                    pulledPayload = pullPayload
                )
            )
        }
    }
}