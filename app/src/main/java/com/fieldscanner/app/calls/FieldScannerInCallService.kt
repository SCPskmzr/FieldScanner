package com.fieldscanner.app.calls

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.fieldscanner.app.MainActivity

/** Supplies FieldScanner's own in-call UI after the user makes it the default dialer. */
class FieldScannerInCallService : InCallService() {
    private val callbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        // Call screening normally rejects this before it reaches the in-call UI. Keep
        // the in-call layer fail-closed as a fallback for devices that skip screening.
        if (call.state == Call.STATE_RINGING && !WhitelistPolicy.isAllowed(call.details.handle?.schemeSpecificPart)) {
            call.reject(false, null)
            return
        }
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_ACTIVE) setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                publish(call)
            }
            override fun onDetailsChanged(call: Call, details: Call.Details) = publish(call)
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        publish(call)
        showCallUi()
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let(call::unregisterCallback)
        CallCoordinator.clear(call)
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        val current = CallCoordinator.state.value ?: return
        CallCoordinator.update(
            current.copy(
                isMuted = audioState?.isMuted == true,
                isSpeakerOn = audioState?.route == CallAudioState.ROUTE_SPEAKER,
                bluetoothAvailable = audioState?.supportedRouteMask?.and(CallAudioState.ROUTE_BLUETOOTH) != 0
            )
        )
    }

    private fun publish(call: Call) {
        val status = when (call.state) {
            Call.STATE_RINGING -> FieldCallStatus.INCOMING
            Call.STATE_ACTIVE -> FieldCallStatus.ACTIVE
            Call.STATE_HOLDING -> FieldCallStatus.HOLDING
            else -> FieldCallStatus.DIALING
        }
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val audio = callAudioState
        CallCoordinator.attach(
            service = this,
            call = call,
            state = FieldCallUiState(
                status = status,
                contact = WhitelistPolicy.contactFor(number),
                phoneNumber = number,
                isMuted = audio?.isMuted == true,
                isSpeakerOn = audio?.route == CallAudioState.ROUTE_SPEAKER,
                bluetoothAvailable = audio?.supportedRouteMask?.and(CallAudioState.ROUTE_BLUETOOTH) != 0
            )
        )
    }

    private fun showCallUi() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_SHOW_CALL_UI, true)
        })
    }
}
