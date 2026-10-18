package com.fieldscanner.app.runes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RuneBindingsTest {
    private val bindings = RuneBindings()

    @Test
    fun `journal loading keeps every rune explicitly unassigned`() {
        RuneId.values().forEach { rune ->
            val binding = bindings.resolve(RuneContext.JOURNAL_LOADING, rune)
            assertEquals(RuneAction.Unassigned, binding.action)
            assertFalse(binding.enabled)
        }
    }

    @Test
    fun `camera inactive preserves the approved camera journal contacts display order`() {
        val slots = bindings.hintSlots(RuneContext.CAMERA_INACTIVE)

        assertEquals(3, slots.size)
        assertEquals(RuneId.EMBER, slots[0].binding?.rune)
        assertEquals(RuneAction.ActivateCamera, slots[0].binding?.action)
        assertEquals(RuneId.ASTRA, slots[1].binding?.rune)
        assertEquals(RuneAction.OpenJournal, slots[1].binding?.action)
        assertEquals(RuneId.BOREAL, slots[2].binding?.rune)
        assertEquals(RuneAction.OpenContacts, slots[2].binding?.action)
    }

    @Test
    fun `approved bindings resolve to the requested rune actions`() {
        assertEquals(RuneAction.TakePhoto, bindings.resolve(RuneContext.CAMERA, RuneId.EMBER).action)
        assertEquals(RuneAction.ReturnToInactiveCamera, bindings.resolve(RuneContext.CAMERA, RuneId.DRIFT).action)
        assertEquals(RuneAction.SavePhotoWithoutAnalysis, bindings.resolve(RuneContext.PHOTO_REVIEW, RuneId.EMBER).action)
        assertEquals(RuneAction.AnalyzePhoto, bindings.resolve(RuneContext.PHOTO_REVIEW, RuneId.ASTRA).action)
        assertEquals(RuneAction.DiscardPhotoAndReturnToInactiveCamera, bindings.resolve(RuneContext.PHOTO_REVIEW, RuneId.DRIFT).action)
        assertEquals(RuneAction.CancelAnalysis, bindings.resolve(RuneContext.ANALYZING, RuneId.FLUX).action)
        assertEquals(RuneAction.MoveJournalPrevious, bindings.resolve(RuneContext.JOURNAL, RuneId.CIPHER).action)
        assertEquals(RuneAction.MoveJournalNext, bindings.resolve(RuneContext.JOURNAL, RuneId.BOREAL).action)
        assertEquals(RuneAction.ReturnToInactiveCamera, bindings.resolve(RuneContext.JOURNAL, RuneId.DRIFT).action)
        assertEquals(RuneAction.PlayJournalAudio, bindings.resolve(RuneContext.JOURNAL, RuneId.ASTRA).action)
        assertEquals(RuneAction.StartJournalVoiceSearch, bindings.resolve(RuneContext.JOURNAL, RuneId.EMBER).action)
    }

    @Test
    fun `journal exposes all five active bindings for paging`() {
        assertEquals(
            listOf(RuneId.CIPHER, RuneId.BOREAL, RuneId.DRIFT, RuneId.EMBER, RuneId.ASTRA, RuneId.FLUX),
            bindings.availableHintBindings(RuneContext.JOURNAL).map { it.rune }
        )
    }

    @Test
    fun `journal deletion confirmation exposes only visually distinct confirm and cancel runes`() {
        assertEquals(
            listOf(RuneId.EMBER, RuneId.DRIFT),
            bindings.availableHintBindings(RuneContext.JOURNAL_DELETE_CONFIRMATION).map { it.rune }
        )
        assertEquals(
            RuneAction.ConfirmFindingDeletion,
            bindings.resolve(RuneContext.JOURNAL_DELETE_CONFIRMATION, RuneId.EMBER).action
        )
        assertEquals(
            RuneAction.CancelFindingDeletion,
            bindings.resolve(RuneContext.JOURNAL_DELETE_CONFIRMATION, RuneId.DRIFT).action
        )
        assertEquals(
            RuneAction.Unassigned,
            bindings.resolve(RuneContext.JOURNAL_DELETE_CONFIRMATION, RuneId.FLUX).action
        )
    }

    @Test
    fun `analysis result deletion confirmation reuses the same confirm and cancel runes as the journal`() {
        assertEquals(
            listOf(RuneId.EMBER, RuneId.DRIFT),
            bindings.availableHintBindings(RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION).map { it.rune }
        )
        assertEquals(
            RuneAction.ConfirmFindingDeletion,
            bindings.resolve(RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION, RuneId.EMBER).action
        )
        assertEquals(
            RuneAction.CancelFindingDeletion,
            bindings.resolve(RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION, RuneId.DRIFT).action
        )
        assertEquals(
            RuneAction.Unassigned,
            bindings.resolve(RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION, RuneId.FLUX).action
        )
    }

    @Test
    fun `long-pressing ASTRA in the journal starts the language switch`() {
        assertEquals(
            RuneAction.StartLanguageChange,
            bindings.resolveLongPress(RuneContext.JOURNAL, RuneId.ASTRA).action
        )
        // A short press of the same rune is unchanged.
        assertEquals(
            RuneAction.PlayJournalAudio,
            bindings.resolve(RuneContext.JOURNAL, RuneId.ASTRA).action
        )
    }

    @Test
    fun `every other context and rune has no long-press meaning`() {
        RuneContext.values().forEach { context ->
            RuneId.values().forEach { rune ->
                val isLanguageSwitch = context == RuneContext.JOURNAL && rune == RuneId.ASTRA
                val action = bindings.resolveLongPress(context, rune).action
                if (isLanguageSwitch) {
                    assertEquals(RuneAction.StartLanguageChange, action)
                } else {
                    assertEquals("$context/$rune", RuneAction.Unassigned, action)
                }
            }
        }
    }

    @Test
    fun `language change mode exposes confirm cycle and cancel on ember astra drift`() {
        assertEquals(
            listOf(RuneId.EMBER, RuneId.ASTRA, RuneId.DRIFT),
            bindings.availableHintBindings(RuneContext.LANGUAGE_CHANGE).map { it.rune }
        )
        assertEquals(RuneAction.ConfirmLanguageChange, bindings.resolve(RuneContext.LANGUAGE_CHANGE, RuneId.EMBER).action)
        assertEquals(RuneAction.CycleLanguageChange, bindings.resolve(RuneContext.LANGUAGE_CHANGE, RuneId.ASTRA).action)
        assertEquals(RuneAction.CancelLanguageChange, bindings.resolve(RuneContext.LANGUAGE_CHANGE, RuneId.DRIFT).action)
        assertEquals(RuneAction.Unassigned, bindings.resolve(RuneContext.LANGUAGE_CHANGE, RuneId.FLUX).action)
    }

    @Test
    fun `contacts expose call next and return in approved order`() {
        assertEquals(
            listOf(RuneId.EMBER, RuneId.BOREAL, RuneId.DRIFT),
            bindings.availableHintBindings(RuneContext.CONTACTS).map { it.rune }
        )
    }
}
