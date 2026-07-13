package net.calvuz.qreport.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import net.calvuz.qreport.repository.CrudRepository

// ─── JsonObject → Map<String, Any?> helper ───────────────────────────────────
// Receive body as JsonObject (supported by kotlinx.serialization), then
// convert to Map so CrudRepository can work with plain Kotlin types.

private fun JsonObject.toMap(): Map<String, Any?> = mapValues { (_, v) ->
    when (v) {
        is JsonNull    -> null
        is JsonPrimitive -> when {
            v.isString     -> v.content
            v.content == "true" || v.content == "false" -> v.content.toBoolean()
            v.content.contains('.') -> v.content.toDoubleOrNull() ?: v.content
            else           -> v.content.toLongOrNull() ?: v.content
        }
        is JsonObject  -> v.toMap()
        is JsonArray   -> v.map { if (it is JsonObject) it.toMap() else it.toString() }
        else           -> v.toString()
    }
}

// ─── Map<String, Any?> → JsonObject helper ───────────────────────────────────

private fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
    forEach { (key, value) ->
        when (value) {
            null        -> put(key, JsonNull)
            is Boolean  -> put(key, JsonPrimitive(value))
            is Number   -> put(key, JsonPrimitive(value))
            is String   -> put(key, JsonPrimitive(value))
            is List<*>  -> put(key, JsonArray(
                value.filterIsInstance<Map<String, Any?>>().map { it.toJsonObject() }
            ))
            else        -> put(key, JsonPrimitive(value.toString()))
        }
    }
}

private fun listResponse(items: List<Map<String, Any?>>): JsonObject = buildJsonObject {
    put("data", JsonArray(items.map { it.toJsonObject() }))
    put("total", JsonPrimitive(items.size))
}

// ─── Registration ─────────────────────────────────────────────────────────────

fun Application.configureCrudRoutes(repo: CrudRepository) {
    routing {
        authenticate("jwt-auth") {
            islandTypeRoutes(repo)
            clientRoutes(repo)
            contactRoutes(repo)
            contractRoutes(repo)
            facilityRoutes(repo)
            islandRoutes(repo)
            mechanicalUnitRoutes(repo)
            maintenanceLogRoutes(repo)
            checkupRoutes(repo)
            moduleTypeRoutes(repo)
            criticalityLevelRoutes(repo)
            checkupStatusRoutes(repo)
            checkItemTemplateRoutes(repo)
        }
    }
}

// ─── Island Types ──────────────────────────────────────────────────────────
// "Delete" is a soft-deactivate (is_active = false), never a hard delete —
// facility_islands keeps a nullable FK to island_types.

