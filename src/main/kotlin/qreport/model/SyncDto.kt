package net.calvuz.qreport.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ===== REQUEST / RESPONSE WRAPPERS =====

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val role: String = "TECHNICIAN"
)

@Serializable
data class SyncPayload(
    val deviceId: String,
    val syncTimestamp: Long,
    val islandTypes: List<IslandTypeDto> = emptyList(),
    val clients: List<ClientDto> = emptyList(),
    val contacts: List<ContactDto> = emptyList(),
    val contracts: List<ContractDto> = emptyList(),
    val facilities: List<FacilityDto> = emptyList(),
    val facilityIslands: List<FacilityIslandDto> = emptyList(),
    val mechanicalUnits: List<MechanicalUnitDto> = emptyList(),
    val maintenanceLogs: List<MaintenanceLogDto> = emptyList(),
    val documents: List<DocumentDto> = emptyList(),
    // checkup master data
    val moduleTypes: List<ModuleTypeDto> = emptyList(),
    val criticalityLevels: List<CriticalityLevelDto> = emptyList(),
    val checkupStatuses: List<CheckUpStatusDto> = emptyList(),
    val checkItemTemplates: List<CheckItemTemplateDto> = emptyList(),
    val moduleTypeIslandTypeLinks: List<ModuleTypeIslandTypeLinkDto> = emptyList(),
    val checkUpStatusTransitions: List<CheckUpStatusTransitionDto> = emptyList(),
    // checkup records (push from device, read-only on server/web)
    val checkups: List<CheckUpRecordDto> = emptyList(),
    val checkupIslandAssociations: List<CheckUpIslandAssociationDto> = emptyList(),
    val checkupItems: List<CheckItemDto> = emptyList(),
    val photos: List<PhotoDto> = emptyList()
)

@Serializable
data class SyncResponse(
    val acceptedIds: List<String>,
    val pulledPayload: SyncPayload
)

@Serializable
data class CheckItemDto(
    val id: String,
    @SerialName("checkup_id")     val checkupId: String,
    @SerialName("module_type")    val moduleType: String,
    @SerialName("module_type_id") val moduleTypeId: String? = null,
    @SerialName("item_code")      val itemCode: String,
    val description: String,
    val status: String,
    val criticality: String,
    @SerialName("criticality_id") val criticalityId: String? = null,
    val notes: String = "",
    @SerialName("checked_at")     val checkedAt: Long? = null,
    @SerialName("order_index")    val orderIndex: Int = 0
)

/**
 * Photo metadata, synced wholesale alongside the check items of its owning
 * checkup (see [SyncServerRepository] push/pull — same "delete all + reinsert
 * for the touched check items" pattern as [CheckItemDto]). File bytes travel
 * through the separate /photos/manifest+upload+download channel; the hash
 * used there is computed on the fly, not persisted here.
 */
