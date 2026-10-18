package com.fieldscanner.app

data class DisplayState(
    val showCameraInCenter: Boolean = false,
    val bottomStatusMode: BottomStatusMode = BottomStatusMode.IDLE,
    val bottomSignalFrozenAtMillis: Long? = null,
    val contactSelection: ContactSelection? = null,
    val journalTitle: String? = null,
    val journalCoordinates: String? = null,
    val callTitle: String? = null,
    val callContactName: String? = null,
    val callPhoneNumber: String? = null,
    val showOutdoorTemperature: Boolean = false
)

/** Visual-only state for the child-facing signal strip in the lower display zone. */
enum class BottomStatusMode {
    IDLE,
    CAMERA,
    CAPTURE_FLASH,
    PHOTO_REVIEW,
    ANALYZING,
    ANALYSIS_RESULT,
    DELETE_CONFIRMATION,
    HIDDEN
}

enum class ContactSelection {
    MOM,
    DAD
}
