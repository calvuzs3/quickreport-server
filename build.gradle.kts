
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "net.calvuz"
version = "1.0.1"

application {
    mainClass = "net.calvuz.qreport.ApplicationKt"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.resources)
    implementation(ktorLibs.server.statusPages)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)

    // PostgreSQL driver
    implementation("org.postgresql:postgresql:42.7.3")

    // Exposed ORM (query type-safe in Kotlin)
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")

    // Connection pool
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // To sign JWT token
    implementation("com.auth0:java-jwt:4.4.0")

    // KMP shared sync DTOs (composite build — see settings.gradle.kts)
    implementation("net.calvuz:shared:0.1.0")
}
