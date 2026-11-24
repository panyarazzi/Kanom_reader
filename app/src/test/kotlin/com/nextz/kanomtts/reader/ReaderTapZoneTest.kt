package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapZoneTest {
    private fun action(x: Float, y: Float, width: Float = 900f, height: Float = 900f, anywhere: Boolean = false) =
        resolveReaderTapAction(x, y, width, height, tapAnywhereToNext = anywhere)

    @Test fun centerCellOpensMenu() {
        assertTrue(isCenterMenuTap(450f, 450f, 900f, 900f))
        assertEquals(ReaderTapAction.TOGGLE_CHROME, action(450f, 450f))
    }

    @Test fun onlyTheMiddleThirdInBothAxesIsTheMenu() {
        assertTrue(isCenterMenuTap(300f, 300f, 900f, 900f))
        assertTrue(isCenterMenuTap(599.9f, 599.9f, 900f, 900f))
        assertFalse(isCenterMenuTap(299.9f, 450f, 900f, 900f))
        assertFalse(isCenterMenuTap(600f, 450f, 900f, 900f))
        assertFalse(isCenterMenuTap(450f, 299.9f, 900f, 900f))
        assertFalse(isCenterMenuTap(450f, 600f, 900f, 900f))
    }

    @Test fun eightOuterCellsMapToFourPreviousAndFourNextZones() {
        val previousCells = listOf(
            150f to 150f, 450f to 150f, 750f to 150f,
            150f to 450f,
            150f to 750f,
        )
        val nextCells = listOf(
            750f to 450f,
            150f to 750f, 450f to 750f, 750f to 750f,
        )
        previousCells.forEach { (x, y) -> assertEquals("previous at ($x,$y)", ReaderTapAction.PREVIOUS_PAGE, action(x, y)) }
        nextCells.forEach { (x, y) -> assertEquals("next at ($x,$y)", ReaderTapAction.NEXT_PAGE, action(x, y)) }
    }

    @Test fun middleRowLeftAndRightRemainPreviousAndNext() {
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(150f, 450f))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(750f, 450f))
    }

    @Test fun anywhereToNextAdvancesAllEightOuterCellsButKeepsCenterMenu() {
        val outer = listOf(
            150f to 150f, 450f to 150f, 750f to 150f,
            150f to 450f, 750f to 450f,
            150f to 750f, 450f to 750f, 750f to 750f,
        )
        outer.forEach { (x, y) ->
            assertEquals("outer tap at ($x,$y)", ReaderTapAction.NEXT_PAGE, action(x, y, anywhere = true))
        }
        assertEquals(ReaderTapAction.TOGGLE_CHROME, action(450f, 450f, anywhere = true))
    }

    @Test fun gridUsesRelativeCoordinatesInPortraitAndLandscape() {
        assertEquals(ReaderTapAction.TOGGLE_CHROME, action(200f, 300f, width = 600f, height = 900f))
        assertEquals(ReaderTapAction.TOGGLE_CHROME, action(400f, 200f, width = 1200f, height = 600f))
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(100f, 300f, width = 600f, height = 900f))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(1100f, 300f, width = 1200f, height = 600f))
    }

    @Test fun exactGridLinesResolveDeterministically() {
        // Left/top boundaries belong to the center cell; right/bottom boundaries do not.
        assertEquals(ReaderTapAction.TOGGLE_CHROME, action(300f, 300f))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(600f, 450f))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(450f, 600f))
    }

    @Test fun invalidViewportDoesNotAccidentallyTurnPage() {
        assertEquals(ReaderTapAction.TOGGLE_CHROME, action(10f, 10f, width = 0f, height = 0f))
    }
}
