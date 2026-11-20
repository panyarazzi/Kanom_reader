package com.nextz.kanomtts.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// NOTE: uses static Anchan schemes. Material 3 dynamic color (seeded from #5D63D6,
// spec §3.1/§6.1) is intentionally DEFERRED per Plan 03 self-review. When adding it,
// introduce a `dynamicColor: Boolean = false` param (additive) + dynamicLight/DarkColorScheme.
@Composable
fun KanomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val kanomColors = if (darkTheme) DarkKanomColors else LightKanomColors
    CompositionLocalProvider(LocalKanomColors provides kanomColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KanomTypography,
            shapes = KanomShapes,
            content = content,
        )
    }
}

/** Access brand colors: `KanomTheme.colors.highlight`. */
object KanomTheme {
    val colors: KanomColors
        @Composable get() = LocalKanomColors.current
}

@Preview(name = "Kanom brand — light")
@Composable
private fun KanomBrandPreview() {
    KanomTheme(darkTheme = false) {
        Surface {
            Column(Modifier.padding(16.dp)) {
                Text("ขนม Kanom TTS", style = MaterialTheme.typography.displaySmall)
                Text("บทที่ ๑ — จุดเริ่มต้น", style = MaterialTheme.typography.titleLarge)
                Text(
                    "เธอเปิดแอป Kanom TTS แล้วกดเล่น",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.background(KanomTheme.colors.highlightContainer).padding(4.dp),
                )
            }
        }
    }
}
