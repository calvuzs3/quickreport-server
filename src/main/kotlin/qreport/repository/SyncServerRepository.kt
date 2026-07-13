package net.calvuz.qreport.repository

import net.calvuz.qreport.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

private const val ROLE_ADMIN = "ADMIN"

/**
 * Server-side repository for sync operations.
 *
 * pull: returns all records updated after [since] that are not from [deviceId]
 *       (no point sending back what the device just pushed).
 * push: upserts incoming records and logs the sync session.
 *
 * Table definitions moved to Tables.kt (same package) to allow sharing
 * with ExposedCrudRepository without duplication.
 */
class SyncServerRepository {

    // ===== PULL — return records changed since [since] =====

    fun pull(since: Long): SyncPayload = transaction {
        val now = System.currentTimeMillis()

        // Island types are always returned in full (small table, no delta filter).
        // Inactive/deleted ones are included too, so clients can deactivate their local copy.
        val islandTypes = IslandTypes.selectAll()
            .orderBy(IslandTypes.sortOrder)
            .map { it.toIslandTypeDto() }

        // ── Fetch delta-filtered entities first, so we know which ancestor IDs
        //    must be force-included below regardless of the ancestor's own
        //    updatedAt. Without this, e.g. a client whose facility just changed
        //    (but who wasn't itself touched recently) can be excluded from the
        //    payload while its facility is included, crashing the Android client
        //    on a FOREIGN KEY constraint (facilities.client_id). Same pattern
        //    applies to facilities/contacts/contracts/documents → clients and
        //    facilities → islands/documents. ──

        val contacts = Contacts.selectAll()
            .where { Contacts.updatedAt greater since }
            .map { it.toContactDto() }

        val contracts = Contracts.selectAll()
            .where { Contracts.updatedAt greater since }
            .map { it.toContractDto() }

        // Full pull — associations reference island IDs that may predate `since`.
        // Because every island is always returned, any FK into FacilityIslands
        // (mechanical units, maintenance logs, documents.island_id) is always safe
        // and needs no force-inclusion logic below.
        val islands = FacilityIslands.selectAll()
            .map { it.toFacilityIslandDto() }

        val units = MechanicalUnits.selectAll()
            .where { MechanicalUnits.updatedAt greater since }
            .map { it.toMechanicalUnitDto() }

        val logs = MaintenanceLogs.selectAll()
            .where { MaintenanceLogs.updatedAt greater since }
            .map { it.toMaintenanceLogDto() }

        val documents = IslandDocuments.selectAll()
            .where { IslandDocuments.updatedAt greater since }
            .map { it.toDocumentDto() }

        // Facilities: force-include ones referenced by an island (always fully
        // pulled above) or a document, even if the facility itself hasn't changed.
        val neededFacilityIds = (islands.map { it.facilityId } + documents.mapNotNull { it.facilityId }).toHashSet()
        val facilities = if (neededFacilityIds.isEmpty())
            Facilities.selectAll()
                .where { Facilities.updatedAt greater since }
                .map { it.toFacilityDto() }
        else
            Facilities.selectAll()
                .where { (Facilities.updatedAt greater since) or (Facilities.id inList neededFacilityIds) }
                .map { it.toFacilityDto() }

        // Clients: force-include ones referenced by a facility, contact, contract
        // or document, even if the client itself hasn't changed.
        val neededClientIds = (facilities.map { it.clientId } +
            contacts.map { it.clientId } +
            contracts.map { it.clientId } +
            documents.mapNotNull { it.clientId }).toHashSet()
        val clients = if (neededClientIds.isEmpty())
            Clients.selectAll()
                .where { Clients.updatedAt greater since }
                .map { it.toClientDto() }
        else
            Clients.selectAll()
                .where { (Clients.updatedAt greater since) or (Clients.id inList neededClientIds) }
                .map { it.toClientDto() }

        // Checkup master data — returned in full (small tables, no delta filter)
        val moduleTypes = ModuleTypes.selectAll()
            .orderBy(ModuleTypes.sortOrder)
            .map { it.toModuleTypeDto() }

        val criticalityLevels = CriticalityLevels.selectAll()
            .orderBy(CriticalityLevels.sortOrder)
            .map { it.toCriticalityLevelDto() }

        val checkupStatuses = CheckupStatuses.selectAll()
            .orderBy(CheckupStatuses.sortOrder)
            .map { it.toCheckUpStatusDto() }

        val checkItemTemplates = CheckItemTemplates.selectAll()
            .where { CheckItemTemplates.updatedAt greater since }
            .map { it.toCheckItemTemplateDto() }

        val moduleTypeIslandTypeLinks = ModuleTypeIslandTypes.selectAll()
            .map { ModuleTypeIslandTypeLinkDto(
                islandTypeId = it[ModuleTypeIslandTypes.islandTypeId],
                moduleTypeId = it[ModuleTypeIslandTypes.moduleTypeId]
            ) }

        val checkUpStatusTransitions = CheckupStatusTransitions.selectAll()
            .map { CheckUpStatusTransitionDto(
                fromStatusId = it[CheckupStatusTransitions.fromStatusId],
                toStatusId = it[CheckupStatusTransitions.toStatusId]
            ) }

        val checkups = Checkups.selectAll()
            .where { Checkups.updatedAt greater since }
            .map { it.toCheckUpRecordDto() }

        val checkupAssociations = CheckupIslandAssociations.selectAll()
            .where { CheckupIslandAssociations.updatedAt greater since }
            .map { it.toCheckUpIslandAssociationDto() }

        val checkupIds = checkups.map { it.id }
        val checkupItems = if (checkupIds.isNotEmpty())
            CheckItems.selectAll()
                .where { CheckItems.checkupId inList checkupIds }
                .map { it.toCheckItemDto() }
        else emptyList()

        val checkItemIds = checkupItems.map { it.id }
        val photos = if (checkItemIds.isNotEmpty())
            Photos.selectAll()
                .where { Photos.checkItemId inList checkItemIds }
                .map { it.toPhotoDto() }
        else emptyList()

        SyncPayload(
            deviceId = "server",
            syncTimestamp = now,
            islandTypes = islandTypes,
            clients = clients,
            contacts = contacts,
            contracts = contracts,
            facilities = facilities,
            facilityIslands = islands,
            mechanicalUnits = units,
            maintenanceLogs = logs,
            documents = documents,
            moduleTypes = moduleTypes,
            criticalityLevels = criticalityLevels,
            checkupStatuses = checkupStatuses,
            checkItemTemplates = checkItemTemplates,
            moduleTypeIslandTypeLinks = moduleTypeIslandTypeLinks,
            checkUpStatusTransitions = checkUpStatusTransitions,
            checkups = checkups,
            checkupIslandAssociations = checkupAssociations,
            checkupItems = checkupItems,
            photos = photos
        )
    }

