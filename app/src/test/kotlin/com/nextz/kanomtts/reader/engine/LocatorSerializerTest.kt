package com.nextz.kanomtts.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class LocatorSerializerTest {
    private fun sample() = Locator(
        href = Url("chapter1.xhtml")!!,
        mediaType = MediaType.XHTML,
        title = "Chapter 1",
        locations = Locator.Locations(progression = 0.5, totalProgression = 0.25, position = 3),
        text = Locator.Text(before = "before ", highlight = "the spoken sentence", after = " after"),
    )

    @Test fun round_trips_through_json() {
        val original = sample()
        val restored = LocatorSerializer.decode(LocatorSerializer.encode(original))
        assertEquals(original, restored)
    }

    @Test fun decode_null_is_null() {
        assertNull(LocatorSerializer.decode(null))
    }

    @Test fun decode_garbage_is_null() {
        assertNull(LocatorSerializer.decode("not json"))
    }
}
