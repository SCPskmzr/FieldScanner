package com.fieldscanner.app

import kotlin.math.abs

/** Pure waveform analysis used by the visual-only lower status display. */
internal object SignalAnalysis {
    /** Hash-based rather than periodic: the signal contains calm plateaus, dips and isolated peaks. */
    fun signalValue(pointIndex: Int, timeBucket: Long, amplitude: Float): Float {
        val block = pointIndex / 5
        val activity = maxOf(unitNoise(block, timeBucket, 17), unitNoise(block + 31, timeBucket, 113))
        if (activity < if (amplitude > 30f) 0.68f else 0.78f) {
            return signedNoise(pointIndex, timeBucket, 29) * amplitude * 0.055f
        }
        val local = (
            signedNoise(pointIndex - 1, timeBucket, 43) * 0.2f +
                signedNoise(pointIndex, timeBucket, 43) * 0.6f +
                signedNoise(pointIndex + 1, timeBucket, 43) * 0.2f
            ) * amplitude
        val peak = if (unitNoise(pointIndex, timeBucket, 71) > 0.91f) {
            signedNoise(pointIndex, timeBucket, 89) * amplitude * 0.85f
        } else {
            0f
        }
        return (local + peak).coerceIn(-amplitude, amplitude)
    }

    fun sampleAtX(samples: List<SignalSample>, x: Float): SignalSample {
        val rightIndex = samples.indexOfFirst { it.x >= x }
        if (rightIndex == -1) return samples.last()
        if (rightIndex == 0) return samples.first()
        val left = samples[rightIndex - 1]
        val right = samples[rightIndex]
        return SignalSample(x, interpolate(left.x, left.y, right.x, right.y, x))
    }

    fun selectKeyExtrema(
        samples: List<SignalSample>,
        maxCount: Int,
        minimumDistance: Float,
        minimumProminence: Float
    ): List<SignalExtremum> {
        if (samples.size < 9) return emptyList()
        val candidates = ArrayList<SignalExtremum>()
        for (index in 3 until samples.lastIndex - 2) {
            val sample = samples[index]
            val isMaximum = sample.y < samples[index - 1].y && sample.y <= samples[index + 1].y
            val isMinimum = sample.y > samples[index - 1].y && sample.y >= samples[index + 1].y
            if (!isMaximum && !isMinimum) continue
            val surrounding = (samples[index - 3].y + samples[index + 3].y) / 2f
            val prominence = abs(sample.y - surrounding)
            if (prominence >= minimumProminence) candidates += SignalExtremum(sample, isMaximum, prominence)
        }
        val selected = ArrayList<SignalExtremum>()
        candidates.sortedByDescending { it.prominence }.forEach { candidate ->
            if (selected.size < maxCount && selected.none { abs(it.sample.x - candidate.sample.x) < minimumDistance }) {
                selected += candidate
            }
        }
        return selected.sortedBy { it.sample.x }
    }

    private fun interpolate(fromX: Float, fromY: Float, toX: Float, toY: Float, x: Float): Float =
        fromY + (toY - fromY) * ((x - fromX) / (toX - fromX))

    private fun unitNoise(index: Int, timeBucket: Long, salt: Int): Float {
        val key = index * 1_103_515_245 + timeBucket.toInt() * 12_345 + salt
        return (hash(key) ushr 8 and 0x00FF_FFFF) / 16_777_215f
    }

    private fun signedNoise(index: Int, timeBucket: Long, salt: Int): Float =
        unitNoise(index, timeBucket, salt) * 2f - 1f

    private fun hash(value: Int): Int {
        var result = value xor (value shl 13)
        result = result xor (result ushr 17)
        return result xor (result shl 5)
    }
}

internal data class SignalSample(val x: Float, val y: Float)

internal data class SignalExtremum(
    val sample: SignalSample,
    val isMaximum: Boolean,
    val prominence: Float
)
