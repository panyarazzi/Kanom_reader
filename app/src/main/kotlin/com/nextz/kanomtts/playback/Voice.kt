package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

data class Voice(
    val id: String,
    val name: String,
    val lang: Lang,
    val needsNetwork: Boolean,
    val quality: Int,
)
