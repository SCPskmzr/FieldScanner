package com.fieldscanner.app.calls

/** A contact that is allowed to use the device's cellular calling capability. */
data class AllowedContact(
    val name: String,
    val phoneNumber: String
)

object AllowedContacts {
    val dad = AllowedContact(name = "Папа", phoneNumber = "+48883264766")
    val mom = AllowedContact(name = "Мама", phoneNumber = "+48883532683")

    val all: List<AllowedContact> = listOf(dad, mom)
}
