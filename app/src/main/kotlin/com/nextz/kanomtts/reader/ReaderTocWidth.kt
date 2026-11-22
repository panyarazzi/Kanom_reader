package com.nextz.kanomtts.reader

/** Target-device sizing: Meebook G7S uses a compact half-width drawer; phones use a wider panel. */
internal fun readerTocWidthFraction(model: String, manufacturer: String): Float =
    if (model.contains("G7S", ignoreCase = true) || manufacturer.contains("Meebook", ignoreCase = true)) 0.50f else 0.82f
