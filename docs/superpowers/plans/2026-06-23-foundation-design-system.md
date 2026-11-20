# Foundation & Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the `:app` module into a Jetpack **Compose + Material 3 Expressive** app carrying the **"Anchan & Coconut"** Kanom design system (color, typography, shape, the karaoke-highlight token, reader palettes), wired with **Hilt** and a **Compose Navigation** scaffold whose bottom bar matches the approved three screens (Library / Now Playing / Settings).

**Architecture:** `:app` keeps AGP 9's built-in Kotlin (no Kotlin plugin applied) and gains Compose via `buildFeatures.compose`. The design system lives in a self-contained `com.nextz.kanomtts.ui.theme` package (colors, type, shape, extended colors via a `CompositionLocal`, and the `KanomTheme` entry point) so feature screens depend only on `KanomTheme` + `MaterialTheme`/`LocalKanomColors`. Navigation is a single-Activity Compose `NavHost` with placeholder destinations to be filled by later feature plans. No business logic here — this is the shell everything else renders inside.

## Decision: module granularity

The design system is intentionally placed in `:app` (`com.nextz.kanomtts.ui.theme`) rather than the spec §3.2 `:core:designsystem` module. Modules are **collapsed** for the solo build per the spec's own "modules can be collapsed" latitude — a separate `:core:designsystem` module at this stage adds build complexity with no benefit. Extraction to `:core:designsystem` is to be revisited if/when feature modules (`:feature:*`) are introduced in Plans 05/06.

**Tech Stack:** Jetpack Compose (BOM), Material 3 + Material 3 Expressive, Compose Navigation, Hilt, AGP 9.2.1 built-in Kotlin 2.2.10. Bundled Google Fonts: Itim (display) + Sarabun (body).

## Global Constraints

- `:app` uses **AGP 9.2.1's built-in Kotlin (2.2.10)** — do NOT apply `org.jetbrains.kotlin.android` or any Kotlin plugin to `:app` (fails: "extension 'kotlin' already registered"). `src/main/kotlin` compiles automatically. (From the Plan 02 spike findings.)
- `:app` already requires **Java 17 + core-library desugaring** (Readium) — keep them. (From spike.)
- Package root: `com.nextz.kanomtts`; design system in `com.nextz.kanomtts.ui.theme`. — spec §3.2.
- Palette **"Anchan & Coconut"** exact tokens (spec §6.1): primary `#5D63D6`, primaryContainer `#EBECFB`, primary-gradient-end `#7B82F0`, **karaoke highlight `#FF5E9C`**, highlight bg `#FFE0EC`, highlight text `#9E2B5E`, background/coconut-cream `#FBF7EF`, surface `#FFFFFF`, ink/onSurface `#2B2A33`, muted `#A79E8E`. Rose `#FF5E9C` is reserved EXCLUSIVELY for the karaoke highlight.
- Typography (spec §6.2): **Itim** = brand/display/headlines; **Sarabun** = reading body + UI body (the default reading font, user-changeable later). Fallbacks: Noto Sans JP / SC (not bundled in this plan).
- Reader themes (spec §5.1, §6.1): Light / Dark / **Sepia** / **true-black** must exist as design tokens.
- All body/reading color pairs must meet **WCAG AA** contrast (spec §6.1).
- Min/compile/target unchanged: minSdk 26, compileSdk 36, targetSdk 36, AGP 9.2.1.
- Verification for this plan is primarily **`./gradlew :app:assembleDebug` compiling** plus Compose `@Preview`s; pure-logic helpers (reader-theme selection) get JVM unit tests where they don't touch Android types. Instrumented Compose UI tests are out of scope here (no device in CI) — noted as a later infra task.

---

## File Structure

