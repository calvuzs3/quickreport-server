package net.calvuz.qreport.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

/**
 * Initializes the database connection pool using HikariCP and connects Exposed to it.
 *
 * Configuration is read from environment variables so no credentials are hardcoded:
 *   DB_URL      e.g. jdbc:postgresql://localhost:5432/qreport_db
 *   DB_USER     e.g. qreport_user
 *   DB_PASSWORD e.g. your_password
 */
object DatabaseFactory {

    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = System.getenv("DB_URL")
                ?: "jdbc:postgresql://localhost:5432/qreport_db"
            username = System.getenv("DB_USER")
                ?: "qreport_user"
            password = System.getenv("DB_PASSWORD")
                ?: error("DB_PASSWORD environment variable not set")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
    }
}

