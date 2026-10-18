package com.fieldscanner.app.calls

import com.fieldscanner.app.DisplayZonesView
import com.fieldscanner.app.camera.CameraController
import com.fieldscanner.app.runes.RuneAction
import com.fieldscanner.app.runes.RuneBinding
import com.fieldscanner.app.runes.RuneId
import com.fieldscanner.app.voice.VoiceSearchController

/** Renders call state and keeps Telecom-specific UI out of the activity. */
class CallScreenPresenter(
    private val displayZones: DisplayZonesView,
    private val camera: CameraController,
    private val voiceSearch: VoiceSearchController,
    private val contactSelection: (AllowedContact?) -> com.fieldscanner.app.ContactSelection?,
    private val closeContacts: () -> Unit,
    private val refreshRuneHints: () -> Unit
) {
    fun render(state: FieldCallUiState?) {
        if (state == null) {
            closeContacts()
            displayZones.setContactPicker(null)
            displayZones.setCallDetails(null, null, null)
            refreshRuneHints()
            return
        }
        closeContacts()
        val selection = contactSelection(state.contact)
        displayZones.setContactPicker(selection)
        camera.stop()
        voiceSearch.stop()
        displayZones.hideCentralCamera()
        if (selection == null) {
            displayZones.setCallDetails(
                title = when (state.status) {
                    FieldCallStatus.INCOMING -> "ВХОДЯЩИЙ ЗВОНОК"
                    FieldCallStatus.DIALING -> "СОЕДИНЕНИЕ"
                    FieldCallStatus.ACTIVE -> "В РАЗГОВОРЕ"
                    FieldCallStatus.HOLDING -> "УДЕРЖАНИЕ"
                },
                contactName = state.contact?.name ?: state.phoneNumber,
                phoneNumber = if (state.status == FieldCallStatus.INCOMING) state.phoneNumber else null
            )
        } else displayZones.setCallDetails(null, null, null)
        displayZones.setAvailableHintBindings(
            if (state.status == FieldCallStatus.INCOMING) incomingBindings else activeBindings
        )
    }

    fun handleRune(rune: RuneId): Boolean {
        when (CallCoordinator.state.value?.status ?: return false) {
            FieldCallStatus.INCOMING -> when (rune) {
                RuneId.EMBER -> CallCoordinator.answer()
                RuneId.FLUX -> CallCoordinator.reject()
                else -> Unit
            }
            FieldCallStatus.DIALING, FieldCallStatus.ACTIVE, FieldCallStatus.HOLDING ->
                if (rune == RuneId.FLUX) CallCoordinator.disconnect()
        }
        return true
    }

    private companion object {
        val incomingBindings = listOf(RuneBinding(RuneId.EMBER, RuneAction.AnswerCall), RuneBinding(RuneId.FLUX, RuneAction.RejectCall))
        val activeBindings = listOf(RuneBinding(RuneId.FLUX, RuneAction.EndCall))
    }
}
