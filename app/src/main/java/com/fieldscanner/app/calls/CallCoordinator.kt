package com.fieldscanner.app.calls

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FieldCallStatus { INCOMING, DIALING, ACTIVE, HOLDING }

data class FieldCallUiState(
    val status: FieldCallStatus,
    val contact: AllowedContact?,
    val phoneNumber: String,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val bluetoothAvailable: Boolean = false
)

/** Process-local bridge between [FieldScannerInCallService] and the scanner UI. */
object CallCoordinator {
    private val _state = MutableStateFlow<FieldCallUiState?>(null)
    val state = _state.asStateFlow()

    private var service: FieldScannerInCallService? = null
    private var call: Call? = null

    fun attach(service: FieldScannerInCallService, call: Call, state: FieldCallUiState) {
        this.service = service
        this.call = call
        _state.value = state
    }

    fun update(state: FieldCallUiState) {
        _state.value = state
    }

    fun clear(call: Call) {
        if (this.call == call) {
            this.call = null
            service = null
            _state.value = null
        }
    }

    fun answer() = call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject() = call?.reject(false, null)
    fun disconnect() = call?.disconnect()
    fun toggleMute() = service?.setMuted(!(_state.value?.isMuted ?: false))

    fun toggleSpeaker() {
        val route = if (_state.value?.isSpeakerOn == true) {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service?.setAudioRoute(route)
    }

    fun selectBluetooth() = service?.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
}
