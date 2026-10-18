package com.fieldscanner.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ScannerViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ScannerViewModel(container.analyzer, container.findings, container.speech, container.diagnostics) {
            container.languagePreference.current().locale()
        } as T
}
