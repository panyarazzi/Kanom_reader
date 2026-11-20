package com.nextz.kanomtts.playback

fun matchesSelectionStart(
    elementText: String,
    elementProgression: Double?,
    selectionHighlight: String,
    selectionProgression: Double?,
    tolerance: Double = 0.05,
): Boolean =
    elementText.contains(selectionHighlight) &&
        (selectionProgression == null ||
            elementProgression == null ||
            elementProgression >= selectionProgression - tolerance)
