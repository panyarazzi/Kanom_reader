package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressDaoTest : RoomTestBase() {
    @Test fun upsert_overwrites_and_get_returns_latest() = runTest {
        db.progressDao().upsert(ProgressEntity("b1", "loc1", null, 0.1f, 1L))
        db.progressDao().upsert(ProgressEntity("b1", "loc2", "tts2", 0.5f, 2L))
        val p = db.progressDao().get("b1")!!
        assertEquals("loc2", p.locator); assertEquals("tts2", p.ttsLocator); assertEquals(0.5f, p.percent, 0f)
    }
    @Test fun observe_emits_null_when_absent_then_value() = runTest {
        db.progressDao().upsert(ProgressEntity("b2", "loc", null, 0.2f, 1L))
        assertEquals(0.2f, db.progressDao().observe("b2").first()!!.percent, 0f)
    }
}
