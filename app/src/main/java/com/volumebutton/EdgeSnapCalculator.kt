package com.volumebutton

/**
 * Bounds defining the safe dragging/resting area of the overlay window,
 * taking into account screen dimensions, view size, and system bar / gesture insets.
 */
data class SafeBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
)

/**
 * Result of computing the nearest edge to snap to.
 */
data class SnapResult(
    val edge: ScreenEdge,
    val targetX: Int,
    val targetY: Int,
    val fraction: Float
)

/**
 * Pure Kotlin mathematical functions for calculating safe bounds,
 * nearest edges, snapping targets, and fraction-based position restoration.
 * Kept isolated and deterministic for 100% unit test coverage.
 */
object EdgeSnapCalculator {

    fun calculateSafeBounds(
        screenWidth: Int,
        screenHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        insetLeft: Int = 0,
        insetTop: Int = 0,
        insetRight: Int = 0,
        insetBottom: Int = 0
    ): SafeBounds {
        val minX = insetLeft
        val maxX = (screenWidth - viewWidth - insetRight).coerceAtLeast(minX)
        val minY = insetTop
        val maxY = (screenHeight - viewHeight - insetBottom).coerceAtLeast(minY)
        return SafeBounds(minX, maxX, minY, maxY)
    }

    fun findNearestEdge(
        currentX: Int,
        currentY: Int,
        bounds: SafeBounds
    ): SnapResult {
        val clampedX = currentX.coerceIn(bounds.minX, bounds.maxX)
        val clampedY = currentY.coerceIn(bounds.minY, bounds.maxY)

        val distLeft = clampedX - bounds.minX
        val distRight = bounds.maxX - clampedX
        val distTop = clampedY - bounds.minY
        val distBottom = bounds.maxY - clampedY

        val minDist = minOf(distLeft, distRight, distTop, distBottom)

        return when (minDist) {
            distLeft -> {
                val spanY = (bounds.maxY - bounds.minY).toFloat()
                val fraction = if (spanY > 0) (clampedY - bounds.minY) / spanY else 0.5f
                SnapResult(ScreenEdge.LEFT, bounds.minX, clampedY, fraction.coerceIn(0f, 1f))
            }
            distRight -> {
                val spanY = (bounds.maxY - bounds.minY).toFloat()
                val fraction = if (spanY > 0) (clampedY - bounds.minY) / spanY else 0.5f
                SnapResult(ScreenEdge.RIGHT, bounds.maxX, clampedY, fraction.coerceIn(0f, 1f))
            }
            distTop -> {
                val spanX = (bounds.maxX - bounds.minX).toFloat()
                val fraction = if (spanX > 0) (clampedX - bounds.minX) / spanX else 0.5f
                SnapResult(ScreenEdge.TOP, clampedX, bounds.minY, fraction.coerceIn(0f, 1f))
            }
            else -> {
                val spanX = (bounds.maxX - bounds.minX).toFloat()
                val fraction = if (spanX > 0) (clampedX - bounds.minX) / spanX else 0.5f
                SnapResult(ScreenEdge.BOTTOM, clampedX, bounds.maxY, fraction.coerceIn(0f, 1f))
            }
        }
    }

    fun calculateCoordinates(
        edge: ScreenEdge,
        fraction: Float,
        bounds: SafeBounds
    ): Pair<Int, Int> {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        return when (edge) {
            ScreenEdge.LEFT -> {
                val spanY = bounds.maxY - bounds.minY
                Pair(bounds.minX, bounds.minY + (spanY * clampedFraction).toInt())
            }
            ScreenEdge.RIGHT -> {
                val spanY = bounds.maxY - bounds.minY
                Pair(bounds.maxX, bounds.minY + (spanY * clampedFraction).toInt())
            }
            ScreenEdge.TOP -> {
                val spanX = bounds.maxX - bounds.minX
                Pair(bounds.minX + (spanX * clampedFraction).toInt(), bounds.minY)
            }
            ScreenEdge.BOTTOM -> {
                val spanX = bounds.maxX - bounds.minX
                Pair(bounds.minX + (spanX * clampedFraction).toInt(), bounds.maxY)
            }
        }
    }
}

