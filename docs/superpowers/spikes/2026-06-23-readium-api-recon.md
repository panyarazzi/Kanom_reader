# Readium 3.3.0 API Recon — Plan 06 (Reader)

- **Date:** 2026-06-23
- **Plan:** 06 (Reader) — planning-phase API recon
- **Purpose:** The Plan 02 spike verified the open/content/TTS-navigator/tokenizer APIs but deliberately **not** the render-fragment, `EpubPreferences`, `Decoration`, font-registration, or resource-transform APIs. This doc consolidates verified signatures (6 parallel recon agents → cross-checked synthesis) so Plan 06's tasks reference real APIs, not guesses. `readyToPlan: true`.

All call sites in the reader module carry `@OptIn(ExperimentalReadiumApi::class)` (import `org.readium.r2.shared.ExperimentalReadiumApi`). Extra opt-ins (`@DelicateReadiumApi`, `@InternalReadiumApi`) are called out per API.

---

## 1. Hosting / Fragment (HIGH)

```kotlin
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.ExperimentalReadiumApi
```

```kotlin
public class EpubNavigatorFactory(
    private val publication: Publication,
    private val configuration: Configuration = Configuration(),   // FACTORY Configuration (small)
) {
    public data class Configuration(val defaults: EpubDefaults = EpubDefaults())
    public fun createFragmentFactory(
        initialLocator: Locator?,
        readingOrder: List<Link>? = null,
        initialPreferences: EpubPreferences = EpubPreferences(),
        listener: EpubNavigatorFragment.Listener? = null,
        paginationListener: EpubNavigatorFragment.PaginationListener? = null,
        configuration: EpubNavigatorFragment.Configuration = EpubNavigatorFragment.Configuration(),
    ): FragmentFactory
    public fun createPreferencesEditor(currentPreferences: EpubPreferences): EpubPreferencesEditor
}
```

- **`EpubNavigatorFragment.Listener`** — only `onExternalLinkActivated(url: AbsoluteUrl)` has **no default** (host MUST implement). `onResourceLoadFailed(href: Url, error: ReadError)`, `onJumpToLocator`, `shouldFollowInternalLink` all default.
- **`EpubNavigatorFragment`** has an **internal constructor** — only the `FragmentFactory` instantiates it. `companion.createDummyFactory()` is for process-death restore.
- Navigation: `val currentLocator: StateFlow<Locator>`; `go(locator, animated): Boolean`; `go(link, animated): Boolean`; `goForward/goBackward(animated)`.
- **Host pattern:** set `fragmentManager.fragmentFactory = factory` **BEFORE** `super.onCreate()`, then `commit { add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag) }`. In Compose: `AndroidView { FragmentContainerView(it).apply { id = <stable id> } }`, commit only when first composed.
- **ToC is NOT suspend:** `val tableOfContents: List<Link> get() = manifest.tableOfContents` (a plain `val` — do **not** write `tableOfContents()`); `fun locatorFromLink(link): Locator?` (not suspend). Prefer `navigator.go(tocLink)` directly.

## 2. Preferences (HIGH)

```kotlin
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.navigator.preferences.Theme        // LIGHT, DARK, SEPIA — NO true-black/AMOLED
import org.readium.r2.navigator.preferences.Color         // value class Color(@ColorInt val int: Int)
import org.readium.r2.navigator.preferences.FontFamily     // value class FontFamily(val name: String); .SERIF/.SANS_SERIF/...
import org.readium.r2.navigator.preferences.TextAlign
```

`EpubPreferences` (ctor is `@ExperimentalReadiumApi`) fields used by Plan 06: `theme: Theme?`, `backgroundColor: Color?`, `textColor: Color?`, `fontFamily: FontFamily?`, `fontSize: Double?` (ratio, 1.0=100%), `lineHeight: Double?` (ratio), `pageMargins: Double?` (ratio), `paragraphSpacing: Double?`, `publisherStyles: Boolean?`, `language: Language?`, `scroll: Boolean?`. Merge with `+`.

**Live apply:** `EpubNavigatorFragment` implements `Configurable<EpubSettings, EpubPreferences>`:
```kotlin
fun submitPreferences(preferences: EpubPreferences)   // fire-and-forget, NOT suspend
val settings: StateFlow<EpubSettings>
```
**Editor (optional UI helper):** `factory.createPreferencesEditor(current)` → `EpubPreferencesEditor` with `Preference<T>`/`RangePreference<T>`/`EnumPreference<T>` per setting (`.set(v)`, `.clear()`, `.supportedRange`, `.supportedValues`).

**Load-bearing rules:**
- **`publisherStyles=false` GATE:** `fontSize` (beyond base), `lineHeight`, `paragraphSpacing`, `paragraphIndent`, `letterSpacing`, `wordSpacing`, `typeScale`, `textAlign`, `hyphens`, `ligatures` require `publisherStyles=false` to take effect. **NOT gated:** `theme`, `scroll`, `columnCount`, `fontFamily`, `textColor`, `backgroundColor`.
- **Pure black:** no AMOLED enum — use `theme=Theme.DARK` + `backgroundColor=Color(BLACK)` (+ a `textColor`).
- Forced palette that wins over publisher CSS: `textColor + backgroundColor + publisherStyles=false` together.

