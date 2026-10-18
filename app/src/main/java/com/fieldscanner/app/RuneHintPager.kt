package com.fieldscanner.app

import android.os.SystemClock
import com.fieldscanner.app.runes.HintSlotId
import com.fieldscanner.app.runes.RuneBinding
import com.fieldscanner.app.runes.RuneHintSlot

/** Keeps the transient pagination state for the three visible rune hints. */
internal class RuneHintPager {
    private var bindings: List<RuneBinding> = emptyList()
    private var page = 0
    private var pageStartedAtMillis = SystemClock.uptimeMillis()
    private var visibleSlots: List<RuneHintSlot> = emptySlots()

    fun setBindings(newBindings: List<RuneBinding>) {
        bindings = newBindings
        reset()
    }

    fun reset() {
        page = 0
        pageStartedAtMillis = SystemClock.uptimeMillis()
        visibleSlots = emptySlots()
    }

    fun advancePage(): Boolean {
        val pageCount = pageCount()
        if (pageCount <= 1) return false
        page = (page + 1) % pageCount
        pageStartedAtMillis = SystemClock.uptimeMillis()
        visibleSlots = emptySlots()
        return true
    }

    fun slots(): List<RuneHintSlot> = visibleSlots

    private fun emptySlots(): List<RuneHintSlot> {
        val startIndex = page * HintSlotId.values().size
        return HintSlotId.values().mapIndexed { index, id ->
            RuneHintSlot(id, bindings.getOrNull(startIndex + index))
        }
    }

    fun pageCount(): Int =
        (bindings.size + HintSlotId.values().size - 1) / HintSlotId.values().size

    fun opacity(now: Long): Float {
        val elapsed = (now - pageStartedAtMillis).coerceAtLeast(0L)
        if (elapsed < APPEAR_DURATION_MILLIS) return elapsed.toFloat() / APPEAR_DURATION_MILLIS
        if (pageCount() <= 1) return 1f
        val fadeStart = PAGE_DURATION_MILLIS - FADE_DURATION_MILLIS
        val fadeElapsed = (elapsed - fadeStart).coerceAtLeast(0L)
        return 1f - (fadeElapsed.toFloat() / FADE_DURATION_MILLIS).coerceIn(0f, 1f)
    }

    companion object {
        const val PAGE_DURATION_MILLIS = 5_000L
        private const val APPEAR_DURATION_MILLIS = 1_000L
        private const val FADE_DURATION_MILLIS = 2_000L
    }
}
