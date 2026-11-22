package com.nextz.kanomtts.reader

import android.webkit.JavascriptInterface

/** Narrow JS bridge used only to remember the tapped anchor position for Readium footnotes. */
internal class FootnoteTapBridge(private val onTap: (Float, Float) -> Unit) {
    @JavascriptInterface
    fun capture(x: Float, y: Float) = onTap(x, y)
}
