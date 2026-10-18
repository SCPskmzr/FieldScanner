package com.fieldscanner.app.calls

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.fieldscanner.app.ContactSelection
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog

/** Owns contact selection, dialer-role and CALL_PHONE permission transitions. */
class CallUiController(
    private val activity: Activity,
    private val requestDefaultDialer: (android.content.Intent) -> Unit,
    private val requestCallPermission: () -> Unit,
    private val onError: (String) -> Unit,
    private val diagnostics: DiagnosticsLog = DiagnosticsLog.NONE
) {
    var selectedContact: AllowedContact = AllowedContacts.mom
        private set
    var isPickerVisible = false
        private set
    private var pendingContact: AllowedContact? = null

    fun selectedContactSelection(): ContactSelection = requireNotNull(selectionFor(selectedContact))
    fun openPicker(): ContactSelection {
        selectedContact = AllowedContacts.mom
        isPickerVisible = true
        return selectedContactSelection()
    }
    fun closePicker() { isPickerVisible = false }
    fun selectNextContact(): ContactSelection {
        selectedContact = if (selectedContact == AllowedContacts.mom) AllowedContacts.dad else AllowedContacts.mom
        return selectedContactSelection()
    }

    fun requestCall(contact: AllowedContact = selectedContact) {
        if (!WhitelistPolicy.isAllowed(contact.phoneNumber)) { fail("Номер не входит в whitelist"); return }
        pendingContact = contact
        if (PhoneRoleManager.isDefaultDialer(activity)) ensurePermission(contact)
        else requestDefaultDialer(PhoneRoleManager.requestDefaultDialerIntent(activity))
    }

    fun onDefaultDialerResult() {
        val contact = pendingContact ?: return
        if (PhoneRoleManager.isDefaultDialer(activity)) ensurePermission(contact)
        else fail("FieldScanner должен быть приложением телефона по умолчанию")
    }

    fun onCallPermissionResult(granted: Boolean) {
        val contact = pendingContact ?: return
        if (granted) placeCall(contact) else fail("Разрешение на звонки не выдано")
    }

    fun selectionFor(contact: AllowedContact?): ContactSelection? = when (contact) {
        AllowedContacts.mom -> ContactSelection.MOM
        AllowedContacts.dad -> ContactSelection.DAD
        else -> null
    }

    private fun ensurePermission(contact: AllowedContact) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) placeCall(contact)
        else requestCallPermission()
    }

    private fun placeCall(contact: AllowedContact) {
        if (!WhitelistPolicy.isAllowed(contact.phoneNumber)) { fail("Номер не входит в whitelist"); return }
        try {
            activity.getSystemService(TelecomManager::class.java).placeCall(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, contact.phoneNumber, null), Bundle()
            )
            pendingContact = null
        } catch (error: SecurityException) { diagnostics.error("CallUiController", "Call permission denied by telecom", error); fail("Не удалось получить доступ к телефонной связи")
        } catch (error: IllegalStateException) { diagnostics.error("CallUiController", "Telecom could not place call", error); fail("Сейчас нельзя совершить звонок") }
    }

    private fun fail(message: String) { pendingContact = null; onError(message) }
}
