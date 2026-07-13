package net.calvuz.qreport.model

import kotlinx.serialization.Serializable

// ===== ADMIN USER MANAGEMENT DTOs =====
// Server-only — no client equivalent, not moved to :shared.

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    val role: String,
    val isActive: Boolean,
    val createdAt: Long
)

@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String = "TECHNICIAN"
)

@Serializable
data class UpdateUserRequest(
    val role: String? = null,
    val isActive: Boolean? = null,
    val password: String? = null
)
