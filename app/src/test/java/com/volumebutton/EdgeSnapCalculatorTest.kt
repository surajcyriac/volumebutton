package com.volumebutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSnapCalculatorTest {

    private val screenWidth = 1080
    private val screenHeight = 2400
    private val viewWidth = 100
    private val viewHeight = 100

    private val safeBounds = EdgeSnapCalculator.calculateSafeBounds(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        insetLeft = 0,
        insetTop = 80,
        insetRight = 0,
        insetBottom = 120
    )

    @Test
    fun testSafeBoundsCalculation() {
        assertEquals(0, safeBounds.minX)
        assertEquals(1080 - 100, safeBounds.maxX) // 980
        assertEquals(80, safeBounds.minY)
        assertEquals(2400 - 100 - 120, safeBounds.maxY) // 2180
    }

    @Test
    fun testSnapToRightEdge() {
        // Closer to right edge
        val currentX = 850
        val currentY = 1000

        val result = EdgeSnapCalculator.findNearestEdge(currentX, currentY, safeBounds)
        assertEquals(ScreenEdge.RIGHT, result.edge)
        assertEquals(safeBounds.maxX, result.targetX)
        assertEquals(currentY, result.targetY)
        assertTrue(result.fraction in 0f..1f)
    }

    @Test
    fun testSnapToLeftEdge() {
        // Closer to left edge
        val currentX = 120
        val currentY = 1200

        val result = EdgeSnapCalculator.findNearestEdge(currentX, currentY, safeBounds)
        assertEquals(ScreenEdge.LEFT, result.edge)
        assertEquals(safeBounds.minX, result.targetX)
        assertEquals(currentY, result.targetY)
        assertTrue(result.fraction in 0f..1f)
    }

    @Test
    fun testSnapToTopEdge() {
        // Closer to top edge
        val currentX = 500
        val currentY = 100

        val result = EdgeSnapCalculator.findNearestEdge(currentX, currentY, safeBounds)
        assertEquals(ScreenEdge.TOP, result.edge)
        assertEquals(currentX, result.targetX)
        assertEquals(safeBounds.minY, result.targetY)
    }

    @Test
    fun testSnapToBottomEdge() {
        // Closer to bottom edge
        val currentX = 500
        val currentY = 2150

        val result = EdgeSnapCalculator.findNearestEdge(currentX, currentY, safeBounds)
        assertEquals(ScreenEdge.BOTTOM, result.edge)
        assertEquals(currentX, result.targetX)
        assertEquals(safeBounds.maxY, result.targetY)
    }

    @Test
    fun testPositionRestorationSurvivesRotationOrResize() {
        val originalEdge = ScreenEdge.RIGHT
        val fraction = 0.5f

        val coords = EdgeSnapCalculator.calculateCoordinates(originalEdge, fraction, safeBounds)
        assertEquals(safeBounds.maxX, coords.first)
        val expectedY = safeBounds.minY + ((safeBounds.maxY - safeBounds.minY) * 0.5f).toInt()
        assertEquals(expectedY, coords.second)

        // After rotation (dimensions swapped)
        val rotatedBounds = EdgeSnapCalculator.calculateSafeBounds(
            screenWidth = 2400,
            screenHeight = 1080,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            insetLeft = 80,
            insetTop = 0,
            insetRight = 120,
            insetBottom = 0
        )
        val rotatedCoords = EdgeSnapCalculator.calculateCoordinates(originalEdge, fraction, rotatedBounds)
        assertEquals(rotatedBounds.maxX, rotatedCoords.first)
        val expectedRotatedY = rotatedBounds.minY + ((rotatedBounds.maxY - rotatedBounds.minY) * 0.5f).toInt()
        assertEquals(expectedRotatedY, rotatedCoords.second)
    }
}

