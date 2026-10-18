package com.fieldscanner.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageChangeControllerTest {
    private val voice = FakeVoice()
    private val timeout = FakeTimeout()
    private var finishedCount = 0

    private fun controller(current: AppLanguage = AppLanguage.RUSSIAN): Pair<LanguageChangeController, FakePreference> {
        val preference = FakePreference(current)
        val controller = LanguageChangeController(preference, voice, timeout)
            .also { it.onFinished = { finishedCount++ } }
        return controller to preference
    }

    @Test
    fun `entering the mode speaks the current language and sits on it`() {
        val (controller, _) = controller(current = AppLanguage.ENGLISH)

        controller.start()

        assertTrue(controller.isActive())
        assertEquals(AppLanguage.ENGLISH.endonym to AppLanguage.ENGLISH, voice.spoken.single())
    }

    @Test
    fun `the cycle key walks languages aloud and wraps around`() {
        val (controller, _) = controller() // starts on Russian (index 0)

        controller.start()
        controller.onCycleKey()
        assertEquals(AppLanguage.POLISH.endonym, voice.spoken.last().first)

        repeat(5) { controller.onCycleKey() } // past HEBREW, back to RUSSIAN
        assertEquals(AppLanguage.RUSSIAN.endonym, voice.spoken.last().first)
    }

    @Test
    fun `the confirm key persists the cycled-to language and speaks it once`() {
        val (controller, preference) = controller()

        controller.start()
        controller.onCycleKey() // -> Polski
        controller.onConfirmKey()

        assertSame(AppLanguage.POLISH, preference.current())
        assertEquals(AppLanguage.POLISH.endonym to AppLanguage.POLISH, voice.spoken.last())
        assertFalse(controller.isActive())
        assertEquals(1, finishedCount)
        assertEquals(0, voice.errorCount)
    }

    @Test
    fun `confirming immediately keeps the current language`() {
        val (controller, preference) = controller()

        controller.start()
        controller.onConfirmKey()

        assertSame(AppLanguage.RUSSIAN, preference.current())
        assertTrue(preference.history.isEmpty())
        assertFalse(controller.isActive())
    }

    @Test
    fun `the cancel key leaves the mode with no change`() {
        val (controller, preference) = controller()

        controller.start()
        controller.onCycleKey()
        controller.onCancelKey()

        assertSame(AppLanguage.RUSSIAN, preference.current())
        assertTrue(preference.history.isEmpty())
        assertFalse(controller.isActive())
        assertEquals(0, voice.errorCount)
    }

    @Test
    fun `the safety timeout ends a stuck mode`() {
        val (controller, preference) = controller()

        controller.start()
        timeout.fire()

        assertFalse(controller.isActive())
        assertEquals(1, voice.errorCount)
        assertSame(AppLanguage.RUSSIAN, preference.current())
    }

    @Test
    fun `keys do nothing once the mode has ended`() {
        val (controller, preference) = controller()

        controller.start()
        controller.onConfirmKey() // ends on current
        val spokenAfterConfirm = voice.spoken.size

        controller.onCycleKey()
        controller.onConfirmKey()

        assertEquals(spokenAfterConfirm, voice.spoken.size)
        assertSame(AppLanguage.RUSSIAN, preference.current())
    }

    private class FakePreference(initial: AppLanguage) : LanguagePreference {
        private var value: AppLanguage = initial
        val history = mutableListOf<AppLanguage>()
        override fun current() = value
        override fun update(language: AppLanguage) { value = language; history += language }
    }

    private class FakeVoice : LanguageVoice {
        val spoken = mutableListOf<Pair<String, AppLanguage>>()
        var errorCount = 0
        var released = false
        override fun speak(text: String, language: AppLanguage, onDone: () -> Unit) {
            spoken += text to language
            onDone()
        }
        override fun error() { errorCount++ }
        override fun release() { released = true }
    }

    private class FakeTimeout : LanguageChangeController.FlowTimeout {
        private var onFire: (() -> Unit)? = null
        override fun arm(onFire: () -> Unit) { this.onFire = onFire }
        override fun cancel() { onFire = null }
        fun fire() { onFire?.invoke() }
    }
}
