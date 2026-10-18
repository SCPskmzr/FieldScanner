package com.fieldscanner.app.runes

/**
 * Central source of rune meanings. It is intentionally complete even before
 * product decisions are made: every context/rune pair is visible as UNASSIGNED.
 *
 * Replace an entry here when a meaning is approved; do not encode it in a View
 * or an input listener.
 */
class RuneBindings {
    private val bindings: Map<RuneContext, Map<RuneId, RuneBinding>> =
        RuneContext.values().associateWith { context ->
            RuneId.values().associateWith { rune ->
                bindingFor(context, rune)
            }
        }

    /**
     * Long-press meanings. Only the journal `ASTRA` rune carries one so far
     * (start the voice language switch); a short press of the same rune keeps
     * playing the finding audio. Kept here, never in a View or input listener.
     */
    private val longPressBindings: Map<RuneContext, Map<RuneId, RuneBinding>> =
        RuneContext.values().associateWith { context ->
            RuneId.values().associateWith { rune ->
                longPressBindingFor(context, rune)
            }
        }

    /** Product-approved display order; it is independent from the physical rune enum order. */
    private val hintOrder: Map<RuneContext, List<RuneId>> = mapOf(
        RuneContext.CAMERA_INACTIVE to listOf(RuneId.EMBER, RuneId.ASTRA, RuneId.BOREAL),
        RuneContext.CAMERA to listOf(RuneId.EMBER, RuneId.DRIFT),
        RuneContext.PHOTO_REVIEW to listOf(RuneId.EMBER, RuneId.ASTRA, RuneId.DRIFT),
        RuneContext.ANALYZING to listOf(RuneId.FLUX),
        RuneContext.ANALYSIS_RESULT to listOf(RuneId.EMBER, RuneId.ASTRA, RuneId.DRIFT, RuneId.FLUX),
        RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION to listOf(RuneId.EMBER, RuneId.DRIFT),
        RuneContext.JOURNAL_LOADING to emptyList(),
        RuneContext.JOURNAL to listOf(RuneId.CIPHER, RuneId.BOREAL, RuneId.DRIFT, RuneId.EMBER, RuneId.ASTRA, RuneId.FLUX),
        RuneContext.JOURNAL_DELETE_CONFIRMATION to listOf(RuneId.EMBER, RuneId.DRIFT),
        RuneContext.CONTACTS to listOf(RuneId.EMBER, RuneId.BOREAL, RuneId.DRIFT),
        RuneContext.LANGUAGE_CHANGE to listOf(RuneId.EMBER, RuneId.ASTRA, RuneId.DRIFT)
    )

