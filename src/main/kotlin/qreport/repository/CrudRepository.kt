package net.calvuz.qreport.repository

import net.calvuz.qreport.model.MaintenanceLogDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.util.UUID

// Table objects are defined in Tables.kt (same package) — no imports needed.

// ─── Repository interface ─────────────────────────────────────────────────────

interface CrudRepository {
    // Clients
    fun getClients(clientId: String? = null): List<Map<String, Any?>>
    fun getClient(id: String): Map<String, Any?>?
    fun upsertClient(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteClient(id: String)

    // Contacts
    fun getContacts(clientId: String? = null): List<Map<String, Any?>>
    fun getContact(id: String): Map<String, Any?>?
    fun upsertContact(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteContact(id: String)

    // Contracts
    fun getContracts(clientId: String? = null): List<Map<String, Any?>>
    fun getContract(id: String): Map<String, Any?>?
    fun upsertContract(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteContract(id: String)

    // Facilities
    fun getFacilities(clientId: String? = null): List<Map<String, Any?>>
    fun getFacility(id: String): Map<String, Any?>?
    fun upsertFacility(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteFacility(id: String)

    // Islands
    fun getIslands(facilityId: String? = null): List<Map<String, Any?>>
    fun getIsland(id: String): Map<String, Any?>?
    fun upsertIsland(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteIsland(id: String)

    // Mechanical units
    fun getMechanicalUnits(islandId: String? = null): List<Map<String, Any?>>
    fun getMechanicalUnit(id: String): Map<String, Any?>?
    fun upsertMechanicalUnit(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteMechanicalUnit(id: String)

    // Maintenance logs
    fun getMaintenanceLogs(islandId: String? = null): List<Map<String, Any?>>
    fun getMaintenanceLog(id: String): Map<String, Any?>?
    fun upsertMaintenanceLog(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteMaintenanceLog(id: String)

    // Island types
    fun getIslandTypes(includeInactive: Boolean = false): List<Map<String, Any?>>
    fun getIslandType(id: String): Map<String, Any?>?
    fun upsertIslandType(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteIslandType(id: String)

    // Module types
    fun getModuleTypes(includeInactive: Boolean = false): List<Map<String, Any?>>
    fun getModuleType(id: String): Map<String, Any?>?
    fun upsertModuleType(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteModuleType(id: String)

    // Criticality levels
    fun getCriticalityLevels(includeInactive: Boolean = false): List<Map<String, Any?>>
    fun getCriticalityLevel(id: String): Map<String, Any?>?
    fun upsertCriticalityLevel(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteCriticalityLevel(id: String)

    // Checkup statuses
    fun getCheckupStatuses(includeInactive: Boolean = false): List<Map<String, Any?>>
    fun getCheckupStatus(id: String): Map<String, Any?>?
    fun upsertCheckupStatus(data: Map<String, Any?>): Map<String, Any?>
    fun softDeleteCheckupStatus(id: String)

    // Check item templates
    fun getCheckItemTemplates(moduleTypeId: String? = null): List<Map<String, Any?>>
    fun getCheckItemTemplate(id: String): Map<String, Any?>?
    fun upsertCheckItemTemplate(data: Map<String, Any?>): Map<String, Any?>
    fun deleteCheckItemTemplate(id: String)

    // Checkups (read-only on server — pushed from Android)
    fun getCheckupsForClient(clientId: String): List<Map<String, Any?>>
    fun getCheckupsForIsland(islandId: String): List<Map<String, Any?>>
}

// ─── Exposed implementation ───────────────────────────────────────────────────

class ExposedCrudRepository : CrudRepository {

    // ── Clients ───────────────────────────────────────────────────────────────

    override fun getClients(clientId: String?) = transaction {
        Clients.selectAll().where { Clients.isDeleted eq false }.map { it.toClientMap() }
    }

    override fun getClient(id: String) = transaction {
        Clients.selectAll().where { (Clients.id eq id) and (Clients.isDeleted eq false) }
            .singleOrNull()?.toClientMap()
    }

    override fun upsertClient(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        Clients.upsert {
            it[Clients.id] = id
            it[companyName] = data["company_name"] as String
            it[notes] = data["notes"] as? String
            it[headquartersJson] = data["headquarters_json"] as? String
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now
            it[syncedAt] = null
            it[isDeleted] = false
        }
        getClient(id)!!
    }

    override fun softDeleteClient(id: String) = transaction {
        Clients.update({ Clients.id eq id }) {
            it[isDeleted] = true
            it[updatedAt] = System.currentTimeMillis()
        }
        Unit
    }

    // ── Contacts ──────────────────────────────────────────────────────────────

    override fun getContacts(clientId: String?) = transaction {
        val query = Contacts.selectAll().where { Contacts.isDeleted eq false }
        if (clientId != null) query.andWhere { Contacts.clientId eq clientId }
        query.map { it.toContactMap() }
    }

    override fun getContact(id: String) = transaction {
        Contacts.selectAll().where { (Contacts.id eq id) and (Contacts.isDeleted eq false) }
            .singleOrNull()?.toContactMap()
    }

    override fun upsertContact(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        Contacts.upsert {
            it[Contacts.id] = id
            it[clientId] = data["client_id"] as String
            it[firstName] = data["first_name"] as String
            it[lastName] = data["last_name"] as? String
            it[title] = data["title"] as? String
            it[role] = data["role"] as? String
            it[department] = data["department"] as? String
            it[phone] = data["phone"] as? String
            it[mobilePhone] = data["mobile_phone"] as? String
            it[email] = data["email"] as? String
            it[alternativeEmail] = data["alternative_email"] as? String
            it[isPrimary] = data["is_primary"] as? Boolean ?: false
            it[preferredContactMethod] = data["preferred_contact_method"] as? String
            it[notes] = data["notes"] as? String
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now
            it[syncedAt] = null
            it[isDeleted] = false
        }
        getContact(id)!!
    }

    override fun softDeleteContact(id: String) = transaction {
        Contacts.update({ Contacts.id eq id }) {
            it[isDeleted] = true; it[updatedAt] = System.currentTimeMillis()
        }; Unit
    }

    // ── Contracts ─────────────────────────────────────────────────────────────

    override fun getContracts(clientId: String?) = transaction {
        val query = Contracts.selectAll().where { Contracts.isDeleted eq false }
        if (clientId != null) query.andWhere { Contracts.clientId eq clientId }
        query.map { it.toContractMap() }
    }

    override fun getContract(id: String) = transaction {
        Contracts.selectAll().where { (Contracts.id eq id) and (Contracts.isDeleted eq false) }
            .singleOrNull()?.toContractMap()
    }

    override fun upsertContract(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        Contracts.upsert {
            it[Contracts.id] = id
            it[clientId] = data["client_id"] as String
            it[name] = data["name"] as? String
            it[description] = data["description"] as? String
            it[startDate] = (data["start_date"] as Number).toLong()
            it[endDate] = (data["end_date"] as Number).toLong()
            it[hasPriority] = data["has_priority"] as? Boolean ?: true
            it[hasRemoteAssistance] = data["has_remote_assistance"] as? Boolean ?: true
            it[hasMaintenance] = data["has_maintenance"] as? Boolean ?: true
            it[notes] = data["notes"] as? String
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now; it[syncedAt] = null; it[isDeleted] = false
        }
        getContract(id)!!
    }

    override fun softDeleteContract(id: String) = transaction {
        Contracts.update({ Contracts.id eq id }) {
            it[isDeleted] = true; it[updatedAt] = System.currentTimeMillis()
        }; Unit
    }

    // ── Facilities ────────────────────────────────────────────────────────────

    override fun getFacilities(clientId: String?) = transaction {
        val query = Facilities.selectAll().where { Facilities.isDeleted eq false }
        if (clientId != null) query.andWhere { Facilities.clientId eq clientId }
        query.map { it.toFacilityMap() }
    }

    override fun getFacility(id: String) = transaction {
        Facilities.selectAll().where { (Facilities.id eq id) and (Facilities.isDeleted eq false) }
            .singleOrNull()?.toFacilityMap()
    }

    override fun upsertFacility(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        Facilities.upsert {
            it[Facilities.id] = id
            it[clientId] = data["client_id"] as String
            it[name] = data["name"] as String
            it[code] = data["code"] as? String
            it[notes] = data["notes"] as? String
            it[facilityType] = data["facility_type"] as String
            it[addressJson] = data["address_json"] as? String
            it[isPrimary] = data["is_primary"] as? Boolean ?: false
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now; it[syncedAt] = null; it[isDeleted] = false
        }
        getFacility(id)!!
    }

    override fun softDeleteFacility(id: String) = transaction {
        Facilities.update({ Facilities.id eq id }) {
            it[isDeleted] = true; it[updatedAt] = System.currentTimeMillis()
        }; Unit
    }

    // ── Islands ───────────────────────────────────────────────────────────────

    override fun getIslands(facilityId: String?) = transaction {
        val query = FacilityIslands.selectAll().where { FacilityIslands.isDeleted eq false }
        if (facilityId != null) query.andWhere { FacilityIslands.facilityId eq facilityId }
        query.map { it.toIslandMap() }
    }

    override fun getIsland(id: String) = transaction {
        FacilityIslands.selectAll()
            .where { (FacilityIslands.id eq id) and (FacilityIslands.isDeleted eq false) }
            .singleOrNull()?.toIslandMap()
    }

    override fun upsertIsland(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val islandTypeCode = data["island_type"] as String
        val resolvedIslandTypeId = data["island_type_id"] as? String
            ?: IslandTypes.selectAll()
                .where { IslandTypes.code eq islandTypeCode }
                .singleOrNull()?.get(IslandTypes.id)

        FacilityIslands.upsert {
            it[FacilityIslands.id] = id
            it[facilityId] = data["facility_id"] as String
            it[commissioningNumber] = data["commissioning_number"] as? String
            it[islandType] = islandTypeCode
            it[islandTypeId] = resolvedIslandTypeId
            it[serialNumber] = data["serial_number"] as String
            it[modelNumber] = data["model_number"] as? String
            it[model] = data["model"] as? String
            it[installationDate] = (data["installation_date"] as? Number)?.toLong()
            it[warrantyExpiration] = (data["warranty_expiration"] as? Number)?.toLong()
            it[operatingHours] = (data["operating_hours"] as? Number)?.toLong() ?: 0L
            it[cycleCount] = (data["cycle_count"] as? Number)?.toLong() ?: 0L
            it[lastMaintenanceDate] = (data["last_maintenance_date"] as? Number)?.toLong()
            it[nextScheduledMaintenance] = (data["next_scheduled_maintenance"] as? Number)?.toLong()
            it[customName] = data["custom_name"] as? String
            it[location] = data["location"] as? String
            it[notes] = data["notes"] as? String
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now; it[syncedAt] = null; it[isDeleted] = false
        }
        getIsland(id)!!
    }

    override fun softDeleteIsland(id: String) = transaction {
        FacilityIslands.update({ FacilityIslands.id eq id }) {
            it[isDeleted] = true; it[updatedAt] = System.currentTimeMillis()
        }; Unit
    }

    // ── Mechanical units ──────────────────────────────────────────────────────

    override fun getMechanicalUnits(islandId: String?) = transaction {
        val query = MechanicalUnits.selectAll().where { MechanicalUnits.isDeleted eq false }
        if (islandId != null) query.andWhere { MechanicalUnits.islandId eq islandId }
        query.map { it.toMechanicalUnitMap() }
    }

    override fun getMechanicalUnit(id: String) = transaction {
        MechanicalUnits.selectAll()
            .where { (MechanicalUnits.id eq id) and (MechanicalUnits.isDeleted eq false) }
            .singleOrNull()?.toMechanicalUnitMap()
    }

    override fun upsertMechanicalUnit(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        MechanicalUnits.upsert {
            it[MechanicalUnits.id] = id
            it[islandId] = data["island_id"] as String
            it[unitType] = data["unit_type"] as String
            it[name] = data["name"] as String
            it[serialNumber] = data["serial_number"] as? String
            it[model] = data["model"] as? String
            it[notes] = data["notes"] as? String
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now; it[syncedAt] = null; it[isDeleted] = false
        }
        getMechanicalUnit(id)!!
    }

    override fun softDeleteMechanicalUnit(id: String) = transaction {
        MechanicalUnits.update({ MechanicalUnits.id eq id }) {
            it[isDeleted] = true; it[updatedAt] = System.currentTimeMillis()
        }; Unit
    }

// ─── Maintenance logs ─────────────────────────────────────────────────────

    override fun getMaintenanceLogs(islandId: String?) = transaction {
        val query = MaintenanceLogs.selectAll().where { MaintenanceLogs.isDeleted eq false }
        if (islandId != null) query.andWhere { MaintenanceLogs.islandId eq islandId }
        query.map { it.toMaintenanceLogMap() }
    }

    override fun getMaintenanceLog(id: String) = transaction {
        MaintenanceLogs.selectAll()
            .where { (MaintenanceLogs.id eq id) and (MaintenanceLogs.isDeleted eq false) }
            .singleOrNull()?.toMaintenanceLogMap()
    }

    override fun upsertMaintenanceLog(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        MaintenanceLogs.upsert {
            it[MaintenanceLogs.id] = id
            it[islandId] = data["island_id"] as String
            it[operationType] = data["operation_type"] as String
            it[customOperationLabel] = data["custom_operation_label"] as? String
            it[mechanicalUnitId] = data["mechanical_unit_id"] as? String
            it[componentLabel] = data["component_label"] as? String
            it[description] = data["description"] as String
            it[technicianName] = data["technician_name"] as String
            it[technicianCompany] = data["technician_company"] as? String
            it[operatingHoursAtEvent] = data["operating_hours_at_event"] as? Int
            it[cycleCountAtEvent] = data["cycle_count_at_event"] as? Long
            it[outcome] = data["outcome"] as String
            it[durationMinutes] = data["duration_minutes"] as? Int
            it[notes] = data["notes"] as? String
            it[performedAt] = (data["performed_at"] as? Number)?.toLong() ?: now
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now; it[syncedAt] = null; it[isDeleted] = false
        }
        getMaintenanceLog(id)!!
    }

    override fun softDeleteMaintenanceLog(id: String) = transaction {
        MaintenanceLogs.update({ MaintenanceLogs.id eq id }) {
            it[isDeleted] = true; it[updatedAt] = System.currentTimeMillis()
        }; Unit
    }

    // ── Island types ──────────────────────────────────────────────────────────

    override fun getIslandTypes(includeInactive: Boolean) = transaction {
        val query = IslandTypes.selectAll().where { IslandTypes.isDeleted eq false }
        if (!includeInactive) query.andWhere { IslandTypes.isActive eq true }
        query.orderBy(IslandTypes.sortOrder).map { it.toIslandTypeMap() }
    }

    override fun getIslandType(id: String) = transaction {
        IslandTypes.selectAll()
            .where { (IslandTypes.id eq id) and (IslandTypes.isDeleted eq false) }
            .singleOrNull()?.toIslandTypeMap()
    }

    override fun upsertIslandType(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        IslandTypes.upsert {
            it[IslandTypes.id] = id
            it[code] = data["code"] as String
            it[label] = data["label"] as String
            it[description] = data["description"] as? String
            it[iconName] = data["icon_name"] as? String
            it[maintenanceIntervalDays] = (data["maintenance_interval_days"] as? Number)?.toInt() ?: 180
            it[sortOrder] = (data["sort_order"] as? Number)?.toInt() ?: 0
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now
            it[syncedAt] = null
            it[isDeleted] = false
        }
        getIslandType(id)!!
    }

    override fun softDeleteIslandType(id: String) = transaction {
        IslandTypes.update({ IslandTypes.id eq id }) {
            it[isActive] = false
            it[updatedAt] = System.currentTimeMillis()
        }
        Unit
    }

    // ── Module Types ──────────────────────────────────────────────────────────

    override fun getModuleTypes(includeInactive: Boolean) = transaction {
        val q = ModuleTypes.selectAll().where { ModuleTypes.isDeleted eq false }
        if (!includeInactive) q.andWhere { ModuleTypes.isActive eq true }
        q.orderBy(ModuleTypes.sortOrder).map { it.toModuleTypeMap() }
    }

    override fun getModuleType(id: String) = transaction {
        ModuleTypes.selectAll()
            .where { (ModuleTypes.id eq id) and (ModuleTypes.isDeleted eq false) }
            .singleOrNull()?.toModuleTypeMap()
    }

    override fun upsertModuleType(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        ModuleTypes.upsert {
            it[ModuleTypes.id] = id
            it[code] = data["code"] as String
            it[label] = data["label"] as String
            it[description] = data["description"] as? String
            it[iconName] = data["icon_name"] as? String
            it[sortOrder] = (data["sort_order"] as? Number)?.toInt() ?: 0
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now
            it[syncedAt] = null
            it[isDeleted] = false
        }
        getModuleType(id)!!
    }

    override fun softDeleteModuleType(id: String) = transaction {
        ModuleTypes.update({ ModuleTypes.id eq id }) {
            it[isActive] = false
            it[updatedAt] = System.currentTimeMillis()
        }
        Unit
    }

    // ── Criticality Levels ────────────────────────────────────────────────────

    override fun getCriticalityLevels(includeInactive: Boolean) = transaction {
        val q = CriticalityLevels.selectAll().where { CriticalityLevels.isDeleted eq false }
        if (!includeInactive) q.andWhere { CriticalityLevels.isActive eq true }
        q.orderBy(CriticalityLevels.sortOrder).map { it.toCriticalityLevelMap() }
    }

    override fun getCriticalityLevel(id: String) = transaction {
        CriticalityLevels.selectAll()
            .where { (CriticalityLevels.id eq id) and (CriticalityLevels.isDeleted eq false) }
            .singleOrNull()?.toCriticalityLevelMap()
    }

    override fun upsertCriticalityLevel(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        CriticalityLevels.upsert {
            it[CriticalityLevels.id] = id
            it[code] = data["code"] as String
            it[label] = data["label"] as String
            it[priority] = (data["priority"] as? Number)?.toInt() ?: 0
            it[colorHex] = data["color_hex"] as? String ?: "#808080"
            it[iconEmoji] = data["icon_emoji"] as? String
            it[sortOrder] = (data["sort_order"] as? Number)?.toInt() ?: 0
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now
            it[syncedAt] = null
            it[isDeleted] = false
        }
        getCriticalityLevel(id)!!
    }

    override fun softDeleteCriticalityLevel(id: String) = transaction {
        CriticalityLevels.update({ CriticalityLevels.id eq id }) {
            it[isActive] = false
            it[updatedAt] = System.currentTimeMillis()
        }
        Unit
    }

    // ── Checkup Statuses ──────────────────────────────────────────────────────

    override fun getCheckupStatuses(includeInactive: Boolean) = transaction {
        val q = CheckupStatuses.selectAll().where { CheckupStatuses.isDeleted eq false }
        if (!includeInactive) q.andWhere { CheckupStatuses.isActive eq true }
        q.orderBy(CheckupStatuses.sortOrder).map { it.toCheckupStatusMap() }
    }

    override fun getCheckupStatus(id: String) = transaction {
        CheckupStatuses.selectAll()
            .where { (CheckupStatuses.id eq id) and (CheckupStatuses.isDeleted eq false) }
            .singleOrNull()?.toCheckupStatusMap()
    }

    override fun upsertCheckupStatus(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        CheckupStatuses.upsert {
            it[CheckupStatuses.id] = id
            it[code] = data["code"] as String
            it[label] = data["label"] as String
            it[colorHex] = data["color_hex"] as? String ?: "#808080"
            it[iconEmoji] = data["icon_emoji"] as? String
            it[sortOrder] = (data["sort_order"] as? Number)?.toInt() ?: 0
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[blocksDeletion] = data["blocks_deletion"] as? Boolean ?: false
            it[marksCompletion] = data["marks_completion"] as? Boolean ?: false
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now
            it[syncedAt] = null
            it[isDeleted] = false
        }
        getCheckupStatus(id)!!
    }

    override fun softDeleteCheckupStatus(id: String) = transaction {
        CheckupStatuses.update({ CheckupStatuses.id eq id }) {
            it[isActive] = false
            it[updatedAt] = System.currentTimeMillis()
        }
        Unit
    }

    // ── Check Item Templates ──────────────────────────────────────────────────

    override fun getCheckItemTemplates(moduleTypeId: String?) = transaction {
        val q = CheckItemTemplates.selectAll().where { CheckItemTemplates.isDeleted eq false }
        if (moduleTypeId != null) q.andWhere { CheckItemTemplates.moduleTypeId eq moduleTypeId }
        q.orderBy(CheckItemTemplates.orderIndex).map { it.toCheckItemTemplateMap() }
    }

    override fun getCheckItemTemplate(id: String) = transaction {
        CheckItemTemplates.selectAll()
            .where { (CheckItemTemplates.id eq id) and (CheckItemTemplates.isDeleted eq false) }
            .singleOrNull()?.toCheckItemTemplateMap()
    }

    override fun upsertCheckItemTemplate(data: Map<String, Any?>): Map<String, Any?> = transaction {
        val id = data["id"] as? String ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        CheckItemTemplates.upsert {
            it[CheckItemTemplates.id] = id
            it[moduleTypeId] = data["module_type_id"] as String
            it[category] = data["category"] as? String ?: ""
            it[description] = data["description"] as String
            it[criticalityId] = data["criticality_id"] as String
            it[orderIndex] = (data["order_index"] as? Number)?.toInt() ?: 0
            it[isActive] = data["is_active"] as? Boolean ?: true
            it[createdAt] = (data["created_at"] as? Number)?.toLong() ?: now
            it[updatedAt] = now
            it[syncedAt] = null
            it[isDeleted] = false
        }
        getCheckItemTemplate(id)!!
    }

    override fun deleteCheckItemTemplate(id: String) = transaction {
        CheckItemTemplates.update({ CheckItemTemplates.id eq id }) {
            it[isDeleted] = true
            it[updatedAt] = System.currentTimeMillis()
        }
        Unit
    }

    // ── Checkups (read-only) ──────────────────────────────────────────────────

    override fun getCheckupsForClient(clientId: String) = transaction {
        Checkups
            .join(CheckupIslandAssociations, JoinType.INNER, Checkups.id, CheckupIslandAssociations.checkupId)
            .join(FacilityIslands, JoinType.INNER, CheckupIslandAssociations.islandId, FacilityIslands.id)
            .join(Facilities, JoinType.INNER, FacilityIslands.facilityId, Facilities.id)
            .select(Checkups.columns)
            .where { (Facilities.clientId eq clientId) and (Checkups.isDeleted eq false) }
            .orderBy(Checkups.updatedAt, SortOrder.DESC)
            .distinctBy { it[Checkups.id] }
            .map { it.toCheckupMap() }
    }

    override fun getCheckupsForIsland(islandId: String) = transaction {
        Checkups
            .join(CheckupIslandAssociations, JoinType.INNER, Checkups.id, CheckupIslandAssociations.checkupId)
            .select(Checkups.columns)
            .where { (CheckupIslandAssociations.islandId eq islandId) and (Checkups.isDeleted eq false) }
            .orderBy(Checkups.updatedAt, SortOrder.DESC)
            .distinctBy { it[Checkups.id] }
            .map { it.toCheckupMap() }
    }
}

// ─── ResultRow → Map helpers ──────────────────────────────────────────────────

private fun ResultRow.toClientMap() = mapOf(
    "id" to this[Clients.id],
    "company_name" to this[Clients.companyName],
    "notes" to this[Clients.notes],
    "headquarters_json" to this[Clients.headquartersJson],
    "is_active" to this[Clients.isActive],
    "created_at" to this[Clients.createdAt],
    "updated_at" to this[Clients.updatedAt],
    "synced_at" to this[Clients.syncedAt],
    "is_deleted" to this[Clients.isDeleted]
)

private fun ResultRow.toContactMap() = mapOf(
    "id" to this[Contacts.id],
    "client_id" to this[Contacts.clientId],
    "first_name" to this[Contacts.firstName],
    "last_name" to this[Contacts.lastName],
    "title" to this[Contacts.title],
    "role" to this[Contacts.role],
    "department" to this[Contacts.department],
    "phone" to this[Contacts.phone],
    "mobile_phone" to this[Contacts.mobilePhone],
    "email" to this[Contacts.email],
    "alternative_email" to this[Contacts.alternativeEmail],
    "is_primary" to this[Contacts.isPrimary],
    "preferred_contact_method" to this[Contacts.preferredContactMethod],
    "notes" to this[Contacts.notes],
    "is_active" to this[Contacts.isActive],
    "created_at" to this[Contacts.createdAt],
    "updated_at" to this[Contacts.updatedAt],
    "synced_at" to this[Contacts.syncedAt],
    "is_deleted" to this[Contacts.isDeleted]
)

private fun ResultRow.toContractMap() = mapOf(
    "id" to this[Contracts.id],
    "client_id" to this[Contracts.clientId],
    "name" to this[Contracts.name],
    "description" to this[Contracts.description],
    "start_date" to this[Contracts.startDate],
    "end_date" to this[Contracts.endDate],
    "has_priority" to this[Contracts.hasPriority],
    "has_remote_assistance" to this[Contracts.hasRemoteAssistance],
    "has_maintenance" to this[Contracts.hasMaintenance],
    "notes" to this[Contracts.notes],
    "is_active" to this[Contracts.isActive],
    "created_at" to this[Contracts.createdAt],
    "updated_at" to this[Contracts.updatedAt],
    "synced_at" to this[Contracts.syncedAt],
    "is_deleted" to this[Contracts.isDeleted]
)

private fun ResultRow.toFacilityMap() = mapOf(
    "id" to this[Facilities.id],
    "client_id" to this[Facilities.clientId],
    "name" to this[Facilities.name],
    "code" to this[Facilities.code],
    "notes" to this[Facilities.notes],
    "facility_type" to this[Facilities.facilityType],
    "address_json" to this[Facilities.addressJson],
    "is_primary" to this[Facilities.isPrimary],
    "is_active" to this[Facilities.isActive],
    "created_at" to this[Facilities.createdAt],
    "updated_at" to this[Facilities.updatedAt],
    "synced_at" to this[Facilities.syncedAt],
    "is_deleted" to this[Facilities.isDeleted]
)

private fun ResultRow.toIslandTypeMap() = mapOf(
    "id" to this[IslandTypes.id],
    "code" to this[IslandTypes.code],
    "label" to this[IslandTypes.label],
    "description" to this[IslandTypes.description],
    "icon_name" to this[IslandTypes.iconName],
    "maintenance_interval_days" to this[IslandTypes.maintenanceIntervalDays],
    "sort_order" to this[IslandTypes.sortOrder],
    "is_active" to this[IslandTypes.isActive],
    "created_at" to this[IslandTypes.createdAt],
    "updated_at" to this[IslandTypes.updatedAt],
    "synced_at" to this[IslandTypes.syncedAt],
    "is_deleted" to this[IslandTypes.isDeleted]
)

private fun ResultRow.toIslandMap() = mapOf(
    "id" to this[FacilityIslands.id],
    "facility_id" to this[FacilityIslands.facilityId],
    "commissioning_number" to this[FacilityIslands.commissioningNumber],
    "island_type" to this[FacilityIslands.islandType],
    "island_type_id" to this[FacilityIslands.islandTypeId],
    "serial_number" to this[FacilityIslands.serialNumber],
    "model_number" to this[FacilityIslands.modelNumber],
    "model" to this[FacilityIslands.model],
    "installation_date" to this[FacilityIslands.installationDate],
    "warranty_expiration" to this[FacilityIslands.warrantyExpiration],
    "operating_hours" to this[FacilityIslands.operatingHours],
    "cycle_count" to this[FacilityIslands.cycleCount],
    "last_maintenance_date" to this[FacilityIslands.lastMaintenanceDate],
    "next_scheduled_maintenance" to this[FacilityIslands.nextScheduledMaintenance],
    "custom_name" to this[FacilityIslands.customName],
    "location" to this[FacilityIslands.location],
    "notes" to this[FacilityIslands.notes],
    "is_active" to this[FacilityIslands.isActive],
    "created_at" to this[FacilityIslands.createdAt],
    "updated_at" to this[FacilityIslands.updatedAt],
    "synced_at" to this[FacilityIslands.syncedAt],
    "is_deleted" to this[FacilityIslands.isDeleted]
)

private fun ResultRow.toMechanicalUnitMap() = mapOf(
    "id" to this[MechanicalUnits.id],
    "island_id" to this[MechanicalUnits.islandId],
    "unit_type" to this[MechanicalUnits.unitType],
    "name" to this[MechanicalUnits.name],
    "serial_number" to this[MechanicalUnits.serialNumber],
    "model" to this[MechanicalUnits.model],
    "notes" to this[MechanicalUnits.notes],
    "is_active" to this[MechanicalUnits.isActive],
    "created_at" to this[MechanicalUnits.createdAt],
    "updated_at" to this[MechanicalUnits.updatedAt],
    "synced_at" to this[MechanicalUnits.syncedAt],
    "is_deleted" to this[MechanicalUnits.isDeleted]
)

private fun ResultRow.toCheckupMap() = mapOf(
    "id"                            to this[Checkups.id],
    "client_company_name"           to this[Checkups.clientCompanyName],
    "client_contact_person"         to this[Checkups.clientContactPerson],
    "client_site"                   to this[Checkups.clientSite],
    "island_serial_number"          to this[Checkups.islandSerialNumber],
    "island_model"                  to this[Checkups.islandModel],
    "island_type"                   to this[Checkups.islandType],
    "island_type_id"                to this[Checkups.islandTypeId],
    "technician_name"               to this[Checkups.technicianName],
    "technician_company"            to this[Checkups.technicianCompany],
    "checkup_date"                  to this[Checkups.checkupDate],
    "header_notes"                  to this[Checkups.headerNotes],
    "status"                        to this[Checkups.status],
    "created_at"                    to this[Checkups.createdAt],
    "updated_at"                    to this[Checkups.updatedAt],
    "completed_at"                  to this[Checkups.completedAt],
    "is_deleted"                    to this[Checkups.isDeleted]
)

private fun ResultRow.toModuleTypeMap() = mapOf(
    "id" to this[ModuleTypes.id],
    "code" to this[ModuleTypes.code],
    "label" to this[ModuleTypes.label],
    "description" to this[ModuleTypes.description],
    "icon_name" to this[ModuleTypes.iconName],
    "sort_order" to this[ModuleTypes.sortOrder],
    "is_active" to this[ModuleTypes.isActive],
    "created_at" to this[ModuleTypes.createdAt],
    "updated_at" to this[ModuleTypes.updatedAt],
    "synced_at" to this[ModuleTypes.syncedAt],
    "is_deleted" to this[ModuleTypes.isDeleted]
)

private fun ResultRow.toCriticalityLevelMap() = mapOf(
    "id" to this[CriticalityLevels.id],
    "code" to this[CriticalityLevels.code],
    "label" to this[CriticalityLevels.label],
    "priority" to this[CriticalityLevels.priority],
    "color_hex" to this[CriticalityLevels.colorHex],
    "icon_emoji" to this[CriticalityLevels.iconEmoji],
    "sort_order" to this[CriticalityLevels.sortOrder],
    "is_active" to this[CriticalityLevels.isActive],
    "created_at" to this[CriticalityLevels.createdAt],
    "updated_at" to this[CriticalityLevels.updatedAt],
    "synced_at" to this[CriticalityLevels.syncedAt],
    "is_deleted" to this[CriticalityLevels.isDeleted]
)

private fun ResultRow.toCheckupStatusMap() = mapOf(
    "id" to this[CheckupStatuses.id],
    "code" to this[CheckupStatuses.code],
    "label" to this[CheckupStatuses.label],
    "color_hex" to this[CheckupStatuses.colorHex],
    "icon_emoji" to this[CheckupStatuses.iconEmoji],
    "sort_order" to this[CheckupStatuses.sortOrder],
    "is_active" to this[CheckupStatuses.isActive],
    "blocks_deletion" to this[CheckupStatuses.blocksDeletion],
    "marks_completion" to this[CheckupStatuses.marksCompletion],
    "created_at" to this[CheckupStatuses.createdAt],
    "updated_at" to this[CheckupStatuses.updatedAt],
    "synced_at" to this[CheckupStatuses.syncedAt],
    "is_deleted" to this[CheckupStatuses.isDeleted]
)

private fun ResultRow.toCheckItemTemplateMap() = mapOf(
    "id" to this[CheckItemTemplates.id],
    "module_type_id" to this[CheckItemTemplates.moduleTypeId],
    "category" to this[CheckItemTemplates.category],
    "description" to this[CheckItemTemplates.description],
    "criticality_id" to this[CheckItemTemplates.criticalityId],
    "order_index" to this[CheckItemTemplates.orderIndex],
    "is_active" to this[CheckItemTemplates.isActive],
    "created_at" to this[CheckItemTemplates.createdAt],
    "updated_at" to this[CheckItemTemplates.updatedAt],
    "synced_at" to this[CheckItemTemplates.syncedAt],
    "is_deleted" to this[CheckItemTemplates.isDeleted]
)

private fun ResultRow.toMaintenanceLogMap() = mapOf(
    "id" to this[MaintenanceLogs.id],
    "island_id" to this[MaintenanceLogs.islandId],
    "operation_type" to this[MaintenanceLogs.operationType],
    "custom_operation_label" to this[MaintenanceLogs.customOperationLabel],
    "mechanical_unit_id" to this[MaintenanceLogs.mechanicalUnitId],
    "component_label" to this[MaintenanceLogs.componentLabel],
    "description" to this[MaintenanceLogs.description],
    "technician_name" to this[MaintenanceLogs.technicianName],
    "technician_company" to this[MaintenanceLogs.technicianCompany],
    "operating_hours_at_event" to this[MaintenanceLogs.operatingHoursAtEvent],
    "cycle_count_at_event" to this[MaintenanceLogs.cycleCountAtEvent],
    "outcome" to this[MaintenanceLogs.outcome],
    "duration_minutes" to this[MaintenanceLogs.durationMinutes],
    "notes" to this[MaintenanceLogs.notes],
    "performed_at" to this[MaintenanceLogs.performedAt],
    "created_at" to this[MaintenanceLogs.createdAt],
    "updated_at" to this[MaintenanceLogs.updatedAt],
    "synced_at" to this[MaintenanceLogs.syncedAt],
    "is_active" to this[MaintenanceLogs.isActive],
    "is_deleted" to this[MaintenanceLogs.isDeleted]
)
