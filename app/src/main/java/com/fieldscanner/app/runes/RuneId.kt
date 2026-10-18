package com.fieldscanner.app.runes

/**
 * Stable identity of a physical rune button.
 *
 * A RuneId deliberately does not describe what the button does. Its meaning is
 * resolved from the active [RuneContext].
 */
enum class RuneId {
    ASTRA,
    BOREAL,
    CIPHER,
    DRIFT,
    EMBER,
    FLUX
}