```
gradle/libs.versions.toml          (modify: Compose BOM, material3, activity-compose, navigation-compose, hilt, lifecycle)
build.gradle.kts                   (modify: declare hilt + (if needed) compose-compiler plugins `apply false`)
app/build.gradle.kts               (modify: buildFeatures.compose, Compose+Hilt+nav deps, kapt/ksp for Hilt)
app/src/main/AndroidManifest.xml   (modify: Application name, single Activity theme)
app/src/main/kotlin/com/nextz/kanomtts/
    KanomApplication.kt            (Hilt @HiltAndroidApp)
    MainActivity.kt                (@AndroidEntryPoint, setContent { KanomTheme { KanomApp() } })
    ui/theme/Color.kt              (Anchan raw colors + light/dark ColorSchemes)
    ui/theme/KanomColors.kt        (extended colors: highlight + reader palettes + CompositionLocal)
    ui/theme/Type.kt               (Itim + Sarabun FontFamilies + Typography)
    ui/theme/Shape.kt              (sweet rounded shapes)
    ui/theme/Theme.kt             (KanomTheme entry point + brand @Preview)
    ui/navigation/KanomDestinations.kt   (route enum/sealed + nav items)
    ui/navigation/KanomApp.kt      (Scaffold + NavHost + KanomBottomBar)
    ui/screens/LibraryScreen.kt    (placeholder)
    ui/screens/NowPlayingScreen.kt (placeholder)
    ui/screens/SettingsScreen.kt   (placeholder)
app/src/main/res/font/             (itim_regular.ttf, sarabun_regular/medium/semibold/bold.ttf)
app/src/test/kotlin/com/nextz/kanomtts/ui/theme/ReaderThemeTest.kt  (pure-logic unit test)
```
Delete on the way: the old View-based `MainActivity` + `activity_main.xml` if the scaffold created them (the scaffold is currently code-free beyond the Readium spike probe, but verify). Keep `app/.../spike/ReadiumProbe.kt` (Plan 06 reference; it still compiles).

---

### Task 1: Enable Compose + Material 3 Expressive (toolchain canary)

Prove Compose builds under AGP 9.2.1's built-in Kotlin 2.2.10 and render a trivial composable. This is the canary — the exact mechanism to enable the Compose compiler with built-in Kotlin must be confirmed before any UI is written.

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create/replace: `app/src/main/kotlin/com/nextz/kanomtts/MainActivity.kt`

**Interfaces:**
- Produces: a Compose-enabled `:app` that assembles; catalog aliases for `androidx.compose` BOM, `androidx.compose.material3:material3`, `androidx.activity:activity-compose`, `androidx.compose.ui:ui-tooling`/`ui-tooling-preview`.

- [ ] **Step 1: Determine the correct Compose-enablement mechanism (research first)**

