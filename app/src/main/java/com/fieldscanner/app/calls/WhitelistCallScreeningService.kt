package com.fieldscanner.app.calls

import android.telecom.Call
import android.telecom.CallScreeningService

/** Rejects every incoming caller that is not explicitly present in the whitelist. */
class WhitelistCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val allowed = WhitelistPolicy.isAllowed(number)
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(!allowed)
                .setRejectCall(!allowed)
                .setSkipCallLog(!allowed)
                .setSkipNotification(!allowed)
                .build()
        )
    }
}
