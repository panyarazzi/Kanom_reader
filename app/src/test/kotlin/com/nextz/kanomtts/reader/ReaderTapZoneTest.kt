package com.nextz.kanomtts.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapZoneTest {
    @Test fun centerTapOpensMenu() { assertTrue(isCenterMenuTap(500f, 500f, 1000f, 1000f)) }
    @Test fun tapOutsideCenterAdvances() {
        assertFalse(isCenterMenuTap(50f, 500f, 1000f, 1000f))
        assertFalse(isCenterMenuTap(500f, 50f, 1000f, 1000f))
    }
}