## 3. Decoration (HIGH)

```kotlin
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration                  // typealias DecorationId = String
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.navigator.html.HtmlDecorationTemplate
```

```kotlin
public interface DecorableNavigator : Navigator {
    public suspend fun applyDecorations(decorations: List<Decoration>, group: String)   // SUSPEND
    public fun <T : Decoration.Style> supportsDecorationStyle(style: KClass<T>): Boolean
    public fun addDecorationListener(group: String, listener: Listener)
}
@Parcelize public data class Decoration(
    val id: DecorationId, val locator: Locator, val style: Style, val extras: Map<String, Any> = mapOf(),
) : JSONable, Parcelable {
    public interface Style : Parcelable
    @Parcelize public data class Highlight(@ColorInt override val tint: Int, override val isActive: Boolean = false) : Style, Tinted, Activable
    @Parcelize public data class Underline(@ColorInt override val tint: Int, override val isActive: Boolean = false) : Style, Tinted, Activable
}
```

- `tint` is `@ColorInt Int` (use `composeColor.toArgb()` or `android.graphics.Color.parseColor("#FF5E9C")`), NOT a Compose `Color`.
- **Group semantics (load-bearing):** `applyDecorations(list, group)` declares the ENTIRE current state of that group; Readium diffs vs the previous list. A single-element list REPLACES the prior highlight. Clear with `applyDecorations(emptyList(), group)`. Distinct groups (`"bookmarks"`, `"tts-sentence"`) are independent.
- `applyDecorations` is **suspend** → call from `lifecycleScope`. Apply only after the first locator/page-loaded callback (not in `onViewCreated`).
- **Custom templates** registered in `EpubNavigatorFragment.Configuration { decorationTemplates = HtmlDecorationTemplates.defaultTemplates().apply { set(MyStyle::class, HtmlDecorationTemplate.highlight(...)) } }` — **before** fragment creation; no runtime add. `supportsDecorationStyle` is true only for registered styles. Custom styles = your own `Decoration.Style` (Parcelable), implement `Tinted`/`Activable` to reuse tint/active CSS.

## 4. Fonts — bundled Sarabun (HIGH)

```kotlin
import org.readium.r2.navigator.preferences.FontFamily               // navigator.preferences (NOT navigator.web.common)
import org.readium.r2.navigator.epub.css.FontStyle                   // epub.css package
import org.readium.r2.navigator.epub.css.FontWeight
```
Build `EpubNavigatorFragment.Configuration` via the **DSL** (ctor is internal):
```kotlin
val configuration = EpubNavigatorFragment.Configuration {
    servedAssets += "fonts/.*"            // MANDATORY — authorizes WebView to load assets/fonts/*
    addFontFamilyDeclaration(FontFamily("Sarabun")) {
        addFontFace { addSource("fonts/sarabun_regular.ttf");  setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.NORMAL) }
        addFontFace { addSource("fonts/sarabun_medium.ttf");   setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.MEDIUM) }
        addFontFace { addSource("fonts/sarabun_semibold.ttf"); setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.SEMI_BOLD) }
        addFontFace { addSource("fonts/sarabun_bold.ttf");     setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.BOLD) }
    }
}
navigator.submitPreferences(EpubPreferences(fontFamily = FontFamily("Sarabun")))   // SAME name string
```
**Load-bearing rules:**
- `servedAssets += "fonts/.*"` is MANDATORY (without it the `@font-face src` 404s and silently falls back; fragment only auto-adds `"readium/.*"`).
- TTFs MUST live under **`app/src/main/assets/fonts/`** (served at `https://readium/assets/fonts/...`). `res/font` is Compose-only, unreachable by the WebView — a **separate `assets/` copy** is required.
- The `FontFamily("Sarabun")` string in the declaration must EXACTLY equal the one in `EpubPreferences.fontFamily`.
- `fontFamily` not in the publisherStyles gate, but EPUBs often hard-set font-family → **fallback:** `publisherStyles=false` to force.
- Sarabun is not variable → discrete `FontWeight` enum, one `addFontFace` per weight.

## 5. Content → Locator (HIGH; AttributesHolder import MEDIUM)

