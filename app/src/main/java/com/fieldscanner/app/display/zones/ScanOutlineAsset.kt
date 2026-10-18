package com.fieldscanner.app.display.zones

import android.content.Context
import android.graphics.PathMeasure
import androidx.core.graphics.PathParser
import com.fieldscanner.app.SignalSample
import com.fieldscanner.app.data.finding.ScanVisual

/**
 * Loads and samples the one open SVG path used by a result outline.
 *
 * The requested [ScanVisual] is resolved against its
 * [ScanVisual.fallbackChain]: the first asset that exists and parses wins.
 * Returns `null` when no asset in the chain can be read, in which case the
 * caller keeps its procedural fallback.
 */
internal object ScanOutlineAsset {
    private const val ASSET_DIR = "scan_outlines"
    private val outlinePathPattern = Regex(
        """<path\b(?=[^>]*\bid\s*=\s*["']outline["'])[^>]*\bd\s*=\s*["']([^"']+)["']"""
    )

    fun loadSamples(context: Context, visual: ScanVisual, sampleCount: Int): List<SignalSample>? {
        require(sampleCount >= 2) { "At least two samples are required." }
        for (candidate in visual.fallbackChain()) {
            val samples = loadAsset(context, candidate.assetName, sampleCount)
            if (samples != null) return samples
        }
        return null
    }

    private fun loadAsset(context: Context, assetName: String, sampleCount: Int): List<SignalSample>? = runCatching {
        val svg = context.assets.open("$ASSET_DIR/$assetName").bufferedReader().use { it.readText() }
        val pathData = extractPathData(svg) ?: return null
        val path = PathParser.createPathFromPathData(pathData) ?: return null
        val measure = PathMeasure(path, false)
        require(measure.length > 0f) { "Outline $assetName has no measurable path." }
        FloatArray(2).let { position ->
            List(sampleCount) { index ->
                val distance = measure.length * index / (sampleCount - 1)
                check(measure.getPosTan(distance, position, null)) { "Could not sample outline $assetName." }
                ScanMorphGeometry.projectTemplatePoint(position[0], position[1])
            }
        }
    }.getOrNull()

    internal fun extractPathData(svg: String): String? =
        outlinePathPattern.find(svg)?.groupValues?.get(1)
}
