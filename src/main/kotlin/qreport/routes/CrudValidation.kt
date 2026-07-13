package net.calvuz.qreport.routes

import net.calvuz.qreport.shared.validation.ClientValidationRules
import net.calvuz.qreport.shared.validation.ContactValidationRules
import net.calvuz.qreport.shared.validation.FacilityValidationRules
import net.calvuz.qreport.shared.validation.IslandValidationRules

/**
 * Validation gate for the /api CRUD routes, mirroring the Android client's
 * domain validators (net.calvuz.qreport.client.*.domain.validator on the
 * Android side) via the shared rules in :shared. Each function returns null
 * when the data is valid, or a message suitable for a 400 response body.
 */

fun validateClientData(data: Map<String, Any?>): String? {
    val companyName = data["company_name"] as? String
    if (companyName.isNullOrBlank()) return "company_name is required"
    if (!ClientValidationRules.isCompanyNameLengthValid(companyName))
        return "company_name must be between ${ClientValidationRules.MIN_COMPANY_NAME_LENGTH} and ${ClientValidationRules.MAX_COMPANY_NAME_LENGTH} characters"
    return null
}

fun validateContactData(data: Map<String, Any?>): String? {
    val clientId = data["client_id"] as? String
    if (clientId.isNullOrBlank()) return "client_id is required"

    val firstName = data["first_name"] as? String
    if (firstName.isNullOrBlank()) return "first_name is required"
    if (!ContactValidationRules.isFirstNameLengthValid(firstName))
        return "first_name must be between ${ContactValidationRules.MIN_FIRST_NAME_LENGTH} and ${ContactValidationRules.MAX_FIRST_NAME_LENGTH} characters"

    val lastName = data["last_name"] as? String
    if ((lastName?.length ?: 0) > ContactValidationRules.MAX_LAST_NAME_LENGTH)
        return "last_name must be at most ${ContactValidationRules.MAX_LAST_NAME_LENGTH} characters"

    val email = data["email"] as? String
    if (!email.isNullOrBlank() && !ContactValidationRules.isEmailValid(email))
        return "email is not a valid address"

    val phone = data["phone"] as? String
    if (!phone.isNullOrBlank() && !ContactValidationRules.isPhoneValid(phone))
        return "phone is not a valid phone number"

    val mobilePhone = data["mobile_phone"] as? String
    if (!mobilePhone.isNullOrBlank() && !ContactValidationRules.isPhoneValid(mobilePhone))
        return "mobile_phone is not a valid phone number"

    val title = data["title"] as? String
    if ((title?.length ?: 0) > ContactValidationRules.MAX_TITLE_LENGTH)
        return "title must be at most ${ContactValidationRules.MAX_TITLE_LENGTH} characters"

    val role = data["role"] as? String
    if ((role?.length ?: 0) > ContactValidationRules.MAX_ROLE_LENGTH)
        return "role must be at most ${ContactValidationRules.MAX_ROLE_LENGTH} characters"

    val department = data["department"] as? String
    if ((department?.length ?: 0) > ContactValidationRules.MAX_DEPARTMENT_LENGTH)
        return "department must be at most ${ContactValidationRules.MAX_DEPARTMENT_LENGTH} characters"

    if (!ContactValidationRules.hasAnyContactInfo(email, phone, mobilePhone))
        return "at least one of email, phone or mobile_phone is required"

    return null
}

fun validateFacilityData(data: Map<String, Any?>): String? {
    val clientId = data["client_id"] as? String
    if (clientId.isNullOrBlank()) return "client_id is required"

    val name = data["name"] as? String
    if (name.isNullOrBlank()) return "name is required"
    if (!FacilityValidationRules.isNameLengthValid(name))
        return "name must be between ${FacilityValidationRules.MIN_NAME_LENGTH} and ${FacilityValidationRules.MAX_NAME_LENGTH} characters"

    return null
}

fun validateIslandData(data: Map<String, Any?>): String? {
    val facilityId = data["facility_id"] as? String
    if (facilityId.isNullOrBlank()) return "facility_id is required"

    val serialNumber = data["serial_number"] as? String
    if (serialNumber.isNullOrBlank()) return "serial_number is required"
    if (!IslandValidationRules.isSerialNumberLengthValid(serialNumber))
        return "serial_number must be between ${IslandValidationRules.MIN_SERIAL_NUMBER_LENGTH} and ${IslandValidationRules.MAX_SERIAL_NUMBER_LENGTH} characters"
    if (!IslandValidationRules.isValidCode(serialNumber))
        return "serial_number contains invalid characters"

    val commissioningNumber = data["commissioning_number"] as? String
    if (!commissioningNumber.isNullOrBlank() && !IslandValidationRules.isValidCode(commissioningNumber))
        return "commissioning_number contains invalid characters"

    val customName = data["custom_name"] as? String
    if ((customName?.length ?: 0) > IslandValidationRules.MAX_CUSTOM_NAME_LENGTH)
        return "custom_name must be at most ${IslandValidationRules.MAX_CUSTOM_NAME_LENGTH} characters"

    val location = data["location"] as? String
    if ((location?.length ?: 0) > IslandValidationRules.MAX_LOCATION_LENGTH)
        return "location must be at most ${IslandValidationRules.MAX_LOCATION_LENGTH} characters"

    val operatingHours = (data["operating_hours"] as? Number)?.toInt()
    if (operatingHours != null && !IslandValidationRules.isOperatingHoursValid(operatingHours))
        return "operating_hours must be >= 0"

    val cycleCount = (data["cycle_count"] as? Number)?.toLong()
    if (cycleCount != null && !IslandValidationRules.isCycleCountValid(cycleCount))
        return "cycle_count must be >= 0"

    val nowMs = System.currentTimeMillis()
    val installationMs = (data["installation_date"] as? Number)?.toLong()
    val warrantyMs = (data["warranty_expiration"] as? Number)?.toLong()
    val lastMaintenanceMs = (data["last_maintenance_date"] as? Number)?.toLong()
    val nextMaintenanceMs = (data["next_scheduled_maintenance"] as? Number)?.toLong()

    if (!IslandValidationRules.isInstallationDateValid(installationMs, nowMs))
        return "installation_date cannot be in the future"
    if (!IslandValidationRules.isWarrantyDateValid(warrantyMs, installationMs))
        return "warranty_expiration cannot be before installation_date"
    if (!IslandValidationRules.isMaintenanceDateValid(lastMaintenanceMs, installationMs, nowMs))
        return "last_maintenance_date is inconsistent with installation_date or is in the future"
    if (!IslandValidationRules.isNextMaintenanceValid(nextMaintenanceMs, lastMaintenanceMs))
        return "next_scheduled_maintenance must be after last_maintenance_date"

    return null
}
