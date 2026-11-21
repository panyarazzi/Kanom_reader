package com.nextz.kanomtts.reader

import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Observes reader gestures without consuming them. It is registered before Readium's
 * directional adapter so edge taps also refresh the toolbar timeout. Returning false
 * preserves normal EPUB handling, including links, footnotes and native text selection.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderInteractionInputListener(
    private val onInteraction: () -> Unit,
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        onInteraction()
        return false
    }

    override fun onDrag(event: DragEvent): Boolean {
        if (event.type == DragEvent.Type.End) onInteraction()
        return false
    }
}
