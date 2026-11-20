package com.nextz.kanomtts.playback
import org.readium.r2.shared.publication.Locator

/** The Locator for a sentence spanning [start, end) within [element]'s text (end-exclusive). */
object SentenceLocators {
    fun forSentence(element: Locator, start: Int, end: Int): Locator =
        element.copy(text = element.text.substring(start until end))
}
