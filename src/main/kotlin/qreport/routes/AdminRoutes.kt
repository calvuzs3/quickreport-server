package net.calvuz.qreport.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import net.calvuz.qreport.model.CreateUserRequest
import net.calvuz.qreport.model.UpdateUserRequest
import net.calvuz.qreport.model.UserResponse
import net.calvuz.qreport.repository.AuthUsers
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt

@Serializable
private data class UsersListResponse(val data: List<UserResponse>, val total: Int)

private fun ApplicationCall.isAdmin(): Boolean =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString() == "ADMIN"

private fun String.toValidRole(): String = if (uppercase() == "ADMIN") "ADMIN" else "TECHNICIAN"

fun Application.configureAdminRoutes() {
    routing {
        authenticate("jwt-auth") {
            route("/admin/users") {

                get {
                    if (!call.isAdmin()) { call.respond(HttpStatusCode.Forbidden); return@get }
                    val users = transaction {
                        AuthUsers.selectAll()
                            .orderBy(AuthUsers.id, SortOrder.ASC)
                            .map { row ->
                                UserResponse(
                                    id        = row[AuthUsers.id],
                                    username  = row[AuthUsers.username],
                                    role      = row[AuthUsers.role],
                                    isActive  = row[AuthUsers.isActive],
                                    createdAt = row[AuthUsers.createdAt]
                                )
                            }
                    }
                    call.respond(UsersListResponse(data = users, total = users.size))
                }

                get("/{id}") {
                    if (!call.isAdmin()) { call.respond(HttpStatusCode.Forbidden); return@get }
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val user = transaction {
                        AuthUsers.selectAll()
                            .where { AuthUsers.id eq id }
                            .firstOrNull()
                            ?.let { row ->
                                UserResponse(
                                    id        = row[AuthUsers.id],
                                    username  = row[AuthUsers.username],
                                    role      = row[AuthUsers.role],
                                    isActive  = row[AuthUsers.isActive],
                                    createdAt = row[AuthUsers.createdAt]
                                )
                            }
                    } ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(user)
                }

                post {
                    if (!call.isAdmin()) { call.respond(HttpStatusCode.Forbidden); return@post }
                    val req = call.receive<CreateUserRequest>()
                    if (req.username.isBlank() || req.password.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "username and password are required")
                        return@post
                    }
                    val normalizedRole = req.role.toValidRole()
                    val hash = BCrypt.hashpw(req.password, BCrypt.gensalt())
                    val now = System.currentTimeMillis()
                    val newId = transaction {
                        val exists = AuthUsers.selectAll()
                            .where { AuthUsers.username eq req.username }.count() > 0
                        if (exists) return@transaction null
                        val stmt = AuthUsers.insert {
                            it[username]     = req.username
                            it[passwordHash] = hash
                            it[isActive]     = true
                            it[role]         = normalizedRole
                            it[createdAt]    = now
                        }
                        stmt[AuthUsers.id]
                    } ?: return@post call.respond(HttpStatusCode.Conflict, "username already exists")
                    call.respond(
                        HttpStatusCode.Created,
                        UserResponse(id = newId, username = req.username, role = normalizedRole, isActive = true, createdAt = now)
                    )
                }

                put("/{id}") {
                    if (!call.isAdmin()) { call.respond(HttpStatusCode.Forbidden); return@put }
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val req = call.receive<UpdateUserRequest>()
                    val found = transaction {
                        val exists = AuthUsers.selectAll().where { AuthUsers.id eq id }.count() > 0
                        if (!exists) return@transaction false
                        AuthUsers.update({ AuthUsers.id eq id }) { stmt ->
                            req.role?.let { stmt[AuthUsers.role] = it.toValidRole() }
                            req.isActive?.let { stmt[AuthUsers.isActive] = it }
                            req.password?.takeIf { it.isNotBlank() }?.let {
                                stmt[AuthUsers.passwordHash] = BCrypt.hashpw(it, BCrypt.gensalt())
                            }
                        }
                        true
                    }
                    if (!found) return@put call.respond(HttpStatusCode.NotFound)
                    val user = transaction {
                        AuthUsers.selectAll().where { AuthUsers.id eq id }.first().let { row ->
                            UserResponse(row[AuthUsers.id], row[AuthUsers.username], row[AuthUsers.role], row[AuthUsers.isActive], row[AuthUsers.createdAt])
                        }
                    }
                    call.respond(user)
                }

                // Soft-deactivate: sets is_active = false (does not delete the row)
                delete("/{id}") {
                    if (!call.isAdmin()) { call.respond(HttpStatusCode.Forbidden); return@delete }
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val updated = transaction {
                        AuthUsers.update({ AuthUsers.id eq id }) { it[AuthUsers.isActive] = false } > 0
                    }
                    if (!updated) return@delete call.respond(HttpStatusCode.NotFound)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
