package com.nextz.kanomtts.playback.util

import com.nextz.kanomtts.playback.SentenceUtterance
import com.nextz.kanomtts.playback.TtsContentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.readium.r2.shared.publication.Locator

/**
 * Test fake for [TtsContentSource].
 * Returns the fixed [utterances] list as a cold flow on each [stream] call.
 */
class FakeTtsContentSource(
    private val utterances: List<SentenceUtterance>,
) : TtsContentSource {

    override fun stream(bookId: String, fromLocator: Locator?, skipToSelection: Boolean): Flow<SentenceUtterance> =
        flowOf(*utterances.toTypedArray())
}
