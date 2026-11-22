package com.nextz.kanomtts.reader

import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Reader nav handled by Readium after the WebView's own touch handling (so it never steals
 * long-press selection). The central third of the viewport toggles the reader menu; every other
 * unhandled tap advances one page. Swipe page-turn is reimplemented from [DragEvent].
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
    private val viewportWidthPx: () -> Float,
    private val viewportHeightPx: () -> Float,
    private val centerMenuFraction: Float = 0.34f,
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        if (isCenterMenuTap(event.point.x, event.point.y, viewportWidthPx(), viewportHeightPx(), centerMenuFraction)) {
            onToggleChrome()
        } else {
            onNextPage()
        }
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

/** True only for taps inside the centered menu activation area. */
internal fun isCenterMenuTap(x: Float, y: Float, width: Float, height: Float, fraction: Float = 0.34f): Boolean {
    if (width <= 0f || height <= 0f) return true
    val zone = fraction.coerceIn(0.05f, 0.8f)
    val halfWidth = width * zone / 2f
    val halfHeight = height * zone / 2f
    return x in (width / 2f - halfWidth)..(width / 2f + halfWidth) &&
        y in (height / 2f - halfHeight)..(height / 2f + halfHeight)
}
