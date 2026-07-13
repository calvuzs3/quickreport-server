package net.calvuz.qreport.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.calvuz.qreport.shared.dto.LoginRequest
import net.calvuz.qreport.shared.dto.LoginResponse
import net.calvuz.qreport.plugins.jwtAudience
import net.calvuz.qreport.plugins.jwtIssuer
import net.calvuz.qreport.plugins.jwtSecret
import net.calvuz.qreport.repository.AuthUsers
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

fun Route.authRoutes() {
    post("/auth/login") {
        val request = call.receive<LoginRequest>()

        val user = transaction {
            AuthUsers.selectAll()
                .where { AuthUsers.username eq request.username }
                .firstOrNull()
        }

        if (user == null || !user[AuthUsers.isActive]) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            return@post
        }

        if (!BCrypt.checkpw(request.password, user[AuthUsers.passwordHash])) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            return@post
        }

        val role = user[AuthUsers.role]

        val token = JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("username", request.username)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // 30 days
            .sign(Algorithm.HMAC256(jwtSecret))

        call.respond(LoginResponse(token = token, role = role))
    }
}