private fun Route.islandTypeRoutes(repo: CrudRepository) {
    route("/api/island-types") {
        get {
            val includeInactive = call.request.queryParameters["all"]?.toBoolean() ?: false
            call.respond(listResponse(repo.getIslandTypes(includeInactive)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getIslandType(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertIslandType(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertIslandType(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteIslandType(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Clients ──────────────────────────────────────────────────────────────────

private fun Route.clientRoutes(repo: CrudRepository) {
    route("/api/clients") {
        get {
            val clientId = call.request.queryParameters["clientId"]
            call.respond(listResponse(repo.getClients(clientId)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getClient(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            validateClientData(body)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(HttpStatusCode.Created, repo.upsertClient(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            validateClientData(body)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(repo.upsertClient(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteClient(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Contacts ─────────────────────────────────────────────────────────────────

private fun Route.contactRoutes(repo: CrudRepository) {
    route("/api/contacts") {
        get {
            val clientId = call.request.queryParameters["clientId"]
            call.respond(listResponse(repo.getContacts(clientId)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getContact(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            validateContactData(body)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(HttpStatusCode.Created, repo.upsertContact(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            validateContactData(body)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(repo.upsertContact(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteContact(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Contracts ────────────────────────────────────────────────────────────────

private fun Route.contractRoutes(repo: CrudRepository) {
    route("/api/contracts") {
        get {
            val clientId = call.request.queryParameters["clientId"]
            call.respond(listResponse(repo.getContracts(clientId)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getContract(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertContract(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertContract(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteContract(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Facilities ───────────────────────────────────────────────────────────────

private fun Route.facilityRoutes(repo: CrudRepository) {
    route("/api/facilities") {
        get {
            val clientId = call.request.queryParameters["clientId"]
            call.respond(listResponse(repo.getFacilities(clientId)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getFacility(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            validateFacilityData(body)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(HttpStatusCode.Created, repo.upsertFacility(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            validateFacilityData(body)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(repo.upsertFacility(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteFacility(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Islands ──────────────────────────────────────────────────────────────────

private fun Route.islandRoutes(repo: CrudRepository) {
    route("/api/islands") {
        get {
            val facilityId = call.request.queryParameters["facilityId"]
            call.respond(listResponse(repo.getIslands(facilityId)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getIsland(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            validateIslandData(body)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(HttpStatusCode.Created, repo.upsertIsland(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            validateIslandData(body)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(repo.upsertIsland(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteIsland(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Mechanical units ─────────────────────────────────────────────────────────

private fun Route.mechanicalUnitRoutes(repo: CrudRepository) {
    route("/api/mechanical-units") {
        get {
            val islandId = call.request.queryParameters["islandId"]
            call.respond(listResponse(repo.getMechanicalUnits(islandId)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getMechanicalUnit(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertMechanicalUnit(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertMechanicalUnit(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteMechanicalUnit(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Checkups (read-only) ─────────────────────────────────────────────────────

private fun Route.checkupRoutes(repo: CrudRepository) {
    route("/api/checkups") {
        get {
            val clientId = call.request.queryParameters["clientId"]
            val islandId = call.request.queryParameters["islandId"]
            val items = when {
                clientId != null -> repo.getCheckupsForClient(clientId)
                islandId != null -> repo.getCheckupsForIsland(islandId)
                else -> emptyList()
            }
            call.respond(listResponse(items))
        }
    }
}

// ─── Module Types ─────────────────────────────────────────────────────────────

private fun Route.moduleTypeRoutes(repo: CrudRepository) {
    route("/api/module-types") {
        get {
            val includeInactive = call.request.queryParameters["all"]?.toBoolean() ?: false
            call.respond(listResponse(repo.getModuleTypes(includeInactive)))
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getModuleType(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }
        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertModuleType(body).toJsonObject())
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertModuleType(body + ("id" to id)).toJsonObject())
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteModuleType(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Criticality Levels ───────────────────────────────────────────────────────

private fun Route.criticalityLevelRoutes(repo: CrudRepository) {
    route("/api/criticality-levels") {
        get {
            val includeInactive = call.request.queryParameters["all"]?.toBoolean() ?: false
            call.respond(listResponse(repo.getCriticalityLevels(includeInactive)))
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getCriticalityLevel(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }
        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertCriticalityLevel(body).toJsonObject())
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertCriticalityLevel(body + ("id" to id)).toJsonObject())
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteCriticalityLevel(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Checkup Statuses ─────────────────────────────────────────────────────────

private fun Route.checkupStatusRoutes(repo: CrudRepository) {
    route("/api/checkup-statuses") {
        get {
            val includeInactive = call.request.queryParameters["all"]?.toBoolean() ?: false
            call.respond(listResponse(repo.getCheckupStatuses(includeInactive)))
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getCheckupStatus(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }
        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertCheckupStatus(body).toJsonObject())
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertCheckupStatus(body + ("id" to id)).toJsonObject())
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteCheckupStatus(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Check Item Templates ─────────────────────────────────────────────────────

private fun Route.checkItemTemplateRoutes(repo: CrudRepository) {
    route("/api/check-item-templates") {
        get {
            val moduleTypeId = call.request.queryParameters["moduleTypeId"]
            call.respond(listResponse(repo.getCheckItemTemplates(moduleTypeId)))
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getCheckItemTemplate(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }
        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertCheckItemTemplate(body).toJsonObject())
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertCheckItemTemplate(body + ("id" to id)).toJsonObject())
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.deleteCheckItemTemplate(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ─── Maintenance logs ─────────────────────────────────────────────────────────

private fun Route.maintenanceLogRoutes(repo: CrudRepository) {
    route("/api/maintenance-logs") {
        get {
            val islandId = call.request.queryParameters["islandId"]
            call.respond(listResponse(repo.getMaintenanceLogs(islandId)))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = repo.getMaintenanceLog(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(item.toJsonObject())
        }

        post {
            val body = call.receive<JsonObject>().toMap()
            call.respond(HttpStatusCode.Created, repo.upsertMaintenanceLog(body).toJsonObject())
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<JsonObject>().toMap()
            call.respond(repo.upsertMaintenanceLog(body + ("id" to id)).toJsonObject())
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repo.softDeleteMaintenanceLog(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}