package com.fieldscanner.app.calls

/** Centralized, fail-closed number validation for incoming and outgoing calls. */
object WhitelistPolicy {
    private val E164_NUMBER = Regex("^\\+[1-9]\\d{6,14}$")
    private val allowedNumbers = AllowedContacts.all.mapTo(linkedSetOf()) { normalize(it.phoneNumber) }

    fun normalize(phoneNumber: String?): String? {
        val normalized = phoneNumber.orEmpty().filter { it == '+' || it.isDigit() }
        return normalized.takeIf { it.matches(E164_NUMBER) }
    }

    fun isAllowed(phoneNumber: String?): Boolean = normalize(phoneNumber) in allowedNumbers

    fun contactFor(phoneNumber: String?): AllowedContact? {
        val normalized = normalize(phoneNumber) ?: return null
        return AllowedContacts.all.firstOrNull { normalize(it.phoneNumber) == normalized }
    }
}