@Serializable
data class PhotoDto(
    val id: String,
    @SerialName("check_item_id") val checkItemId: String,
    @SerialName("file_name")     val fileName: String,
    val caption: String = "",
    @SerialName("taken_at")      val takenAt: Long,
    @SerialName("file_size")     val fileSize: Long,
    @SerialName("order_index")   val orderIndex: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class ModuleTypeIslandTypeLinkDto(
    val islandTypeId: String,
    val moduleTypeId: String
)

@Serializable
data class CheckUpStatusTransitionDto(
    val fromStatusId: String,
    val toStatusId: String
)

// ===== ENTITY DTOs =====
// Fields match the PostgreSQL schema columns exactly.

@Serializable
data class IslandTypeDto(
    val id: String,
    val code: String,
    val label: String,
    val description: String? = null,
    val iconName: String? = null,
    val maintenanceIntervalDays: Int = 180,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class ClientDto(
    val id: String,
    val companyName: String,
    val notes: String? = null,
    val headquartersJson: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class ContactDto(
    val id: String,
    val clientId: String,
    val firstName: String,
    val lastName: String? = null,
    val title: String? = null,
    val role: String? = null,
    val department: String? = null,
    val phone: String? = null,
    val mobilePhone: String? = null,
    val email: String? = null,
    val alternativeEmail: String? = null,
    val isPrimary: Boolean = false,
    val preferredContactMethod: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class ContractDto(
    val id: String,
    val clientId: String,
    val name: String? = null,
    val description: String? = null,
    val startDate: Long,
    val endDate: Long,
    val hasPriority: Boolean = true,
    val hasRemoteAssistance: Boolean = true,
    val hasMaintenance: Boolean = true,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class FacilityDto(
    val id: String,
    val clientId: String,
    val name: String,
    val code: String? = null,
    val notes: String? = null,
    val facilityType: String,
    val addressJson: String? = null,
    val isPrimary: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class FacilityIslandDto(
    val id: String,
    val facilityId: String,
    val commissioningNumber: String? = null,
    val islandType: String,
    val islandTypeId: String? = null,
    val serialNumber: String,
    val modelNumber: String? = null,
    val model: String? = null,
    val installationDate: Long? = null,
    val warrantyExpiration: Long? = null,
    val operatingHours: Long = 0,
    val cycleCount: Long = 0,
    val lastMaintenanceDate: Long? = null,
    val nextScheduledMaintenance: Long? = null,
    val customName: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class MaintenanceLogDto(
    val id: String,
    val islandId: String,
    val operationType: String,
    val customOperationLabel: String? = null,
    val mechanicalUnitId: String? = null,
    val componentLabel: String? = null,
    val description: String,
    val technicianName: String,
    val technicianCompany: String? = null,
    val operatingHoursAtEvent: Int? = null,
    val cycleCountAtEvent: Long? = null,
    val outcome: String,
    val durationMinutes: Int? = null,
    val notes: String? = null,
    val performedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false
)

@Serializable
data class MechanicalUnitDto(
    val id: String,
    val islandId: String,
    val unitType: String,
    val name: String,
    val serialNumber: String? = null,
    val model: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

// ===== CHECKUP MASTER DTOs =====

@Serializable
data class ModuleTypeDto(
    val id: String,
    val code: String,
    val label: String,
    val description: String? = null,
    val iconName: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class CriticalityLevelDto(
    val id: String,
    val code: String,
    val label: String,
    val priority: Int,
    val colorHex: String,
    val iconEmoji: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class CheckUpStatusDto(
    val id: String,
    val code: String,
    val label: String,
    val colorHex: String,
    val iconEmoji: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val blocksDeletion: Boolean = false,
    val marksCompletion: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class CheckItemTemplateDto(
    val id: String,
    val moduleTypeId: String,
    val category: String,
    val description: String,
    val criticalityId: String,
    val orderIndex: Int,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)

 @Serializable
 data class DocumentDto(
     val id: String,
     val scope: String,
     @SerialName("island_id")   val islandId: String?   = null,
     @SerialName("facility_id") val facilityId: String? = null,
     @SerialName("client_id")   val clientId: String?   = null,
     @SerialName("file_name")   val fileName: String,
     @SerialName("file_size")   val fileSize: Long,
     @SerialName("mime_type")   val mimeType: String,
     @SerialName("file_hash")   val fileHash: String?   = null,
     val title: String,
     val category: String,
     val notes: String?         = null,
     @SerialName("created_at")  val createdAt: Long,
     @SerialName("updated_at")  val updatedAt: Long,
     @SerialName("is_active")   val isActive: Boolean,
     @SerialName("is_deleted")  val isDeleted: Boolean,
     @SerialName("synced_at")   val syncedAt: Long?     = null
     // filePath intentionally absent — local to each device
 )

@Serializable
data class CheckUpRecordDto(
    val id: String,
    @SerialName("client_company_name")          val clientCompanyName: String,
    @SerialName("client_contact_person")        val clientContactPerson: String = "",
    @SerialName("client_site")                  val clientSite: String = "",
    @SerialName("client_address")               val clientAddress: String = "",
    @SerialName("client_phone")                 val clientPhone: String = "",
    @SerialName("client_email")                 val clientEmail: String = "",
    @SerialName("island_serial_number")         val islandSerialNumber: String = "",
    @SerialName("island_model")                 val islandModel: String = "",
    @SerialName("island_installation_date")     val islandInstallationDate: String = "",
    @SerialName("island_last_maintenance_date") val islandLastMaintenanceDate: String = "",
    @SerialName("island_operating_hours")       val islandOperatingHours: Int = 0,
    @SerialName("island_cycle_count")           val islandCycleCount: Long = 0,
    @SerialName("technician_name")              val technicianName: String = "",
    @SerialName("technician_company")           val technicianCompany: String = "",
    @SerialName("technician_certification")     val technicianCertification: String = "",
    @SerialName("technician_phone")             val technicianPhone: String = "",
    @SerialName("technician_email")             val technicianEmail: String = "",
    @SerialName("checkup_date")                 val checkupDate: Long,
    @SerialName("header_notes")                 val headerNotes: String = "",
    @SerialName("island_type")                  val islandType: String = "",
    @SerialName("island_type_id")               val islandTypeId: String? = null,
    val status: String = "DRAFT",
    @SerialName("created_at")                   val createdAt: Long,
    @SerialName("updated_at")                   val updatedAt: Long,
    @SerialName("completed_at")                 val completedAt: Long? = null,
    @SerialName("synced_at")                    val syncedAt: Long? = null,
    @SerialName("is_deleted")                   val isDeleted: Boolean = false
)

// ===== ADMIN USER MANAGEMENT DTOs =====

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

@Serializable
data class CheckUpIslandAssociationDto(
    val id: String,
    @SerialName("checkup_id")        val checkupId: String,
    @SerialName("island_id")         val islandId: String,
    @SerialName("association_type")  val associationType: String,
    val notes: String? = null,
    @SerialName("created_at")        val createdAt: Long,
    @SerialName("updated_at")        val updatedAt: Long,
    @SerialName("synced_at")         val syncedAt: Long? = null
)