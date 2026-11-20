package com.nextz.kanomtts.reader

import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Reader nav handled by Readium after the WebView's own touch handling (so it never steals
 * long-press selection). Registered AFTER [DirectionalNavigationAdapter]: the adapter consumes
 * left/right EDGE taps, so any tap reaching here is a CENTER tap → toggle chrome. Swipe page-turn
 * is reimplemented from [DragEvent] (the device-tuned 48dp threshold).
 *
 * NOTE: the paginated WebView may also page horizontally on drag; if a swipe double-turns on
 * device, drop onDrag and rely on native paging (Task 4 device check).
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderNavInputListener(
    private val onToggleChrome: () -> Unit,
    private val onNextPage: () -> Unit,
    private val onPrevPage: () -> Unit,
    private val swipeThresholdPx: Float,
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        // Edge taps were consumed by DirectionalNavigationAdapter; a tap here is center.
        onToggleChrome()
        return true
    }

    override fun onDrag(event: DragEvent): Boolean {
        if (event.type != DragEvent.Type.End) return false
        return when (swipeDirection(event.offset.x, swipeThresholdPx)) {
            SwipeDir.Next -> { onNextPage(); true }
            SwipeDir.Prev -> { onPrevPage(); true }
            null -> false
        }
    }
}
