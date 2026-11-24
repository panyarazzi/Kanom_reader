package com.nextz.kanomtts.reader

import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Reader nav handled by Readium after the WebView's own touch handling (so it never steals
 * long-press selection). The screen is divided into a 3×3 grid: the center cell opens the
 * reader menu; the left column and top-middle cell go Previous; the right column and
 * bottom-middle cell go Next. Anywhere to Next maps every non-center tap to Next.
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

/**
 * The center cell of a proportional 3×3 screen grid is reserved for the reader menu.
 * Boundaries are half-open so a tap exactly on a grid line always resolves consistently.
 */
internal fun isCenterMenuTap(x: Float, y: Float, width: Float, height: Float): Boolean {
    if (width <= 0f || height <= 0f) return true
    return x >= width / 3f && x < width * 2f / 3f &&
        y >= height / 3f && y < height * 2f / 3f
}

/** Reader action for an unhandled tap after EPUB links/selections have been given priority. */
internal enum class ReaderTapAction { TOGGLE_CHROME, PREVIOUS_PAGE, NEXT_PAGE }

/**
 * 3×3 navigation:
 * - center cell: menu
 * - left column + top-middle: Previous
 * - right column + bottom-middle: Next
 * When tap-anywhere is enabled, all eight outer cells advance.
 */
internal fun resolveReaderTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    tapAnywhereToNext: Boolean,
): ReaderTapAction = when {
    isCenterMenuTap(x, y, width, height) -> ReaderTapAction.TOGGLE_CHROME
    tapAnywhereToNext -> ReaderTapAction.NEXT_PAGE
    width <= 0f || height <= 0f -> ReaderTapAction.TOGGLE_CHROME
    x < width / 3f -> ReaderTapAction.PREVIOUS_PAGE
    x >= width * 2f / 3f -> ReaderTapAction.NEXT_PAGE
    y < height / 3f -> ReaderTapAction.PREVIOUS_PAGE
    else -> ReaderTapAction.NEXT_PAGE
}
