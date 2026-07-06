package net.calvuz.qreport.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

// Read from environment — never hardcode in source
val jwtSecret: String get() = System.getenv("JWT_SECRET") ?: error("JWT_SECRET not set")
val jwtIssuer: String get() = System.getenv("JWT_ISSUER") ?: "qreport-server"
val jwtAudience: String get() = System.getenv("JWT_AUDIENCE") ?: "qreport-android"

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("jwt-auth") {
            realm = "QReport Server"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}