package com.nextz.kanomtts.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

// Icons use the core set (material-icons-core via BOM) to avoid the ~4 MB extended dep.
// Core equivalents: List ≈ library (AutoMirrored for RTL), PlayArrow ≈ now-playing, Settings = settings.
enum class KanomDestination(val route: String, val label: String, val icon: ImageVector) {
    LIBRARY("library", "คลัง", Icons.AutoMirrored.Filled.List),
    NOW_PLAYING("now_playing", "กำลังฟัง", Icons.Filled.PlayArrow),
    SETTINGS("settings", "ตั้งค่า", Icons.Filled.Settings),
}
