package com.nextz.kanomtts.reader

import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Reader nav handled by Readium after the WebView's own touch handling (so it never steals
 * long-press selection). Original Zones maps the left side to Previous, right side to Next,
 * and the center to the reader menu. Anywhere to Next maps every non-center tap to Next.
 * Swipe page-turn is reimplemented from [DragEvent].
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
    tapAnywhereToNext: Boolean = false,
) : InputListener {
    @Volatile
    private var tapAnywhereToNextEnabled: Boolean = tapAnywhereToNext

    /** Update the active listener immediately when the user changes the reader setting. */
    fun setTapAnywhereToNext(enabled: Boolean) {
        tapAnywhereToNextEnabled = enabled
    }

    override fun onTap(event: TapEvent): Boolean {
        when (resolveReaderTapAction(
            x = event.point.x,
            y = event.point.y,
            width = viewportWidthPx(),
            height = viewportHeightPx(),
            tapAnywhereToNext = tapAnywhereToNextEnabled,
            centerMenuFraction = centerMenuFraction,
        )) {
            ReaderTapAction.TOGGLE_CHROME -> onToggleChrome()
            ReaderTapAction.PREVIOUS_PAGE -> onPrevPage()
            ReaderTapAction.NEXT_PAGE -> onNextPage()
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

/** Reader action for an unhandled tap after EPUB links/selections have been given priority. */
internal enum class ReaderTapAction { TOGGLE_CHROME, PREVIOUS_PAGE, NEXT_PAGE }

/**
 * The center menu zone is always reserved. With tap-anywhere enabled, every other
 * unhandled tap advances. Otherwise the left and right screen areas turn backward/forward.
 */
internal fun resolveReaderTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    tapAnywhereToNext: Boolean,
    centerMenuFraction: Float = 0.34f,
): ReaderTapAction =
    when {
        isCenterMenuTap(x, y, width, height, centerMenuFraction) -> ReaderTapAction.TOGGLE_CHROME
        tapAnywhereToNext -> ReaderTapAction.NEXT_PAGE
        width > 0f && x < width / 2f -> ReaderTapAction.PREVIOUS_PAGE
        else -> ReaderTapAction.NEXT_PAGE
    }