    // ===== PUSH — upsert incoming records =====

    fun push(payload: SyncPayload, isAdmin: Boolean = false): List<String> = transaction {
        val acceptedIds = mutableListOf<String>()

        payload.islandTypes.forEach { dto ->
            upsertIslandType(dto)
            acceptedIds.add(dto.id)
        }
        payload.clients.forEach { dto ->
            upsertClient(dto)
            acceptedIds.add(dto.id)
        }
        payload.contacts.forEach { dto ->
            upsertContact(dto)
            acceptedIds.add(dto.id)
        }
        payload.contracts.forEach { dto ->
            upsertContract(dto)
            acceptedIds.add(dto.id)
        }
        payload.facilities.forEach { dto ->
            upsertFacility(dto)
            acceptedIds.add(dto.id)
        }
        payload.facilityIslands.forEach { dto ->
            upsertFacilityIsland(dto)
            acceptedIds.add(dto.id)
        }
        payload.mechanicalUnits.forEach { dto ->
            upsertMechanicalUnit(dto)
            acceptedIds.add(dto.id)
        }
        payload.maintenanceLogs.forEach { dto ->
            upsertMaintenanceLog(dto)
            acceptedIds.add(dto.id)
        }
        payload.documents.forEach { dto ->
            upsertDocument(dto)
            acceptedIds.add(dto.id)
        }

        // Checkup records — all users can push their own checkups
        payload.checkups.forEach { dto ->
            upsertCheckUpRecord(dto)
            acceptedIds.add(dto.id)
        }
        payload.checkupIslandAssociations.forEach { dto ->
            upsertCheckUpIslandAssociation(dto)
            acceptedIds.add(dto.id)
        }
        if (payload.checkupItems.isNotEmpty()) {
            payload.checkupItems.groupBy { it.checkupId }.forEach { (groupCheckupId, items) ->
                CheckItems.deleteWhere { with(SqlExpressionBuilder) { checkupId eq groupCheckupId } }
                items.forEach { dto ->
                    CheckItems.insert {
                        it[id]            = dto.id
                        it[CheckItems.checkupId]     = dto.checkupId
                        it[moduleType]    = dto.moduleType
                        it[moduleTypeId]  = dto.moduleTypeId
                        it[itemCode]      = dto.itemCode
                        it[description]   = dto.description
                        it[status]        = dto.status
                        it[criticality]   = dto.criticality
                        it[criticalityId] = dto.criticalityId
                        it[notes]         = dto.notes
                        it[checkedAt]     = dto.checkedAt
                        it[orderIndex]    = dto.orderIndex
                    }
                    acceptedIds.add(dto.id)
                }
            }

            // Photos are wholesale-replaced for every check item that was just
            // replaced above — this also clears photos for check items whose
            // photo set became empty (nothing to re-insert for them).
            val pushedCheckItemIds = payload.checkupItems.map { it.id }
            Photos.deleteWhere { with(SqlExpressionBuilder) { checkItemId inList pushedCheckItemIds } }
            payload.photos.forEach { dto ->
                Photos.insert {
                    it[id]                 = dto.id
                    it[Photos.checkItemId] = dto.checkItemId
                    it[fileName]           = dto.fileName
                    it[caption]            = dto.caption
                    it[takenAt]            = dto.takenAt
                    it[fileSize]           = dto.fileSize
                    it[orderIndex]         = dto.orderIndex
                    it[width]              = dto.width
                    it[height]             = dto.height
                }
                acceptedIds.add(dto.id)
            }
        }

        // Checkup master data — only ADMIN can push these
        if (isAdmin) {
            payload.moduleTypes.forEach { dto ->
                upsertModuleType(dto)
                acceptedIds.add(dto.id)
            }
            payload.criticalityLevels.forEach { dto ->
                upsertCriticalityLevel(dto)
                acceptedIds.add(dto.id)
            }
            payload.checkupStatuses.forEach { dto ->
                upsertCheckUpStatus(dto)
                acceptedIds.add(dto.id)
            }
            payload.checkItemTemplates.forEach { dto ->
                upsertCheckItemTemplate(dto)
                acceptedIds.add(dto.id)
            }
            if (payload.moduleTypeIslandTypeLinks.isNotEmpty()) {
                ModuleTypeIslandTypes.deleteAll()
                payload.moduleTypeIslandTypeLinks.forEach { dto ->
                    ModuleTypeIslandTypes.insert {
                        it[islandTypeId] = dto.islandTypeId
                        it[moduleTypeId] = dto.moduleTypeId
                    }
                }
            }
            if (payload.checkUpStatusTransitions.isNotEmpty()) {
                CheckupStatusTransitions.deleteAll()
                payload.checkUpStatusTransitions.forEach { dto ->
                    CheckupStatusTransitions.insert {
                        it[fromStatusId] = dto.fromStatusId
                        it[toStatusId] = dto.toStatusId
                    }
                }
            }
        }

        // Log the sync session
        SyncLog.insert {
            it[SyncLog.deviceId] = payload.deviceId
            it[SyncLog.syncedAt] = payload.syncTimestamp
            it[SyncLog.recordsPushed] = acceptedIds.size
            it[SyncLog.recordsPulled] = 0
        }

        acceptedIds
    }

