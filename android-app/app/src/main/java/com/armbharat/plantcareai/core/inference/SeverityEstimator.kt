
package com.armbharat.plantcareai.core.inference

import android.graphics.Bitmap
import android.graphics.Color

enum class SeverityLevel {
    LOW,
    MODERATE,
    SEVERE,
    UNABLE_TO_ESTIMATE
}

data class SeverityResult(
    val damagePercentage: Float,
    val severityLevel: SeverityLevel
)

object SeverityEstimator {

    private const val SAMPLE_SIZE = 200

    fun estimate(bitmap: Bitmap): SeverityResult {

        // Preserve original aspect ratio while resizing
        val scale = SAMPLE_SIZE.toFloat() /
                maxOf(bitmap.width, bitmap.height)

        val resizedWidth = (bitmap.width * scale)
            .toInt().coerceAtLeast(1)

        val resizedHeight = (bitmap.height * scale)
            .toInt().coerceAtLeast(1)

        val resized = Bitmap.createScaledBitmap(
            bitmap,
            resizedWidth,
            resizedHeight,
            true
        )

        val pixels = IntArray(resizedWidth * resizedHeight)

        resized.getPixels(
            pixels,
            0,
            resizedWidth,
            0,
            0,
            resizedWidth,
            resizedHeight
        )

        var leafPixels = 0
        var damagedPixels = 0

        val hsv = FloatArray(3)

        try {
            for (pixel in pixels) {

                Color.colorToHSV(pixel, hsv)

                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]

                // Green leaf regions
                val isGreenLeaf =
                    hue in 35f..165f &&
                            saturation > 0.12f &&
                            value > 0.12f

                // Brown, reddish and yellow regions
                // These may represent damaged leaf tissue
                val isWarmLeafRegion =
                    hue in 0f..35f &&
                            saturation > 0.18f &&
                            value > 0.12f

                val isYellowLeafRegion =
                    hue in 35f..65f &&
                            saturation > 0.25f &&
                            value > 0.20f

                // Count green and lesion-coloured pixels
                // as part of the estimated leaf area
                val isLeaf =
                    isGreenLeaf ||
                            isWarmLeafRegion ||
                            isYellowLeafRegion

                if (isLeaf) {

                    leafPixels++

                    // Brown or reddish lesions
                    val isBrownLesion =
                        hue in 0f..35f &&
                                saturation > 0.22f &&
                                value < 0.85f

                    // Yellow lesions
                    val isYellowLesion =
                        hue in 35f..65f &&
                                saturation > 0.35f &&
                                value > 0.25f

                    // Dark spots within detected leaf regions
                    val isDarkLesion =
                        value < 0.20f

                    if (
                        isBrownLesion ||
                        isYellowLesion ||
                        isDarkLesion
                    ) {
                        damagedPixels++
                    }
                }
            }

        } finally {
            if (resized !== bitmap) {
                resized.recycle()
            }
        }

        if (leafPixels == 0) {
            return SeverityResult(
                0f,
                SeverityLevel.UNABLE_TO_ESTIMATE
            )
        }

        val damagePercentage =
            (damagedPixels.toFloat() / leafPixels) * 100f

        val severity = when {
            damagePercentage < 15f ->
                SeverityLevel.LOW

            damagePercentage <= 40f ->
                SeverityLevel.MODERATE

            else ->
                SeverityLevel.SEVERE
        }

        return SeverityResult(
            damagePercentage = damagePercentage,
            severityLevel = severity
        )
    }
}
