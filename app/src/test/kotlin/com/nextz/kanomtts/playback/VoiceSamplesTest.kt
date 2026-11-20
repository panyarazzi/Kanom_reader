package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSamplesTest {
    @Test fun `every language has a non-blank sample`() {
        Lang.entries.forEach { assertTrue(it.name, VoiceSamples.sampleFor(it).isNotBlank()) }
    }
}