    // ===== UPSERT HELPERS =====

    private fun upsertIslandType(dto: IslandTypeDto) {
        val exists = IslandTypes.selectAll().where { IslandTypes.id eq dto.id }.count() > 0
        if (exists) {
            IslandTypes.update({ IslandTypes.id eq dto.id }) {
                it[code] = dto.code
                it[label] = dto.label
                it[description] = dto.description
                it[iconName] = dto.iconName
                it[maintenanceIntervalDays] = dto.maintenanceIntervalDays
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            IslandTypes.insert {
                it[id] = dto.id
                it[code] = dto.code
                it[label] = dto.label
                it[description] = dto.description
                it[iconName] = dto.iconName
                it[maintenanceIntervalDays] = dto.maintenanceIntervalDays
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertClient(dto: ClientDto) {
        val exists = Clients.selectAll().where { Clients.id eq dto.id }.count() > 0
        if (exists) {
            Clients.update({ Clients.id eq dto.id }) {
                it[companyName] = dto.companyName
                it[notes] = dto.notes
                it[headquartersJson] = dto.headquartersJson
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            Clients.insert {
                it[id] = dto.id
                it[companyName] = dto.companyName
                it[notes] = dto.notes
                it[headquartersJson] = dto.headquartersJson
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertContact(dto: ContactDto) {
        val exists = Contacts.selectAll().where { Contacts.id eq dto.id }.count() > 0
        if (exists) {
            Contacts.update({ Contacts.id eq dto.id }) {
                it[clientId] = dto.clientId
                it[firstName] = dto.firstName
                it[lastName] = dto.lastName
                it[title] = dto.title
                it[role] = dto.role
                it[department] = dto.department
                it[phone] = dto.phone
                it[mobilePhone] = dto.mobilePhone
                it[email] = dto.email
                it[alternativeEmail] = dto.alternativeEmail
                it[isPrimary] = dto.isPrimary
                it[preferredContactMethod] = dto.preferredContactMethod
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            Contacts.insert {
                it[id] = dto.id
                it[clientId] = dto.clientId
                it[firstName] = dto.firstName
                it[lastName] = dto.lastName
                it[title] = dto.title
                it[role] = dto.role
                it[department] = dto.department
                it[phone] = dto.phone
                it[mobilePhone] = dto.mobilePhone
                it[email] = dto.email
                it[alternativeEmail] = dto.alternativeEmail
                it[isPrimary] = dto.isPrimary
                it[preferredContactMethod] = dto.preferredContactMethod
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertContract(dto: ContractDto) {
        val exists = Contracts.selectAll().where { Contracts.id eq dto.id }.count() > 0
        if (exists) {
            Contracts.update({ Contracts.id eq dto.id }) {
                it[clientId] = dto.clientId
                it[name] = dto.name
                it[description] = dto.description
                it[startDate] = dto.startDate
                it[endDate] = dto.endDate
                it[hasPriority] = dto.hasPriority
                it[hasRemoteAssistance] = dto.hasRemoteAssistance
                it[hasMaintenance] = dto.hasMaintenance
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            Contracts.insert {
                it[id] = dto.id
                it[clientId] = dto.clientId
                it[name] = dto.name
                it[description] = dto.description
                it[startDate] = dto.startDate
                it[endDate] = dto.endDate
                it[hasPriority] = dto.hasPriority
                it[hasRemoteAssistance] = dto.hasRemoteAssistance
                it[hasMaintenance] = dto.hasMaintenance
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertFacility(dto: FacilityDto) {
        val exists = Facilities.selectAll().where { Facilities.id eq dto.id }.count() > 0
        if (exists) {
            Facilities.update({ Facilities.id eq dto.id }) {
                it[clientId] = dto.clientId
                it[name] = dto.name
                it[code] = dto.code
                it[notes] = dto.notes
                it[facilityType] = dto.facilityType
                it[addressJson] = dto.addressJson
                it[isPrimary] = dto.isPrimary
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            Facilities.insert {
                it[id] = dto.id
                it[clientId] = dto.clientId
                it[name] = dto.name
                it[code] = dto.code
                it[notes] = dto.notes
                it[facilityType] = dto.facilityType
                it[addressJson] = dto.addressJson
                it[isPrimary] = dto.isPrimary
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertFacilityIsland(dto: FacilityIslandDto) {
        // Resolve island_type_id: use the value from DTO if present (new clients),
        // otherwise look it up from the code string (backward compat with old clients).
        val resolvedIslandTypeId = dto.islandTypeId
            ?: IslandTypes.selectAll()
                .where { IslandTypes.code eq dto.islandType }
                .singleOrNull()?.get(IslandTypes.id)

        val exists = FacilityIslands.selectAll().where { FacilityIslands.id eq dto.id }.count() > 0
        if (exists) {
            FacilityIslands.update({ FacilityIslands.id eq dto.id }) {
                it[facilityId] = dto.facilityId
                it[commissioningNumber] = dto.commissioningNumber
                it[islandType] = dto.islandType
                it[islandTypeId] = resolvedIslandTypeId
                it[serialNumber] = dto.serialNumber
                it[modelNumber] = dto.modelNumber
                it[model] = dto.model
                it[installationDate] = dto.installationDate
                it[warrantyExpiration] = dto.warrantyExpiration
                it[operatingHours] = dto.operatingHours
                it[cycleCount] = dto.cycleCount
                it[lastMaintenanceDate] = dto.lastMaintenanceDate
                it[nextScheduledMaintenance] = dto.nextScheduledMaintenance
                it[customName] = dto.customName
                it[location] = dto.location
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            FacilityIslands.insert {
                it[id] = dto.id
                it[facilityId] = dto.facilityId
                it[commissioningNumber] = dto.commissioningNumber
                it[islandType] = dto.islandType
                it[islandTypeId] = resolvedIslandTypeId
                it[serialNumber] = dto.serialNumber
                it[modelNumber] = dto.modelNumber
                it[model] = dto.model
                it[installationDate] = dto.installationDate
                it[warrantyExpiration] = dto.warrantyExpiration
                it[operatingHours] = dto.operatingHours
                it[cycleCount] = dto.cycleCount
                it[lastMaintenanceDate] = dto.lastMaintenanceDate
                it[nextScheduledMaintenance] = dto.nextScheduledMaintenance
                it[customName] = dto.customName
                it[location] = dto.location
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertMechanicalUnit(dto: MechanicalUnitDto) {
        val exists = MechanicalUnits.selectAll().where { MechanicalUnits.id eq dto.id }.count() > 0
        if (exists) {
            MechanicalUnits.update({ MechanicalUnits.id eq dto.id }) {
                it[islandId] = dto.islandId
                it[name] = dto.name
                it[unitType] = dto.unitType
                it[serialNumber] = dto.serialNumber
                it[model] = dto.model
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            MechanicalUnits.insert {
                it[id] = dto.id
                it[islandId] = dto.islandId
                it[name] = dto.name
                it[unitType] = dto.unitType
                it[serialNumber] = dto.serialNumber
                it[model] = dto.model
                it[notes] = dto.notes
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertMaintenanceLog(dto: MaintenanceLogDto) {
        val exists = MaintenanceLogs.selectAll().where { MaintenanceLogs.id eq dto.id }.count() > 0
        if (exists) {
            MaintenanceLogs.update({ MaintenanceLogs.id eq dto.id }) {
                it[islandId] = dto.islandId
                it[operationType] = dto.operationType
                it[customOperationLabel] = dto.customOperationLabel
                it[mechanicalUnitId] = dto.mechanicalUnitId
                it[componentLabel] = dto.componentLabel
                it[description] = dto.description
                it[technicianName] = dto.technicianName
                it[technicianCompany] = dto.technicianCompany
                it[operatingHoursAtEvent] = dto.operatingHoursAtEvent
                it[cycleCountAtEvent] = dto.cycleCountAtEvent
                it[outcome] = dto.outcome
                it[durationMinutes] = dto.durationMinutes
                it[notes] = dto.notes
                it[performedAt] = dto.performedAt
                it[updatedAt] = dto.updatedAt
                it[isActive] = dto.isActive
                it[isDeleted] = dto.isDeleted
            }
        } else {
            MaintenanceLogs.insert {
                it[id] = dto.id
                it[islandId] = dto.islandId
                it[operationType] = dto.operationType
                it[customOperationLabel] = dto.customOperationLabel
                it[mechanicalUnitId] = dto.mechanicalUnitId
                it[componentLabel] = dto.componentLabel
                it[description] = dto.description
                it[technicianName] = dto.technicianName
                it[technicianCompany] = dto.technicianCompany
                it[operatingHoursAtEvent] = dto.operatingHoursAtEvent
                it[cycleCountAtEvent] = dto.cycleCountAtEvent
                it[outcome] = dto.outcome
                it[durationMinutes] = dto.durationMinutes
                it[notes] = dto.notes
                it[performedAt] = dto.performedAt
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[isActive] = dto.isActive
                it[isDeleted] = dto.isDeleted
                it[syncedAt] = dto.syncedAt
            }
        }
    }


    private fun upsertDocument(dto: DocumentDto) {
        val existing = IslandDocuments.selectAll()
            .where { IslandDocuments.id eq dto.id }
            .singleOrNull()

        if (existing == null) {
            // Insert new record
            IslandDocuments.insert {
                it[id]             = dto.id
                it[scope]          = dto.scope
                it[islandId]       = dto.islandId
                it[facilityId]     = dto.facilityId
                it[clientId]       = dto.clientId
                it[fileName]       = dto.fileName
                it[fileSize]       = dto.fileSize
                it[mimeType]       = dto.mimeType
                it[fileHash]       = dto.fileHash
                it[title]          = dto.title
                it[category]       = dto.category
                it[notes]          = dto.notes
                it[createdAt]      = dto.createdAt
                it[updatedAt]      = dto.updatedAt
                it[isActive]       = dto.isActive
                it[isDeleted]      = dto.isDeleted
                it[syncedAt]       = dto.syncedAt
                // storageBackend defaults to "local" via column default
            }
        } else {
            // Last-write-wins: only update if incoming record is newer
            val existingUpdatedAt = existing[IslandDocuments.updatedAt]
            if (dto.updatedAt > existingUpdatedAt) {
                IslandDocuments.update({ IslandDocuments.id eq dto.id }) {
                    it[scope]      = dto.scope
                    it[islandId]   = dto.islandId
                    it[facilityId] = dto.facilityId
                    it[clientId]   = dto.clientId
                    it[fileName]   = dto.fileName
                    it[fileSize]   = dto.fileSize
                    it[mimeType]   = dto.mimeType
                    // fileHash: only update if device sends a non-null hash.
                    // Preserves server hash if device sends null (hash computation
                    // failed at import time).
                    if (dto.fileHash != null) it[fileHash] = dto.fileHash
                    it[title]      = dto.title
                    it[category]   = dto.category
                    it[notes]      = dto.notes
                    it[updatedAt]  = dto.updatedAt
                    it[isActive]   = dto.isActive
                    it[isDeleted]  = dto.isDeleted
                    it[syncedAt]   = dto.syncedAt
                }
            }
        }
    }

    private fun upsertModuleType(dto: ModuleTypeDto) {
        val exists = ModuleTypes.selectAll().where { ModuleTypes.id eq dto.id }.count() > 0
        if (exists) {
            ModuleTypes.update({ ModuleTypes.id eq dto.id }) {
                it[code] = dto.code
                it[label] = dto.label
                it[description] = dto.description
                it[iconName] = dto.iconName
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            ModuleTypes.insert {
                it[id] = dto.id
                it[code] = dto.code
                it[label] = dto.label
                it[description] = dto.description
                it[iconName] = dto.iconName
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertCriticalityLevel(dto: CriticalityLevelDto) {
        val exists = CriticalityLevels.selectAll().where { CriticalityLevels.id eq dto.id }.count() > 0
        if (exists) {
            CriticalityLevels.update({ CriticalityLevels.id eq dto.id }) {
                it[code] = dto.code
                it[label] = dto.label
                it[priority] = dto.priority
                it[colorHex] = dto.colorHex
                it[iconEmoji] = dto.iconEmoji
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            CriticalityLevels.insert {
                it[id] = dto.id
                it[code] = dto.code
                it[label] = dto.label
                it[priority] = dto.priority
                it[colorHex] = dto.colorHex
                it[iconEmoji] = dto.iconEmoji
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertCheckUpStatus(dto: CheckUpStatusDto) {
        val exists = CheckupStatuses.selectAll().where { CheckupStatuses.id eq dto.id }.count() > 0
        if (exists) {
            CheckupStatuses.update({ CheckupStatuses.id eq dto.id }) {
                it[code] = dto.code
                it[label] = dto.label
                it[colorHex] = dto.colorHex
                it[iconEmoji] = dto.iconEmoji
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[blocksDeletion] = dto.blocksDeletion
                it[marksCompletion] = dto.marksCompletion
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            CheckupStatuses.insert {
                it[id] = dto.id
                it[code] = dto.code
                it[label] = dto.label
                it[colorHex] = dto.colorHex
                it[iconEmoji] = dto.iconEmoji
                it[sortOrder] = dto.sortOrder
                it[isActive] = dto.isActive
                it[blocksDeletion] = dto.blocksDeletion
                it[marksCompletion] = dto.marksCompletion
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    private fun upsertCheckItemTemplate(dto: CheckItemTemplateDto) {
        val exists = CheckItemTemplates.selectAll().where { CheckItemTemplates.id eq dto.id }.count() > 0
        if (exists) {
            CheckItemTemplates.update({ CheckItemTemplates.id eq dto.id }) {
                it[moduleTypeId] = dto.moduleTypeId
                it[category] = dto.category
                it[description] = dto.description
                it[criticalityId] = dto.criticalityId
                it[orderIndex] = dto.orderIndex
                it[isActive] = dto.isActive
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        } else {
            CheckItemTemplates.insert {
                it[id] = dto.id
                it[moduleTypeId] = dto.moduleTypeId
                it[category] = dto.category
                it[description] = dto.description
                it[criticalityId] = dto.criticalityId
                it[orderIndex] = dto.orderIndex
                it[isActive] = dto.isActive
                it[createdAt] = dto.createdAt
                it[updatedAt] = dto.updatedAt
                it[syncedAt] = dto.syncedAt
                it[isDeleted] = dto.isDeleted
            }
        }
    }

    // ===== ROW MAPPERS =====

    private fun ResultRow.toClientDto() = ClientDto(
        id = this[Clients.id],
        companyName = this[Clients.companyName],
        notes = this[Clients.notes],
        headquartersJson = this[Clients.headquartersJson],
        isActive = this[Clients.isActive],
        createdAt = this[Clients.createdAt],
        updatedAt = this[Clients.updatedAt],
        syncedAt = this[Clients.syncedAt],
        isDeleted = this[Clients.isDeleted]
    )

    private fun ResultRow.toContactDto() = ContactDto(
        id = this[Contacts.id],
        clientId = this[Contacts.clientId],
        firstName = this[Contacts.firstName],
        lastName = this[Contacts.lastName],
        title = this[Contacts.title],
        role = this[Contacts.role],
        department = this[Contacts.department],
        phone = this[Contacts.phone],
        mobilePhone = this[Contacts.mobilePhone],
        email = this[Contacts.email],
        alternativeEmail = this[Contacts.alternativeEmail],
        isPrimary = this[Contacts.isPrimary],
        preferredContactMethod = this[Contacts.preferredContactMethod],
        notes = this[Contacts.notes],
        isActive = this[Contacts.isActive],
        createdAt = this[Contacts.createdAt],
        updatedAt = this[Contacts.updatedAt],
        syncedAt = this[Contacts.syncedAt],
        isDeleted = this[Contacts.isDeleted]
    )

    private fun ResultRow.toContractDto() = ContractDto(
        id = this[Contracts.id],
        clientId = this[Contracts.clientId],
        name = this[Contracts.name],
        description = this[Contracts.description],
        startDate = this[Contracts.startDate],
        endDate = this[Contracts.endDate],
        hasPriority = this[Contracts.hasPriority],
        hasRemoteAssistance = this[Contracts.hasRemoteAssistance],
        hasMaintenance = this[Contracts.hasMaintenance],
        notes = this[Contracts.notes],
        isActive = this[Contracts.isActive],
        createdAt = this[Contracts.createdAt],
        updatedAt = this[Contracts.updatedAt],
        syncedAt = this[Contracts.syncedAt],
        isDeleted = this[Contracts.isDeleted]
    )

    private fun ResultRow.toFacilityDto() = FacilityDto(
        id = this[Facilities.id],
        clientId = this[Facilities.clientId],
        name = this[Facilities.name],
        code = this[Facilities.code],
        notes = this[Facilities.notes],
        facilityType = this[Facilities.facilityType],
        addressJson = this[Facilities.addressJson],
        isPrimary = this[Facilities.isPrimary],
        isActive = this[Facilities.isActive],
        createdAt = this[Facilities.createdAt],
        updatedAt = this[Facilities.updatedAt],
        syncedAt = this[Facilities.syncedAt],
        isDeleted = this[Facilities.isDeleted]
    )

    private fun ResultRow.toIslandTypeDto() = IslandTypeDto(
        id = this[IslandTypes.id],
        code = this[IslandTypes.code],
        label = this[IslandTypes.label],
        description = this[IslandTypes.description],
        iconName = this[IslandTypes.iconName],
        maintenanceIntervalDays = this[IslandTypes.maintenanceIntervalDays],
        sortOrder = this[IslandTypes.sortOrder],
        isActive = this[IslandTypes.isActive],
        createdAt = this[IslandTypes.createdAt],
        updatedAt = this[IslandTypes.updatedAt],
        syncedAt = this[IslandTypes.syncedAt],
        isDeleted = this[IslandTypes.isDeleted]
    )

    private fun ResultRow.toFacilityIslandDto() = FacilityIslandDto(
        id = this[FacilityIslands.id],
        facilityId = this[FacilityIslands.facilityId],
        commissioningNumber = this[FacilityIslands.commissioningNumber],
        islandType = this[FacilityIslands.islandType],
        islandTypeId = this[FacilityIslands.islandTypeId],
        serialNumber = this[FacilityIslands.serialNumber],
        modelNumber = this[FacilityIslands.modelNumber],
        model = this[FacilityIslands.model],
        installationDate = this[FacilityIslands.installationDate],
        warrantyExpiration = this[FacilityIslands.warrantyExpiration],
        operatingHours = this[FacilityIslands.operatingHours],
        cycleCount = this[FacilityIslands.cycleCount],
        lastMaintenanceDate = this[FacilityIslands.lastMaintenanceDate],
        nextScheduledMaintenance = this[FacilityIslands.nextScheduledMaintenance],
        customName = this[FacilityIslands.customName],
        location = this[FacilityIslands.location],
        notes = this[FacilityIslands.notes],
        isActive = this[FacilityIslands.isActive],
        createdAt = this[FacilityIslands.createdAt],
        updatedAt = this[FacilityIslands.updatedAt],
        syncedAt = this[FacilityIslands.syncedAt],
        isDeleted = this[FacilityIslands.isDeleted]
    )

    private fun ResultRow.toMechanicalUnitDto() = MechanicalUnitDto(
        id = this[MechanicalUnits.id],
        islandId = this[MechanicalUnits.islandId],
        unitType = this[MechanicalUnits.unitType],
        name = this[MechanicalUnits.name],
        serialNumber = this[MechanicalUnits.serialNumber],
        model = this[MechanicalUnits.model],
        notes = this[MechanicalUnits.notes],
        isActive = this[MechanicalUnits.isActive],
        createdAt = this[MechanicalUnits.createdAt],
        updatedAt = this[MechanicalUnits.updatedAt],
        syncedAt = this[MechanicalUnits.syncedAt],
        isDeleted = this[MechanicalUnits.isDeleted]
    )

    private fun ResultRow.toMaintenanceLogDto() = MaintenanceLogDto(
        id = this[MaintenanceLogs.id],
        islandId = this[MaintenanceLogs.islandId],
        operationType = this[MaintenanceLogs.operationType],
        customOperationLabel = this[MaintenanceLogs.customOperationLabel],
        mechanicalUnitId = this[MaintenanceLogs.mechanicalUnitId],
        componentLabel = this[MaintenanceLogs.componentLabel],
        description = this[MaintenanceLogs.description],
        technicianName = this[MaintenanceLogs.technicianName],
        technicianCompany = this[MaintenanceLogs.technicianCompany],
        operatingHoursAtEvent = this[MaintenanceLogs.operatingHoursAtEvent],
        cycleCountAtEvent = this[MaintenanceLogs.cycleCountAtEvent],
        outcome = this[MaintenanceLogs.outcome],
        durationMinutes = this[MaintenanceLogs.durationMinutes],
        notes = this[MaintenanceLogs.notes],
        performedAt = this[MaintenanceLogs.performedAt],
        createdAt = this[MaintenanceLogs.createdAt],
        updatedAt = this[MaintenanceLogs.updatedAt],
        syncedAt = this[MaintenanceLogs.syncedAt],
        isActive = this[MaintenanceLogs.isActive],
        isDeleted = this[MaintenanceLogs.isDeleted]

    )

    private fun ResultRow.toDocumentDto() = DocumentDto(
        id         = this[IslandDocuments.id],
        scope      = this[IslandDocuments.scope],
        islandId   = this[IslandDocuments.islandId],
        facilityId = this[IslandDocuments.facilityId],
        clientId   = this[IslandDocuments.clientId],
        fileName   = this[IslandDocuments.fileName],
        fileSize   = this[IslandDocuments.fileSize],
        mimeType   = this[IslandDocuments.mimeType],
        fileHash   = this[IslandDocuments.fileHash],
        title      = this[IslandDocuments.title],
        category   = this[IslandDocuments.category],
        notes      = this[IslandDocuments.notes],
        createdAt  = this[IslandDocuments.createdAt],
        updatedAt  = this[IslandDocuments.updatedAt],
        isActive   = this[IslandDocuments.isActive],
        isDeleted  = this[IslandDocuments.isDeleted],
        syncedAt   = this[IslandDocuments.syncedAt]
    )

    private fun ResultRow.toModuleTypeDto() = ModuleTypeDto(
        id          = this[ModuleTypes.id],
        code        = this[ModuleTypes.code],
        label       = this[ModuleTypes.label],
        description = this[ModuleTypes.description],
        iconName    = this[ModuleTypes.iconName],
        sortOrder   = this[ModuleTypes.sortOrder],
        isActive    = this[ModuleTypes.isActive],
        createdAt   = this[ModuleTypes.createdAt],
        updatedAt   = this[ModuleTypes.updatedAt],
        syncedAt    = this[ModuleTypes.syncedAt],
        isDeleted   = this[ModuleTypes.isDeleted]
    )

    private fun ResultRow.toCriticalityLevelDto() = CriticalityLevelDto(
        id        = this[CriticalityLevels.id],
        code      = this[CriticalityLevels.code],
        label     = this[CriticalityLevels.label],
        priority  = this[CriticalityLevels.priority],
        colorHex  = this[CriticalityLevels.colorHex],
        iconEmoji = this[CriticalityLevels.iconEmoji],
        sortOrder = this[CriticalityLevels.sortOrder],
        isActive  = this[CriticalityLevels.isActive],
        createdAt = this[CriticalityLevels.createdAt],
        updatedAt = this[CriticalityLevels.updatedAt],
        syncedAt  = this[CriticalityLevels.syncedAt],
        isDeleted = this[CriticalityLevels.isDeleted]
    )

    private fun ResultRow.toCheckUpStatusDto() = CheckUpStatusDto(
        id               = this[CheckupStatuses.id],
        code             = this[CheckupStatuses.code],
        label            = this[CheckupStatuses.label],
        colorHex         = this[CheckupStatuses.colorHex],
        iconEmoji        = this[CheckupStatuses.iconEmoji],
        sortOrder        = this[CheckupStatuses.sortOrder],
        isActive         = this[CheckupStatuses.isActive],
        blocksDeletion   = this[CheckupStatuses.blocksDeletion],
        marksCompletion  = this[CheckupStatuses.marksCompletion],
        createdAt        = this[CheckupStatuses.createdAt],
        updatedAt        = this[CheckupStatuses.updatedAt],
        syncedAt         = this[CheckupStatuses.syncedAt],
        isDeleted        = this[CheckupStatuses.isDeleted]
    )

    private fun ResultRow.toCheckItemTemplateDto() = CheckItemTemplateDto(
        id            = this[CheckItemTemplates.id],
        moduleTypeId  = this[CheckItemTemplates.moduleTypeId],
        category      = this[CheckItemTemplates.category],
        description   = this[CheckItemTemplates.description],
        criticalityId = this[CheckItemTemplates.criticalityId],
        orderIndex    = this[CheckItemTemplates.orderIndex],
        isActive      = this[CheckItemTemplates.isActive],
        createdAt     = this[CheckItemTemplates.createdAt],
        updatedAt     = this[CheckItemTemplates.updatedAt],
        syncedAt      = this[CheckItemTemplates.syncedAt],
        isDeleted     = this[CheckItemTemplates.isDeleted]
    )

    private fun upsertCheckUpRecord(dto: CheckUpRecordDto) {
        val exists = Checkups.selectAll().where { Checkups.id eq dto.id }.count() > 0
        if (exists) {
            Checkups.update({ Checkups.id eq dto.id }) {
                it[clientCompanyName]          = dto.clientCompanyName
                it[clientContactPerson]        = dto.clientContactPerson
                it[clientSite]                 = dto.clientSite
                it[clientAddress]              = dto.clientAddress
                it[clientPhone]                = dto.clientPhone
                it[clientEmail]                = dto.clientEmail
                it[islandSerialNumber]         = dto.islandSerialNumber
                it[islandModel]                = dto.islandModel
                it[islandInstallationDate]     = dto.islandInstallationDate
                it[islandLastMaintenanceDate]  = dto.islandLastMaintenanceDate
                it[islandOperatingHours]       = dto.islandOperatingHours
                it[islandCycleCount]           = dto.islandCycleCount
                it[technicianName]             = dto.technicianName
                it[technicianCompany]          = dto.technicianCompany
                it[technicianCertification]    = dto.technicianCertification
                it[technicianPhone]            = dto.technicianPhone
                it[technicianEmail]            = dto.technicianEmail
                it[checkupDate]                = dto.checkupDate
                it[headerNotes]                = dto.headerNotes
                it[islandType]                 = dto.islandType
                it[islandTypeId]               = dto.islandTypeId
                it[status]                     = dto.status
                it[updatedAt]                  = dto.updatedAt
                it[completedAt]                = dto.completedAt
                it[syncedAt]                   = dto.syncedAt
                it[isDeleted]                  = dto.isDeleted
            }
        } else {
            Checkups.insert {
                it[id]                         = dto.id
                it[clientCompanyName]          = dto.clientCompanyName
                it[clientContactPerson]        = dto.clientContactPerson
                it[clientSite]                 = dto.clientSite
                it[clientAddress]              = dto.clientAddress
                it[clientPhone]                = dto.clientPhone
                it[clientEmail]                = dto.clientEmail
                it[islandSerialNumber]         = dto.islandSerialNumber
                it[islandModel]                = dto.islandModel
                it[islandInstallationDate]     = dto.islandInstallationDate
                it[islandLastMaintenanceDate]  = dto.islandLastMaintenanceDate
                it[islandOperatingHours]       = dto.islandOperatingHours
                it[islandCycleCount]           = dto.islandCycleCount
                it[technicianName]             = dto.technicianName
                it[technicianCompany]          = dto.technicianCompany
                it[technicianCertification]    = dto.technicianCertification
                it[technicianPhone]            = dto.technicianPhone
                it[technicianEmail]            = dto.technicianEmail
                it[checkupDate]                = dto.checkupDate
                it[headerNotes]                = dto.headerNotes
                it[islandType]                 = dto.islandType
                it[islandTypeId]               = dto.islandTypeId
                it[status]                     = dto.status
                it[createdAt]                  = dto.createdAt
                it[updatedAt]                  = dto.updatedAt
                it[completedAt]                = dto.completedAt
                it[syncedAt]                   = dto.syncedAt
                it[isDeleted]                  = dto.isDeleted
            }
        }
    }

    private fun upsertCheckUpIslandAssociation(dto: CheckUpIslandAssociationDto) {
        val exists = CheckupIslandAssociations.selectAll()
            .where { CheckupIslandAssociations.id eq dto.id }.count() > 0
        if (exists) {
            CheckupIslandAssociations.update({ CheckupIslandAssociations.id eq dto.id }) {
                it[associationType] = dto.associationType
                it[notes]           = dto.notes
                it[updatedAt]       = dto.updatedAt
                it[syncedAt]        = dto.syncedAt
            }
        } else {
            CheckupIslandAssociations.insert {
                it[id]              = dto.id
                it[checkupId]       = dto.checkupId
                it[islandId]        = dto.islandId
                it[associationType] = dto.associationType
                it[notes]           = dto.notes
                it[createdAt]       = dto.createdAt
                it[updatedAt]       = dto.updatedAt
                it[syncedAt]        = dto.syncedAt
            }
        }
    }

    private fun ResultRow.toCheckUpRecordDto() = CheckUpRecordDto(
        id                       = this[Checkups.id],
        clientCompanyName        = this[Checkups.clientCompanyName],
        clientContactPerson      = this[Checkups.clientContactPerson],
        clientSite               = this[Checkups.clientSite],
        clientAddress            = this[Checkups.clientAddress],
        clientPhone              = this[Checkups.clientPhone],
        clientEmail              = this[Checkups.clientEmail],
        islandSerialNumber       = this[Checkups.islandSerialNumber],
        islandModel              = this[Checkups.islandModel],
        islandInstallationDate   = this[Checkups.islandInstallationDate],
        islandLastMaintenanceDate = this[Checkups.islandLastMaintenanceDate],
        islandOperatingHours     = this[Checkups.islandOperatingHours],
        islandCycleCount         = this[Checkups.islandCycleCount],
        technicianName           = this[Checkups.technicianName],
        technicianCompany        = this[Checkups.technicianCompany],
        technicianCertification  = this[Checkups.technicianCertification],
        technicianPhone          = this[Checkups.technicianPhone],
        technicianEmail          = this[Checkups.technicianEmail],
        checkupDate              = this[Checkups.checkupDate],
        headerNotes              = this[Checkups.headerNotes],
        islandType               = this[Checkups.islandType],
        islandTypeId             = this[Checkups.islandTypeId],
        status                   = this[Checkups.status],
        createdAt                = this[Checkups.createdAt],
        updatedAt                = this[Checkups.updatedAt],
        completedAt              = this[Checkups.completedAt],
        syncedAt                 = this[Checkups.syncedAt],
        isDeleted                = this[Checkups.isDeleted]
    )

    private fun ResultRow.toCheckUpIslandAssociationDto() = CheckUpIslandAssociationDto(
        id              = this[CheckupIslandAssociations.id],
        checkupId       = this[CheckupIslandAssociations.checkupId],
        islandId        = this[CheckupIslandAssociations.islandId],
        associationType = this[CheckupIslandAssociations.associationType],
        notes           = this[CheckupIslandAssociations.notes],
        createdAt       = this[CheckupIslandAssociations.createdAt],
        updatedAt       = this[CheckupIslandAssociations.updatedAt],
        syncedAt        = this[CheckupIslandAssociations.syncedAt]
    )

    private fun ResultRow.toCheckItemDto() = CheckItemDto(
        id            = this[CheckItems.id],
        checkupId     = this[CheckItems.checkupId],
        moduleType    = this[CheckItems.moduleType],
        moduleTypeId  = this[CheckItems.moduleTypeId],
        itemCode      = this[CheckItems.itemCode],
        description   = this[CheckItems.description],
        status        = this[CheckItems.status],
        criticality   = this[CheckItems.criticality],
        criticalityId = this[CheckItems.criticalityId],
        notes         = this[CheckItems.notes],
        checkedAt     = this[CheckItems.checkedAt],
        orderIndex    = this[CheckItems.orderIndex]
    )

    private fun ResultRow.toPhotoDto() = PhotoDto(
        id          = this[Photos.id],
        checkItemId = this[Photos.checkItemId],
        fileName    = this[Photos.fileName],
        caption     = this[Photos.caption],
        takenAt     = this[Photos.takenAt],
        fileSize    = this[Photos.fileSize],
        orderIndex  = this[Photos.orderIndex],
        width       = this[Photos.width],
        height      = this[Photos.height]
    )
}