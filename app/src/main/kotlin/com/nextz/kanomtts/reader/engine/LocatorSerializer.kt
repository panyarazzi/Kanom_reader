package com.nextz.kanomtts.reader.engine

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Serialize a Readium [Locator] to/from the JSON String stored in Room (ProgressEntity/BookmarkEntity). */
object LocatorSerializer {
    fun encode(locator: Locator): String = locator.toJSON().toString()

    fun decode(json: String?): Locator? =
        json?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
}
