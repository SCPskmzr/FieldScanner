package com.fieldscanner.app.journal

import com.fieldscanner.app.ScannerViewModel
import com.fieldscanner.app.data.finding.Finding

/** Coordinates journal navigation and filtering with the scanner state holder. */
class JournalCoordinator(private val scanner: ScannerViewModel) {
    fun open() = scanner.loadJournal()

    fun showLoaded(operationId: Long, findings: List<Finding>): Finding? {
        if (findings.isEmpty()) return null
        scanner.setJournal(operationId, findings)
        return scanner.currentJournalFinding()
    }

    fun move(delta: Int): Finding? = scanner.moveJournal(delta)
    fun filter(candidates: List<String>): Finding? = scanner.filterJournal(candidates)
    fun current(): Finding? = scanner.currentJournalFinding()
    fun isVisible(): Boolean = scanner.isJournalVisible()
}
