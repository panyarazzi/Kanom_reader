package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapZoneTest {
    @Test fun centerTapOpensMenuInOriginalMode() {
        assertTrue(isCenterMenuTap(500f, 500f, 1000f, 1000f))
        assertEquals(
            ReaderTapAction.TOGGLE_CHROME,
            resolveReaderTapAction(500f, 500f, 1000f, 1000f, tapAnywhereToNext = false),
        )
    }

    @Test fun originalZonesSplitPreviousAndNextAroundCenterMenu() {
        assertFalse(isCenterMenuTap(50f, 500f, 1000f, 1000f))
        assertFalse(isCenterMenuTap(950f, 500f, 1000f, 1000f))
        assertEquals(
            ReaderTapAction.PREVIOUS_PAGE,
            resolveReaderTapAction(50f, 500f, 1000f, 1000f, tapAnywhereToNext = false),
        )
        assertEquals(
            ReaderTapAction.NEXT_PAGE,
            resolveReaderTapAction(950f, 500f, 1000f, 1000f, tapAnywhereToNext = false),
        )
    }

    @Test fun anywhereToNextAdvancesEverywhereExceptTheCenterMenu() {
        listOf(50f, 950f).forEach { x ->
            assertEquals(
                ReaderTapAction.NEXT_PAGE,
                resolveReaderTapAction(x, 500f, 1000f, 1000f, tapAnywhereToNext = true),
            )
        }
        assertEquals(
            ReaderTapAction.TOGGLE_CHROME,
            resolveReaderTapAction(500f, 500f, 1000f, 1000f, tapAnywhereToNext = true),
        )
    }
    @Test fun anywhereToNextKeepsTheEntireCenterColumnForTheMenu() {
        // A tap can land high or low within the central column; Y must not turn it into Next.
        listOf(0f, 50f, 250f, 500f, 750f, 950f, 1000f).forEach { y ->
            assertEquals(
                "center-column tap at y=$y must open the menu",
                ReaderTapAction.TOGGLE_CHROME,
                resolveReaderTapAction(
                    x = 500f, y = y, width = 1000f, height = 1000f,
                    tapAnywhereToNext = true,
                ),
            )
        }
    }

    @Test fun anywhereToNextStillAdvancesOutsideTheCenterColumnAtAnyHeight() {
        listOf(0f, 250f, 500f, 750f, 1000f).forEach { y ->
            assertEquals(
                ReaderTapAction.NEXT_PAGE,
                resolveReaderTapAction(
                    x = 50f, y = y, width = 1000f, height = 1000f,
                    tapAnywhereToNext = true,
                ),
            )
            assertEquals(
                ReaderTapAction.NEXT_PAGE,
                resolveReaderTapAction(
                    x = 950f, y = y, width = 1000f, height = 1000f,
                    tapAnywhereToNext = true,
                ),
            )
        }
    }

    @Test fun originalZonesRemainScreenRelativeInLandscape() {
        assertEquals(
            ReaderTapAction.PREVIOUS_PAGE,
            resolveReaderTapAction(40f, 300f, 1200f, 600f, tapAnywhereToNext = false),
        )
        assertEquals(
            ReaderTapAction.NEXT_PAGE,
            resolveReaderTapAction(1160f, 300f, 1200f, 600f, tapAnywhereToNext = false),
        )
        assertEquals(
            ReaderTapAction.TOGGLE_CHROME,
            resolveReaderTapAction(600f, 300f, 1200f, 600f, tapAnywhereToNext = false),
        )
    }

}
