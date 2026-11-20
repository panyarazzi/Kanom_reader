package com.nextz.kanomtts.playback

import kotlinx.coroutines.flow.Flow
import org.readium.r2.shared.publication.Locator

interface TtsContentSource {
    fun stream(bookId: String, fromLocator: Locator?, skipToSelection: Boolean = false): Flow<SentenceUtterance>
}
