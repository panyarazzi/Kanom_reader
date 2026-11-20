package com.nextz.kanomtts.reader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
class ReaderGesturesTest {
    @Test fun `finger left past threshold turns to next page`() {
        assertEquals(SwipeDir.Next, swipeDirection(offsetX = -60f, thresholdPx = 48f))
    }
    @Test fun `finger right past threshold turns to previous page`() {
        assertEquals(SwipeDir.Prev, swipeDirection(offsetX = 60f, thresholdPx = 48f))
    }
    @Test fun `below threshold is no turn`() {
        assertNull(swipeDirection(offsetX = -20f, thresholdPx = 48f))
        assertNull(swipeDirection(offsetX = 20f, thresholdPx = 48f))
    }
    @Test fun `exactly at threshold turns`() {
        assertEquals(SwipeDir.Next, swipeDirection(offsetX = -48f, thresholdPx = 48f))
        assertEquals(SwipeDir.Prev, swipeDirection(offsetX = 48f, thresholdPx = 48f))
    }
}
