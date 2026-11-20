package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

/** Narrow seam for enumerating installed system voices — lets settings VMs avoid the whole provider. */
fun interface VoiceCatalog {
    suspend fun voices(lang: Lang): List<Voice>
}
