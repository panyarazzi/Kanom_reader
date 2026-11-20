package com.nextz.kanomtts.reader
/** Page-turn direction from a horizontal drag offset. */
enum class SwipeDir { Next, Prev }
/**
 * Classify a finished horizontal drag. Finger moved LEFT (negative offset) past the
 * threshold → Next page; finger moved RIGHT (positive) past it → Prev; otherwise null.
 * Mirrors the Plan-06 device-tuned 48dp swipe.
 */
fun swipeDirection(offsetX: Float, thresholdPx: Float): SwipeDir? = when {
    offsetX <= -thresholdPx -> SwipeDir.Next
    offsetX >= thresholdPx -> SwipeDir.Prev
    else -> null
}
