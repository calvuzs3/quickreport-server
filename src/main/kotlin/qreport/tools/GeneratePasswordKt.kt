package net.calvuz.qreport.tools

import org.mindrot.jbcrypt.BCrypt
import java.io.File

/**
 * Standalone utility to generate BCrypt password hashes.
 *
 * Run with: ./gradlew run -PmainClass=net.calvuz.qreport.tools.GeneratePasswordKt
 * Or directly from IntelliJ by right-clicking and selecting "Run".
 *
 * Usage: edit the PASSWORDS list below, run, copy the output into the SQL insert.
 */
fun main() {
    // Read from passwords.txt in project root
    // Format: username:password (one per line, # for comments)
    val passwordFile = File("passwords.txt")

    println("===== BCrypt Password Hashes =====")
    println()

    if (!passwordFile.exists()) {
        println("ERROR: passwords.txt not found in ${File(".").absolutePath}")
        println("Create it with format: username:password")
        return
    }

    val entries = passwordFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim()
            else null
        }

    entries.forEach { (username, password) ->
        val hash = BCrypt.hashpw(password, BCrypt.gensalt(10))
        println("Username : $username")
        println("Hash     : $hash")
        println()
        println("SQL:")
        println("INSERT INTO auth_users (username, password_hash, is_active, created_at)")
        println("VALUES ('$username', '$hash', TRUE, ${System.currentTimeMillis()});")
        println()
        println("--------------------------------------------------\n")
    }

    // Verify the hashes are correct
    println("===== Verification =====")
    entries.forEach { (username, password) ->
        val hash = BCrypt.hashpw(password, BCrypt.gensalt(10))
        val valid = BCrypt.checkpw(password, hash)
        println("valid: $valid -> $username: $password ")
    }
}