Under AGP 9.x built-in Kotlin, the Compose compiler is NOT enabled by an applied `kotlin.android` plugin (we don't apply one). Confirm the correct approach via context7 (`query-docs` for "AGP 9 built-in kotlin compose" / "compose compiler gradle plugin") and Android docs: most likely either (a) `android { buildFeatures { compose = true } }` is sufficient because built-in Kotlin bundles the Compose compiler, or (b) the `org.jetbrains.kotlin.plugin.compose` plugin must be applied (version matched to Kotlin 2.2.10) even with built-in Kotlin. Record which works in the report. Try (a) first (simplest); fall back to (b) if assembleDebug reports a missing Compose compiler.

- [ ] **Step 2: Add catalog entries**

Add to `gradle/libs.versions.toml` (verify current stable versions via Google Maven; Compose BOM should be a recent stable that includes **Material 3 1.4.x with Expressive**):
```toml
# [versions]
composeBom = "2025.06.01"   # verify latest stable that ships material3 1.4.x (expressive)
activityCompose = "1.10.1"
navigationCompose = "2.9.0"
hilt = "2.57"
lifecycle = "2.9.1"
# [libraries]
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
# [plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
# add compose-compiler plugin alias ONLY if Step 1 finds it is required
```

- [ ] **Step 3: Wire the build**

In `app/build.gradle.kts`: add `android { buildFeatures { compose = true } }`; add the Compose BOM via `implementation(platform(libs.androidx.compose.bom))` + the Compose deps + `debugImplementation(libs.androidx.compose.ui.tooling)`; keep Java 17 + desugaring + Readium. (Hilt deps + plugin are added in Task 5 to keep this task's canary minimal — OR add now if convenient.) If Step 1 determined the compose-compiler plugin is required, apply it here.

- [ ] **Step 4: Replace MainActivity with a Compose smoke**

Create `app/src/main/kotlin/com/nextz/kanomtts/MainActivity.kt`:
```kotlin
package com.nextz.kanomtts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Text("Kanom") }
            }
        }
    }
}
```
Update `AndroidManifest.xml` so the launcher Activity is this `MainActivity` (remove any old View layout reference). If the scaffold had an XML-based MainActivity/`activity_main.xml`, delete them.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If Compose compiler is missing, apply the `org.jetbrains.kotlin.plugin.compose` plugin (Step 1 fallback) and rebuild. Record the working mechanism.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/
git commit -m "build: enable Jetpack Compose + Material 3 (Expressive) in :app"
```

---

### Task 2: Anchan color system (app schemes + extended colors + reader palettes)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/ui/theme/KanomColors.kt`
- Create test: `app/src/test/kotlin/com/nextz/kanomtts/ui/theme/ReaderThemeTest.kt`

**Interfaces:**
- Produces: `LightColors`/`DarkColors` (`androidx.compose.material3.ColorScheme`); `KanomColors` (extended) with `highlight`, `highlightContainer`, `onHighlight`, `primaryGradientEnd`, `muted`; `enum class ReaderTheme { LIGHT, SEPIA, DARK, TRUE_BLACK }` with `fun ReaderTheme.surface(): Color` / `onSurface(): Color`; `LocalKanomColors` (a `staticCompositionLocalOf<KanomColors>`).

- [ ] **Step 1: Implement `Color.kt`**

```kotlin
package com.nextz.kanomtts.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Anchan & Coconut raw palette (spec §6.1) ---
val Anchan = Color(0xFF5D63D6)            // primary (butterfly-pea blue)
val AnchanContainer = Color(0xFFEBECFB)   // primary container / light tint
val AnchanGradientEnd = Color(0xFF7B82F0)
val RoseHighlight = Color(0xFFFF5E9C)     // karaoke highlight — RESERVED
val RoseHighlightBg = Color(0xFFFFE0EC)
val RoseHighlightInk = Color(0xFF9E2B5E)
val CoconutCream = Color(0xFFFBF7EF)      // background
val SurfaceWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF2B2A33)               // on-surface
val Muted = Color(0xFFA79E8E)
// reader surfaces
val SepiaBg = Color(0xFFF4ECD8)
val SepiaInk = Color(0xFF40382A)
val TrueBlack = Color(0xFF000000)
val DarkBg = Color(0xFF15141A)
val DarkInk = Color(0xFFEAE7F0)

val LightColors = lightColorScheme(
    primary = Anchan,
    onPrimary = Color.White,
    primaryContainer = AnchanContainer,
    onPrimaryContainer = Color(0xFF1B1B4D),
    secondary = Color(0xFF8A8FF0),
    onSecondary = Color.White,
    background = CoconutCream,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = AnchanContainer,
    onSurfaceVariant = Color(0xFF565260),
    outline = Color(0xFFCFC8BB),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB9BCFF),
    onPrimary = Color(0xFF222657),
    primaryContainer = Color(0xFF3D4290),
    onPrimaryContainer = AnchanContainer,
    secondary = Color(0xFFB9BCFF),
    onSecondary = Color(0xFF222657),
    background = DarkBg,
    onBackground = DarkInk,
    surface = Color(0xFF1E1D26),
    onSurface = DarkInk,
    surfaceVariant = Color(0xFF45424F),
    onSurfaceVariant = Color(0xFFC9C5D4),
    outline = Color(0xFF938F9E),
)
```

- [ ] **Step 2: Implement `KanomColors.kt`**

```kotlin
package com.nextz.kanomtts.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand colors not covered by Material's ColorScheme. */
data class KanomColors(
    val highlight: Color,
    val highlightContainer: Color,
    val onHighlight: Color,
    val primaryGradientEnd: Color,
    val muted: Color,
)

val LightKanomColors = KanomColors(
    highlight = RoseHighlight,
    highlightContainer = RoseHighlightBg,
    onHighlight = RoseHighlightInk,
    primaryGradientEnd = AnchanGradientEnd,
    muted = Muted,
)

val DarkKanomColors = LightKanomColors.copy(
    highlightContainer = Color(0xFF5A2740),
    onHighlight = Color(0xFFFFD4E6),
    muted = Color(0xFF9A93A6),
)

val LocalKanomColors = staticCompositionLocalOf { LightKanomColors }

/** Reading surface themes (spec §5.1). Pure data — JVM testable. */
enum class ReaderTheme { LIGHT, SEPIA, DARK, TRUE_BLACK }

fun ReaderTheme.surface(): Color = when (this) {
    ReaderTheme.LIGHT -> CoconutCream
    ReaderTheme.SEPIA -> SepiaBg
    ReaderTheme.DARK -> DarkBg
    ReaderTheme.TRUE_BLACK -> TrueBlack
}

fun ReaderTheme.onSurface(): Color = when (this) {
    ReaderTheme.LIGHT -> Ink
    ReaderTheme.SEPIA -> SepiaInk
    ReaderTheme.DARK -> DarkInk
    ReaderTheme.TRUE_BLACK -> DarkInk
}
```

- [ ] **Step 2b: Write the failing unit test (pure logic, no Android UI runtime)**

`ReaderThemeTest.kt` — `Color` is an inline value class over a `Long`; comparing `.value`/equality works on the JVM without Robolectric for these constants:
```kotlin
package com.nextz.kanomtts.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderThemeTest {
    @Test fun every_reader_theme_maps_to_surface_and_ink() {
        ReaderTheme.values().forEach { t ->
            assertNotEquals("surface must differ from ink for $t", t.surface(), t.onSurface())
        }
    }
    @Test fun true_black_surface_is_pure_black() {
        assertEquals(TrueBlack, ReaderTheme.TRUE_BLACK.surface())
    }
    @Test fun light_reader_surface_is_coconut_cream() {
        assertEquals(CoconutCream, ReaderTheme.LIGHT.surface())
    }
}
```
Run `./gradlew :app:testDebugUnitTest --tests "*ReaderThemeTest"` → FAIL (unresolved) before Step 1/2, PASS after. (If `Color` constants require an Android runtime in this toolchain, the implementer may convert these to assert the underlying `ULong` `.value` instead — keep the assertions meaningful.)

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "*ReaderThemeTest"`
Expected: BUILD SUCCESSFUL + tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src
git commit -m "feat(ui): Anchan color schemes + extended colors + reader palettes"
```

---

### Task 3: Typography — bundle Itim + Sarabun

**Files:**
- Create: `app/src/main/res/font/itim_regular.ttf`, `sarabun_regular.ttf`, `sarabun_medium.ttf`, `sarabun_semibold.ttf`, `sarabun_bold.ttf`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/ui/theme/Type.kt`

**Interfaces:**
- Produces: `Itim` and `Sarabun` `FontFamily`; `KanomTypography` (`androidx.compose.material3.Typography`) — Itim on display/headline roles, Sarabun on title/body/label roles.

- [ ] **Step 1: Fetch the font files**

Download the TTFs from the Google Fonts repo (OFL-licensed; redistribution allowed). Use the static instances:
- Itim: `https://github.com/google/fonts/raw/main/ofl/itim/Itim-Regular.ttf` → `itim_regular.ttf`
- Sarabun: from `https://github.com/google/fonts/tree/main/ofl/sarabun` → `Sarabun-Regular.ttf`, `Sarabun-Medium.ttf`, `Sarabun-SemiBold.ttf`, `Sarabun-Bold.ttf` → snake_case names above.
Place them in `app/src/main/res/font/` (lowercase, digits/underscore only — Android resource naming). Verify each file is a valid non-empty TTF (size > 10KB).

- [ ] **Step 2: Implement `Type.kt`**

```kotlin
package com.nextz.kanomtts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nextz.kanomtts.R

val Itim = FontFamily(Font(R.font.itim_regular, FontWeight.Normal))

val Sarabun = FontFamily(
    Font(R.font.sarabun_regular, FontWeight.Normal),
    Font(R.font.sarabun_medium, FontWeight.Medium),
    Font(R.font.sarabun_semibold, FontWeight.SemiBold),
    Font(R.font.sarabun_bold, FontWeight.Bold),
)

private val base = Typography()

/** Itim = brand/display/headline; Sarabun = titles/body/labels (reading default). */
val KanomTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Itim),
    displayMedium = base.displayMedium.copy(fontFamily = Itim),
    displaySmall = base.displaySmall.copy(fontFamily = Itim),
    headlineLarge = base.headlineLarge.copy(fontFamily = Itim),
    headlineMedium = base.headlineMedium.copy(fontFamily = Itim),
    headlineSmall = base.headlineSmall.copy(fontFamily = Itim),
    titleLarge = base.titleLarge.copy(fontFamily = Sarabun, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Sarabun, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Sarabun),
    bodyMedium = base.bodyMedium.copy(fontFamily = Sarabun),
    bodySmall = base.bodySmall.copy(fontFamily = Sarabun),
    labelLarge = base.labelLarge.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
)
```

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (fonts resolve via `R.font.*`; resource names valid).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/font app/src/main/kotlin/com/nextz/kanomtts/ui/theme/Type.kt
git commit -m "feat(ui): bundle Itim + Sarabun and define Kanom typography"
```

---

### Task 4: Shapes + KanomTheme assembly + brand preview

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/ui/theme/Shape.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: Tasks 2–3.
- Produces: `KanomShapes` (`androidx.compose.material3.Shapes`); `@Composable fun KanomTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` that provides the right `ColorScheme`, `KanomTypography`, `KanomShapes`, and `LocalKanomColors`; an `object KanomTheme { val colors: KanomColors @Composable get() = LocalKanomColors.current }` accessor; a brand `@Preview`.

- [ ] **Step 1: Implement `Shape.kt`**

```kotlin
package com.nextz.kanomtts.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// "Sweet" generously-rounded shapes.
val KanomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
```

- [ ] **Step 2: Implement `Theme.kt`**

```kotlin
package com.nextz.kanomtts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
```

- [ ] **Step 3: Add a brand preview (in `Theme.kt` or a `_Preview.kt`)**

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
```

- [ ] **Step 4: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (preview composables compile; `@Preview` requires `ui-tooling-preview` on `implementation` and `ui-tooling` on `debugImplementation` — ensure both from Task 1).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/ui/theme
git commit -m "feat(ui): Kanom shapes + KanomTheme + brand preview"
```

---

### Task 5: Hilt + Navigation scaffold (Library / Now Playing / Settings)

**Files:**
- Modify: `gradle/libs.versions.toml` (if Hilt not added in Task 1), `build.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`
- Create: `KanomApplication.kt`, update `MainActivity.kt`
- Create: `ui/navigation/KanomDestinations.kt`, `ui/navigation/KanomApp.kt`
- Create: `ui/screens/LibraryScreen.kt`, `NowPlayingScreen.kt`, `SettingsScreen.kt`

**Interfaces:**
- Consumes: `KanomTheme` (Task 4).
- Produces: `@HiltAndroidApp class KanomApplication`; `enum class KanomDestination(route, labelRes-or-label, icon)` with the three entries; `@Composable fun KanomApp()` = `Scaffold(bottomBar = { KanomBottomBar(...) }) { NavHost(...) }`; three placeholder screen composables.

- [ ] **Step 1: Add Hilt to the build**

Apply the Hilt Gradle plugin. Hilt needs an annotation processor — under AGP 9 built-in Kotlin use **KSP** if available, else `kapt`. Confirm the right processor mechanism for built-in Kotlin via context7/docs (KSP plugin `com.google.devtools.ksp` matched to Kotlin 2.2.10 is preferred; `hilt-android-compiler` via `ksp(...)`). In root `build.gradle.kts` declare the hilt (+ ksp if used) plugins `apply false`; in `app/build.gradle.kts` apply them and add `implementation(libs.hilt.android)` + `ksp(libs.hilt.compiler)`. Record the working processor mechanism in the report.

- [ ] **Step 2: `KanomApplication.kt` + manifest**

```kotlin
package com.nextz.kanomtts

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KanomApplication : Application()
```
In `AndroidManifest.xml` set `android:name=".KanomApplication"` on `<application>` and keep `MainActivity` as the launcher.

- [ ] **Step 3: `KanomDestinations.kt`**

```kotlin
package com.nextz.kanomtts.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class KanomDestination(val route: String, val label: String, val icon: ImageVector) {
    LIBRARY("library", "คลัง", Icons.Outlined.LibraryBooks),
    NOW_PLAYING("now_playing", "กำลังฟัง", Icons.Outlined.PlayCircle),
    SETTINGS("settings", "ตั้งค่า", Icons.Outlined.Settings),
}
```
(Add `implementation("androidx.compose.material:material-icons-extended")` via the BOM if the chosen icons aren't in the core set — verify; otherwise use core `Icons.Filled.*` equivalents to avoid the extra dependency.)

- [ ] **Step 4: `KanomApp.kt` (Scaffold + NavHost + bottom bar)**

```kotlin
package com.nextz.kanomtts.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nextz.kanomtts.ui.screens.LibraryScreen
import com.nextz.kanomtts.ui.screens.NowPlayingScreen
import com.nextz.kanomtts.ui.screens.SettingsScreen

@Composable
fun KanomApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                KanomDestination.values().forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(KanomDestination.LIBRARY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = KanomDestination.LIBRARY.route,
            modifier = Modifier,
        ) {
            composable(KanomDestination.LIBRARY.route) { LibraryScreen(Modifier, padding) }
            composable(KanomDestination.NOW_PLAYING.route) { NowPlayingScreen(Modifier, padding) }
            composable(KanomDestination.SETTINGS.route) { SettingsScreen(Modifier, padding) }
        }
    }
}
```

- [ ] **Step 5: Placeholder screens**

Each of `LibraryScreen.kt`, `NowPlayingScreen.kt`, `SettingsScreen.kt` (package `com.nextz.kanomtts.ui.screens`) — same shape, differing label:
```kotlin
package com.nextz.kanomtts.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LibraryScreen(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        Text("คลัง · Library", style = MaterialTheme.typography.headlineSmall)
    }
}
```
(Repeat for `NowPlayingScreen` → "กำลังเล่น · Now Playing", `SettingsScreen` → "ตั้งค่า · Settings".)

- [ ] **Step 6: Wire MainActivity to the scaffold**

```kotlin
package com.nextz.kanomtts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nextz.kanomtts.ui.navigation.KanomApp
import com.nextz.kanomtts.ui.theme.KanomTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KanomTheme { KanomApp() } }
    }
}
```

- [ ] **Step 7: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL — Hilt processes `@HiltAndroidApp`/`@AndroidEntryPoint`, Compose nav compiles, three destinations wired.

- [ ] **Step 8: Commit**

```bash
git add app/ gradle/libs.versions.toml build.gradle.kts
git commit -m "feat(app): Hilt + Compose Navigation scaffold with Kanom bottom bar"
```

---

## Self-Review

**1. Spec coverage:**
- Compose + Material 3 Expressive (spec §3.1, Decision C) → Task 1. ✓
- Anchan palette exact tokens + reserved rose highlight (spec §6.1) → Task 2. ✓
- Light/Dark + Sepia/true-black reader themes (spec §5.1, §6.1) → Task 2 `ReaderTheme`. ✓
- Itim + Sarabun typography (spec §6.2) → Task 3. ✓
- Sweet rounded shapes / brand feel (spec §6.4) → Task 4. ✓
- Three-screen bottom nav (Library/Now Playing/Settings) matching the approved mockups (spec §6.3) → Task 5. ✓
- Hilt DI (spec §3.1) → Task 5. ✓
- *Deferred (correct):* M3 Expressive motion/microinteractions polish (spec §6.4) lands with real screens in feature plans; dynamic-color toggle, mini-player, and accessibility specifics (TalkBack labels beyond contentDescription) come with their screens; instrumented Compose UI tests need device infra (later).

**2. Placeholder scan:** Version numbers in Task 1/5 are marked "verify current via Google Maven/context7," not shipped blind — each is followed by a build gate. No TODO/TBD. Every code step shows complete code. ✓

**3. Type consistency:** `KanomTheme` (composable + accessor object) , `KanomColors`, `LocalKanomColors`, `ReaderTheme`, `KanomTypography`, `KanomShapes`, `KanomDestination`, `KanomApp`, screen composables are defined once and referenced consistently. The `KanomTheme` name is intentionally both a `@Composable fun` and an `object` (standard Compose M3 pattern, e.g. `MaterialTheme`) — Kotlin allows a function and an object of the same name in the same package; if the toolchain objects, the implementer renames the accessor object to `KanomThemeTokens` and updates references (note in report). ✓

**Note for executor:** Tasks 1 and 5 each contain a small build-mechanism unknown for AGP 9.2.1 built-in Kotlin (Compose compiler enablement; Hilt annotation processor = KSP vs kapt). Treat the first build of each as a canary: research the correct mechanism, apply the minimal config, and record what worked in the report — do not thrash. If a mechanism genuinely doesn't work, report BLOCKED with the exact error rather than forcing it.
