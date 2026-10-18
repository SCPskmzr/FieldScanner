package com.fieldscanner.app

import com.fieldscanner.app.calls.CallCoordinator
import com.fieldscanner.app.calls.CallUiController
import com.fieldscanner.app.runes.RuneAction
import com.fieldscanner.app.runes.RuneBinding
import com.fieldscanner.app.runes.RuneContext

/** Routes resolved rune actions without coupling the activity to feature flows. */
class RuneActionRouter(private val screen: MainScreenView, private val scanner: ScannerScreenCoordinator, private val calls: CallUiController) {
    fun execute(binding: RuneBinding) {
        screen.displayZones.notifyRuneInteraction(); if (scanner.execute(binding.action)) return
        when (binding.action) {
            RuneAction.OpenContacts -> if (scanner.activeRuneContext() == RuneContext.CAMERA_INACTIVE) { screen.displayZones.setContactPicker(calls.openPicker()); scanner.refreshRuneHints() }
            RuneAction.SelectNextContact -> screen.displayZones.setContactPicker(calls.selectNextContact())
            RuneAction.CallSelectedContact -> calls.requestCall()
            RuneAction.CloseContacts -> { calls.closePicker(); screen.displayZones.setContactPicker(null); scanner.refreshRuneHints() }
            RuneAction.AnswerCall -> CallCoordinator.answer()
            RuneAction.RejectCall -> CallCoordinator.reject()
            RuneAction.EndCall -> CallCoordinator.disconnect()
            else -> Unit
        }
    }
}