    private fun bindingFor(context: RuneContext, rune: RuneId): RuneBinding = when (context) {
        RuneContext.CAMERA_INACTIVE -> when (rune) {
            RuneId.EMBER -> RuneBinding(rune, RuneAction.ActivateCamera)
            RuneId.ASTRA -> RuneBinding(rune, RuneAction.OpenJournal)
            RuneId.BOREAL -> RuneBinding(rune, RuneAction.OpenContacts)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.CAMERA -> when (rune) {
            RuneId.EMBER -> RuneBinding(rune, RuneAction.TakePhoto)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.ReturnToInactiveCamera)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.PHOTO_REVIEW -> when (rune) {
            RuneId.EMBER -> RuneBinding(rune, RuneAction.SavePhotoWithoutAnalysis)
            RuneId.ASTRA -> RuneBinding(rune, RuneAction.AnalyzePhoto)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.DiscardPhotoAndReturnToInactiveCamera)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.ANALYZING -> when (rune) {
            RuneId.FLUX -> RuneBinding(rune, RuneAction.CancelAnalysis)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.ANALYSIS_RESULT -> when (rune) {
            RuneId.EMBER -> RuneBinding(rune, RuneAction.ContinueScanning)
            RuneId.ASTRA -> RuneBinding(rune, RuneAction.PlayAnalysisResultAudio)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.ReturnToInactiveCamera)
            RuneId.FLUX -> RuneBinding(rune, RuneAction.DeleteCurrentFinding)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION -> when (rune) {
            RuneId.EMBER -> RuneBinding(rune, RuneAction.ConfirmFindingDeletion)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.CancelFindingDeletion)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.JOURNAL_LOADING -> RuneBinding(rune, RuneAction.Unassigned)
        RuneContext.JOURNAL -> when (rune) {
            RuneId.CIPHER -> RuneBinding(rune, RuneAction.MoveJournalPrevious)
            RuneId.BOREAL -> RuneBinding(rune, RuneAction.MoveJournalNext)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.ReturnToInactiveCamera)
            RuneId.ASTRA -> RuneBinding(rune, RuneAction.PlayJournalAudio)
            RuneId.EMBER -> RuneBinding(rune, RuneAction.StartJournalVoiceSearch)
            RuneId.FLUX -> RuneBinding(rune, RuneAction.DeleteCurrentFinding)
        }
        RuneContext.JOURNAL_DELETE_CONFIRMATION -> when (rune) {
            RuneId.EMBER -> RuneBinding(rune, RuneAction.ConfirmFindingDeletion)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.CancelFindingDeletion)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.CONTACTS -> when (rune) {
            RuneId.BOREAL -> RuneBinding(rune, RuneAction.SelectNextContact)
            RuneId.EMBER -> RuneBinding(rune, RuneAction.CallSelectedContact)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.CloseContacts)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
        RuneContext.LANGUAGE_CHANGE -> when (rune) {
            RuneId.EMBER -> RuneBinding(rune, RuneAction.ConfirmLanguageChange)
            RuneId.ASTRA -> RuneBinding(rune, RuneAction.CycleLanguageChange)
            RuneId.DRIFT -> RuneBinding(rune, RuneAction.CancelLanguageChange)
            else -> RuneBinding(rune, RuneAction.Unassigned)
        }
    }

    private fun longPressBindingFor(context: RuneContext, rune: RuneId): RuneBinding = when {
        context == RuneContext.JOURNAL && rune == RuneId.ASTRA ->
            RuneBinding(rune, RuneAction.StartLanguageChange)
        else -> RuneBinding(rune, RuneAction.Unassigned)
    }

    fun resolve(context: RuneContext, rune: RuneId): RuneBinding =
        checkNotNull(bindings[context]?.get(rune))

    /** Long-press resolution; [RuneAction.Unassigned] for every pair without a hold meaning. */
    fun resolveLongPress(context: RuneContext, rune: RuneId): RuneBinding =
        checkNotNull(longPressBindings[context]?.get(rune))

    /** All active bindings; a View decides how to page them across three physical slots. */
    fun availableHintBindings(context: RuneContext): List<RuneBinding> = hintOrder.getValue(context)
            .map { resolve(context, it) }
            .filter { it.enabled && it.hintAnimation != HintAnimationId.NONE }

    /** First page, retained for non-View callers and tests. */
    fun hintSlots(context: RuneContext): List<RuneHintSlot> {
        val visibleBindings = availableHintBindings(context).take(HintSlotId.values().size)
        return HintSlotId.values().mapIndexed { index, id ->
            RuneHintSlot(id = id, binding = visibleBindings.getOrNull(index))
        }
    }
}

/** Single entry point shared by touchscreen emulation and future hardware input. */
class RuneInputDispatcher(
    private val bindings: RuneBindings,
    private val activeContext: () -> RuneContext,
    private val onBindingResolved: (RuneBinding) -> Unit,
    private val onLongBindingResolved: (RuneBinding) -> Unit = onBindingResolved
) {
    fun onRunePressed(rune: RuneId) {
        onBindingResolved(bindings.resolve(activeContext(), rune))
    }

    fun onRuneLongPressed(rune: RuneId) {
        onLongBindingResolved(bindings.resolveLongPress(activeContext(), rune))
    }
}
