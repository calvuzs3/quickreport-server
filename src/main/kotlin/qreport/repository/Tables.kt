package net.calvuz.qreport.repository

import org.jetbrains.exposed.sql.Table

// Shared Exposed table definitions used by both SyncServerRepository and
// ExposedCrudRepository. Declared internal so they are visible across the
// repository package without leaking into other packages.

internal object Clients : Table("clients") {
    val id = text("id")
    val companyName = text("company_name")
    val notes = text("notes").nullable()
    val headquartersJson = text("headquarters_json").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val syncedAt = long("synced_at").nullable()
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object Contacts : Table("contacts") {
    val id = text("id")
    val clientId = text("client_id")
    val firstName = text("first_name")
    val lastName = text("last_name").nullable()
    val title = text("title").nullable()
    val role = text("role").nullable()
    val department = text("department").nullable()
    val phone = text("phone").nullable()
    val mobilePhone = text("mobile_phone").nullable()
    val email = text("email").nullable()
    val alternativeEmail = text("alternative_email").nullable()
    val isPrimary = bool("is_primary").default(false)
    val preferredContactMethod = text("preferred_contact_method").nullable()
    val notes = text("notes").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val syncedAt = long("synced_at").nullable()
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object Contracts : Table("contracts") {
    val id = text("id")
    val clientId = text("client_id")
    val name = text("name").nullable()
    val description = text("description").nullable()
    val startDate = long("start_date")
    val endDate = long("end_date")
    val hasPriority = bool("has_priority").default(true)
    val hasRemoteAssistance = bool("has_remote_assistance").default(true)
    val hasMaintenance = bool("has_maintenance").default(true)
    val notes = text("notes").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val syncedAt = long("synced_at").nullable()
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object Facilities : Table("facilities") {
    val id = text("id")
    val clientId = text("client_id")
    val name = text("name")
    val code = text("code").nullable()
    val notes = text("notes").nullable()
    val facilityType = text("facility_type")
    val addressJson = text("address_json").nullable()
    val isPrimary = bool("is_primary").default(false)
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val syncedAt = long("synced_at").nullable()
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object IslandTypes : Table("island_types") {
    val id                      = text("id")
    val code                    = text("code").uniqueIndex()
    val label                   = text("label")
    val description             = text("description").nullable()
    val iconName                = text("icon_name").nullable()
    val maintenanceIntervalDays = integer("maintenance_interval_days").default(180)
    val sortOrder               = integer("sort_order").default(0)
    val isActive                = bool("is_active").default(true)
    val createdAt               = long("created_at")
    val updatedAt               = long("updated_at")
    val syncedAt                = long("synced_at").nullable()
    val isDeleted               = bool("is_deleted").default(false)
    override val primaryKey     = PrimaryKey(id)
}

internal object FacilityIslands : Table("facility_islands") {
    val id = text("id")
    val facilityId = text("facility_id")
    val commissioningNumber = text("commissioning_number").nullable()
    val islandType = text("island_type")
    val islandTypeId = text("island_type_id").references(IslandTypes.id).nullable()
    val serialNumber = text("serial_number")
    val modelNumber = text("model_number").nullable()
    val model = text("model").nullable()
    val installationDate = long("installation_date").nullable()
    val warrantyExpiration = long("warranty_expiration").nullable()
    val operatingHours = long("operating_hours").default(0)
    val cycleCount = long("cycle_count").default(0)
    val lastMaintenanceDate = long("last_maintenance_date").nullable()
    val nextScheduledMaintenance = long("next_scheduled_maintenance").nullable()
    val customName = text("custom_name").nullable()
    val location = text("location").nullable()
    val notes = text("notes").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val syncedAt = long("synced_at").nullable()
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object MechanicalUnits : Table("mechanical_units") {
    val id = text("id")
    val islandId = text("island_id")
    val unitType = text("unit_type")
    val name = text("name")
    val serialNumber = text("serial_number").nullable()
    val model = text("model").nullable()
    val notes = text("notes").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val syncedAt = long("synced_at").nullable()
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object MaintenanceLogs : Table("maintenance_logs") {
    val id = text("id")
    val islandId = text("island_id")
    val operationType = text("operation_type")
    val customOperationLabel = text("custom_operation_label").nullable()
    val mechanicalUnitId = text("mechanical_unit_id").nullable()
    val componentLabel = text("component_label").nullable()
    val description = text("description")
    val technicianName = text("technician_name")
    val technicianCompany = text("technician_company").nullable()
    val operatingHoursAtEvent = integer("operating_hours_at_event").nullable()
    val cycleCountAtEvent = long("cycle_count_at_event").nullable()
    val outcome = text("outcome")
    val durationMinutes = integer("duration_minutes").nullable()
    val notes = text("notes").nullable()
    val performedAt = long("performed_at")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val syncedAt = long("synced_at").nullable()
    val isActive = bool("is_active").default(true)
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for island_documents.
 *
 * Only the columns needed by document sync routes are declared here.
 * The full table (all metadata columns) is already created by the
 * Android migration 4→5 SQL and the ALTER statements for 5→6.
 * Exposed does not manage schema migrations — it only maps columns.
 */
object IslandDocuments : Table("island_documents") {
    val id              = text("id")
    val scope           = text("scope")
    val islandId        = text("island_id").nullable()
    val facilityId      = text("facility_id").nullable()
    val clientId        = text("client_id").nullable()
    val fileName        = text("file_name")
    val fileSize        = long("file_size")
    val mimeType        = text("mime_type")
    val fileHash        = text("file_hash").nullable()
    val storageBackend  = text("storage_backend").default("local")
    val title           = text("title")
    val category        = text("category")
    val notes           = text("notes").nullable()
    val createdAt       = long("created_at")
    val updatedAt       = long("updated_at")
    val isActive        = bool("is_active").default(true)
    val isDeleted       = bool("is_deleted").default(false)
    val syncedAt        = long("synced_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object ModuleTypes : Table("module_types") {
    val id          = text("id")
    val code        = text("code").uniqueIndex()
    val label       = text("label")
    val description = text("description").nullable()
    val iconName    = text("icon_name").nullable()
    val sortOrder   = integer("sort_order").default(0)
    val isActive    = bool("is_active").default(true)
    val createdAt   = long("created_at")
    val updatedAt   = long("updated_at")
    val syncedAt    = long("synced_at").nullable()
    val isDeleted   = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object CriticalityLevels : Table("criticality_levels") {
    val id         = text("id")
    val code       = text("code").uniqueIndex()
    val label      = text("label")
    val priority   = integer("priority").default(0)
    val colorHex   = text("color_hex").default("#808080")
    val iconEmoji  = text("icon_emoji").nullable()
    val sortOrder  = integer("sort_order").default(0)
    val isActive   = bool("is_active").default(true)
    val createdAt  = long("created_at")
    val updatedAt  = long("updated_at")
    val syncedAt   = long("synced_at").nullable()
    val isDeleted  = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object CheckupStatuses : Table("checkup_statuses") {
    val id               = text("id")
    val code             = text("code").uniqueIndex()
    val label            = text("label")
    val colorHex         = text("color_hex").default("#808080")
    val iconEmoji        = text("icon_emoji").nullable()
    val sortOrder        = integer("sort_order").default(0)
    val isActive         = bool("is_active").default(true)
    val blocksDeletion   = bool("blocks_deletion").default(false)
    val marksCompletion  = bool("marks_completion").default(false)
    val createdAt        = long("created_at")
    val updatedAt        = long("updated_at")
    val syncedAt         = long("synced_at").nullable()
    val isDeleted        = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object CheckupStatusTransitions : Table("checkup_status_transitions") {
    val fromStatusId = text("from_status_id")
    val toStatusId   = text("to_status_id")
    override val primaryKey = PrimaryKey(fromStatusId, toStatusId)
}

internal object CheckItemTemplates : Table("check_item_templates") {
    val id             = text("id")
    val moduleTypeId   = text("module_type_id")
    val category       = text("category").default("")
    val description    = text("description")
    val criticalityId  = text("criticality_id")
    val orderIndex     = integer("order_index").default(0)
    val isActive       = bool("is_active").default(true)
    val createdAt      = long("created_at")
    val updatedAt      = long("updated_at")
    val syncedAt       = long("synced_at").nullable()
    val isDeleted      = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object ModuleTypeIslandTypes : Table("module_type_island_types") {
    val islandTypeId = text("island_type_id")
    val moduleTypeId = text("module_type_id")
    override val primaryKey = PrimaryKey(islandTypeId, moduleTypeId)
}

internal object CheckItems : Table("check_items") {
    val id            = text("id")
    val checkupId     = text("checkup_id")
    val moduleType    = text("module_type")
    val moduleTypeId  = text("module_type_id").nullable()
    val itemCode      = text("item_code")
    val description   = text("description")
    val status        = text("status")
    val criticality   = text("criticality")
    val criticalityId = text("criticality_id").nullable()
    val notes         = text("notes").default("")
    val checkedAt     = long("checked_at").nullable()
    val orderIndex    = integer("order_index").default(0)
    override val primaryKey = PrimaryKey(id)
}

internal object Photos : Table("photos") {
    val id          = text("id")
    val checkItemId = text("check_item_id")
    val fileName    = text("file_name")
    val caption     = text("caption").default("")
    val takenAt     = long("taken_at")
    val fileSize    = long("file_size")
    val orderIndex  = integer("order_index").default(0)
    val width       = integer("width").default(0)
    val height      = integer("height").default(0)
    override val primaryKey = PrimaryKey(id)
}

internal object Checkups : Table("checkups") {
    val id                          = text("id")
    val clientCompanyName           = text("client_company_name").default("")
    val clientContactPerson         = text("client_contact_person").default("")
    val clientSite                  = text("client_site").default("")
    val clientAddress               = text("client_address").default("")
    val clientPhone                 = text("client_phone").default("")
    val clientEmail                 = text("client_email").default("")
    val islandSerialNumber          = text("island_serial_number").default("")
    val islandModel                 = text("island_model").default("")
    val islandInstallationDate      = text("island_installation_date").default("")
    val islandLastMaintenanceDate   = text("island_last_maintenance_date").default("")
    val islandOperatingHours        = integer("island_operating_hours").default(0)
    val islandCycleCount            = long("island_cycle_count").default(0)
    val technicianName              = text("technician_name").default("")
    val technicianCompany           = text("technician_company").default("")
    val technicianCertification     = text("technician_certification").default("")
    val technicianPhone             = text("technician_phone").default("")
    val technicianEmail             = text("technician_email").default("")
    val checkupDate                 = long("checkup_date")
    val headerNotes                 = text("header_notes").default("")
    val islandType                  = text("island_type").default("")
    val islandTypeId                = text("island_type_id").nullable()
    val status                      = text("status").default("DRAFT")
    val createdAt                   = long("created_at")
    val updatedAt                   = long("updated_at")
    val completedAt                 = long("completed_at").nullable()
    val syncedAt                    = long("synced_at").nullable()
    val isDeleted                   = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

internal object CheckupIslandAssociations : Table("checkup_island_associations") {
    val id              = text("id")
    val checkupId       = text("checkup_id")
    val islandId        = text("island_id")
    val associationType = text("association_type")
    val notes           = text("notes").nullable()
    val createdAt       = long("created_at")
    val updatedAt       = long("updated_at")
    val syncedAt        = long("synced_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object SyncLog : Table("sync_log") {
    val id = integer("id").autoIncrement()
    val deviceId = text("device_id")
    val syncedAt = long("synced_at")
    val recordsPushed = integer("records_pushed").default(0)
    val recordsPulled = integer("records_pulled").default(0)
    override val primaryKey = PrimaryKey(id)
}

internal object AuthUsers : Table("auth_users") {
    val id           = integer("id").autoIncrement()
    val username     = text("username")
    val passwordHash = text("password_hash")
    val isActive     = bool("is_active").default(true)
    val role         = text("role").default("TECHNICIAN")
    val createdAt    = long("created_at")
    override val primaryKey = PrimaryKey(id)
}