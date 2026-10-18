package com.fieldscanner.app.runes

/** Existing scanner states expressed as contexts for rune bindings. */
enum class RuneContext {
    CAMERA_INACTIVE,
    CAMERA,
    PHOTO_REVIEW,
    ANALYZING,
    ANALYSIS_RESULT,
    ANALYSIS_RESULT_DELETE_CONFIRMATION,
    JOURNAL_LOADING,
    JOURNAL,
    JOURNAL_DELETE_CONFIRMATION,
    CONTACTS,

    /** Distinct mode entered from the journal to switch the app language by voice + rune (TASK-015). */
    LANGUAGE_CHANGE
}

/**
 * The semantic result of a rune press.
 *
 * Domain actions will be added only after their state-specific meanings are
 * approved. These two explicit values distinguish an undecided binding from a
 * deliberately inactive one.
 */
sealed interface RuneAction {
    object ActivateCamera : RuneAction
    object ReturnToInactiveCamera : RuneAction
    object OpenJournal : RuneAction
    object TakePhoto : RuneAction
    object SavePhotoWithoutAnalysis : RuneAction
    object DiscardPhotoAndReturnToInactiveCamera : RuneAction
    object AnalyzePhoto : RuneAction
    object CancelAnalysis : RuneAction
    object ContinueScanning : RuneAction
    object PlayAnalysisResultAudio : RuneAction
    object DeleteCurrentFinding : RuneAction
    object MoveJournalPrevious : RuneAction
    object MoveJournalNext : RuneAction
    object PlayJournalAudio : RuneAction
    object StartJournalVoiceSearch : RuneAction

    /** Confirm removing the finding under a delete-confirmation context (journal or analysis result). */
    object ConfirmFindingDeletion : RuneAction

    /** Leave a delete-confirmation context without removing the finding. */
    object CancelFindingDeletion : RuneAction

    /** Long-press on journal ASTRA: enter the voice-driven app-language switch mode (TASK-015). */
    object StartLanguageChange : RuneAction

    /** Inside [RuneContext.LANGUAGE_CHANGE]: apply the language now being offered / cycled. */
    object ConfirmLanguageChange : RuneAction

    /** Inside [RuneContext.LANGUAGE_CHANGE]: move to the next candidate language (spoken aloud). */
    object CycleLanguageChange : RuneAction

    /** Inside [RuneContext.LANGUAGE_CHANGE]: leave the mode without changing the language. */
    object CancelLanguageChange : RuneAction
    object OpenContacts : RuneAction
    object SelectNextContact : RuneAction
    object CallSelectedContact : RuneAction
    object CloseContacts : RuneAction
    object AnswerCall : RuneAction
    object RejectCall : RuneAction
    object EndCall : RuneAction
    object Unassigned : RuneAction
    object Disabled : RuneAction
}

enum class HintAnimationId {
    NONE,
    TBD
}

/** References for the final, externally supplied rune artwork and colour palette. */
data class RunePresentation(
    val rune: RuneId,
    val color: RuneColorId,
    val asset: RuneAssetId
)

enum class RuneColorId(val argb: Int) {
    ASTRA(0xFFFDFDFD.toInt()),
    BOREAL(0xFF8701FC.toInt()),
    CIPHER(0xFFFD6100.toInt()),
    DRIFT(0xFF0022FC.toInt()),
    EMBER(0xFF36F702.toInt()),
    FLUX(0xFFFC0101.toInt());
}

enum class RuneAssetId {
    ASTRA,
    BOREAL,
    CIPHER,
    DRIFT,
    EMBER,
    FLUX
}

data class RuneBinding(
    val rune: RuneId,
    val action: RuneAction,
    val hintAnimation: HintAnimationId = HintAnimationId.TBD
) {
    val enabled: Boolean get() = action !is RuneAction.Disabled && action !is RuneAction.Unassigned
}

enum class HintSlotId {
    SLOT_1,
    SLOT_2,
    SLOT_3
}

data class RuneHintSlot(
    val id: HintSlotId,
    val binding: RuneBinding?
)
