package com.fieldscanner.app.runes

/** Persistent rune identity, independent of the current application context. */
object RuneCatalog {
    fun presentation(rune: RuneId): RunePresentation = when (rune) {
        RuneId.ASTRA -> RunePresentation(rune, RuneColorId.ASTRA, RuneAssetId.ASTRA)
        RuneId.BOREAL -> RunePresentation(rune, RuneColorId.BOREAL, RuneAssetId.BOREAL)
        RuneId.CIPHER -> RunePresentation(rune, RuneColorId.CIPHER, RuneAssetId.CIPHER)
        RuneId.DRIFT -> RunePresentation(rune, RuneColorId.DRIFT, RuneAssetId.DRIFT)
        RuneId.EMBER -> RunePresentation(rune, RuneColorId.EMBER, RuneAssetId.EMBER)
        RuneId.FLUX -> RunePresentation(rune, RuneColorId.FLUX, RuneAssetId.FLUX)
    }
}
