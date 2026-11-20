package com.nextz.kanomtts.text.replacement

data class ReplacementRule(
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val enabled: Boolean = true,
    val order: Int = 0,
    val showInReader: Boolean = false,
)