```kotlin
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.Locator
```
```kotlin
public data class TextElement(override val locator: Locator, val role: Role, val segments: List<Segment>, ...) : TextualElement {
    override val text: String get() = segments.joinToString("") { it.text }
}
public data class Segment(val locator: Locator, val text: String, override val attributes: List<Attribute<*>>) : AttributesHolder
// Segment.language: Language? is inherited from AttributesHolder (attribute(AttributeKey.LANGUAGE)) — NOT a ctor field.

public data class Locator(val href: Url, val mediaType: MediaType, val title: String? = null,
                          val locations: Locations = Locations(), val text: Text = Text()) : JSONable, Parcelable {
    override fun toJSON(): JSONObject
    companion object { fun fromJSON(json: JSONObject?, warnings: WarningLogger? = null): Locator? }
    data class Locations(val fragments: List<String> = emptyList(), val progression: Double? = null,
                         val position: Int? = null, val totalProgression: Double? = null, ...)   // totalProgression = 0..1 book progress
    data class Text(val before: String? = null, val highlight: String? = null, val after: String? = null) {
        fun substring(range: IntRange): Text
    }
}
```
- Build a sub-sentence decorable locator: `el.locator.copy(text = el.locator.text.substring(range))` or `loc.copy(text = loc.text.copy(highlight = sentence))`.
- Bookmark capture: `navigator.currentLocator.value` — its `.text.highlight` is the on-screen snippet; persist `.toJSON().toString()`; restore via `navigator.go(Locator.fromJSON(JSONObject(json))!!)`.
- **No `@ExperimentalReadiumApi` on `Locator`/`Decoration`/`DecorableNavigator`/`currentLocator`** — only the `Content` service is experimental.

## 6. Thai word-break transform (HIGH mechanism; wiring seam partially unresolved)

- **CSS/preferences: NOT viable.** No word-break/line-break field on `EpubPreferences`; readium-CSS `overrides: Map<String,String?>` injects CSS **variables only** (cannot add `:lang(th){line-break:auto}`). Blink does not dictionary-break Thai from `lang` alone.
- **JS-injection: NOT cleanly supported** (`Configuration.javascriptInterfaces` registers a named Java↔JS bridge, not script injection).
- **Resource-transform: TOP, cleanly supported:**
```kotlin
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.map                    // Resource.map
import org.readium.r2.shared.util.resource.TransformingContainer  // typealias EntryTransformer = (Url, Resource) -> Resource
import org.readium.r2.shared.util.Try

val transformer: (Url, Resource) -> Resource = { url, res ->
    val s = url.toString()
    if (s.endsWith(".xhtml") || s.endsWith(".html"))
        res.map { bytes -> Try.success(insertThaiZwsp(bytes.decodeToString()).encodeToByteArray()) }
    else res
}
@OptIn(InternalReadiumApi::class)   // container param is @InternalReadiumApi
val displayPublication = Publication(
    manifest = publication.manifest,
    container = TransformingContainer(publication.container, transformer),
    servicesBuilder = /* see unresolved */,
)
```
- `insertThaiZwsp` parses XHTML (jsoup XML mode), walks text nodes only (skip tags/attrs/script/style), runs the ICU Thai word tokenizer per Thai run, inserts `U+200B` at each `IntRange` boundary.
- **Keep an un-transformed publication (or strip U+200B) for the TTS/`Content` pipeline (Plan 07)** — injected ZWSP otherwise pollutes content text extraction. Maintain two `Publication` instances (display-transformed vs TTS-original).
- Still set `EpubPreferences.language = Language("th")` for Thai font selection.
- **Fallback** if the `@InternalReadiumApi` `Publication(container=...)` rebuild is undesirable: wrap the container at parse time. Both paths use the same `TransformingContainer` + `EntryTransformer`.

## 7. Reconciled contradictions
1. **`DecorableNavigator` opt-in name** varies across findings (`@ExperimentalReadiumApi` vs none vs `@ExperimentalDecorator`). Module-wide `@OptIn(ExperimentalReadiumApi::class)` makes calls compile regardless; verify the exact annotation against `DecorableNavigator.kt` at integration (don't assume `@ExperimentalDecorator` exists).
2. **Two `Configuration` types:** `EpubNavigatorFactory.Configuration` (small: `defaults: EpubDefaults`) vs `EpubNavigatorFragment.Configuration` (large, internal ctor, **DSL-built**). Don't conflate.
3. **`EpubNavigatorFragment.Configuration` construction:** use the **DSL** form `EpubNavigatorFragment.Configuration { ... }` (required for `addFontFamilyDeclaration` which writes internal fields); positional-ctor snippets are illustrative only.

## 8. Unresolved → inline spikes / device checks for Plan 06
1. Exact `DecorableNavigator` opt-in annotation (verify vs source; module-wide opt-in covers it).
2. Font DSL method names byte-verified by `fonts` agent (HIGH) but flagged MEDIUM by `preferences` agent — quick compile check before implementing Sarabun.
3. `AttributesHolder`/`AttributeKey.LANGUAGE` import package (IDE auto-import).
4. First-`applyDecorations` timing vs fragment lifecycle — apply on first locator/page-loaded callback; verify empirically.
5. Thai-transform wiring: parse-time container wrap vs `@InternalReadiumApi` `Publication(container=...)` rebuild + re-supplying `ServicesBuilder`. Fallback identified; does not block planning.
6. Device smoke test: U+200B actually breaks Thai on target-minSdk WebView.
7. Compose embedding: stable `FragmentContainerView` id survives recomposition/config-change; `createDummyFactory()` restore after process death.
8. Exact `ReadError` import package (IDE auto-import).
9. `EpubPreferencesEditor` numeric `supportedRange` bounds — read at runtime, don't hardcode.
