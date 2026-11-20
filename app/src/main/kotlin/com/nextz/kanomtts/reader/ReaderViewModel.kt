package com.nextz.kanomtts.reader

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import com.nextz.kanomtts.data.repository.BookmarkStore
import com.nextz.kanomtts.data.repository.ReplacementRuleRepository
import com.nextz.kanomtts.BuildConfig
import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.text.replacement.ReplacementEngine
import com.nextz.kanomtts.playback.TtsPlaybackSource
import com.nextz.kanomtts.reader.engine.EpubPreferencesMapper
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import com.nextz.kanomtts.ui.theme.RoseHighlight
import com.nextz.kanomtts.ui.theme.ReaderTheme
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.AbsoluteUrl
import javax.inject.Inject

@OptIn(ExperimentalReadiumApi::class, DelicateReadiumApi::class, FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val source: ReaderBookSource,
    private val prefs: ReaderPreferences,
    private val bookmarkStore: BookmarkStore,
    private val replacementRuleRepository: ReplacementRuleRepository,
    private val ttsController: TtsPlaybackSource,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId").orEmpty()

    /** Argb int of KanomColors.highlight (RoseHighlight), used for bookmark decorations. */
    private val roseArgb: Int = RoseHighlight.toArgb()

    /** Owns all navigator-decoration concerns (bookmarks, tts highlight, probe). */
    private val decorations = ReaderDecorationController(viewModelScope, roseArgb)

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _quickAddText = MutableStateFlow<String?>(null)
    val quickAddText: StateFlow<String?> = _quickAddText.asStateFlow()
    fun dismissQuickAdd() { _quickAddText.value = null }

    /** Save a replacement rule from the selection. [toThisBook] false = global (default), true = this book. */
    fun addReplacement(pattern: String, replacement: String, isRegex: Boolean, toThisBook: Boolean, showInReader: Boolean = false) {
        if (com.nextz.kanomtts.feature.replacement.ReplacementRuleValidation.validate(pattern, isRegex) != null) return
        viewModelScope.launch {
            replacementRuleRepository.add(
                com.nextz.kanomtts.data.db.ReplacementRuleEntity(
                    scope = if (toThisBook) "book" else "global",
                    bookId = if (toThisBook) bookId else null,
                    pattern = pattern, replacement = replacement, isRegex = isRegex,
                    enabled = true, position = -1, showInReader = showInReader,
                )
            )
            _quickAddText.value = null
            toast(R.string.toast_replacement_added)
        }
    }

    /** True when TTS is actively playing; drives the chrome play/pause icon. */
    val ttsPlaying: StateFlow<Boolean> = ttsController.state
        .map { it is PlaybackState.Playing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var publicationToClose: Publication? = null

    /**
     * Snapshot of the visual replacement engine applied at book-open time.
     * Set by [load] and read by Task 6 (highlight coordinate remapping).
     * Null when visualReplacement is off or there are no showInReader rules.
     */
    private var visualEngine: ReplacementEngine? = null

    private var navigatorBound = false
    private var navigator: EpubNavigatorFragment? = null

    /** Tracks the per-bind navigator.currentLocator collector so unbind can cancel it (avoid leak + double-handling on rebind). */
    private var navLocatorJob: Job? = null

    /** Held for removal in [onCleared]; registered first → consumes edge taps. */
    private var edgeAdapter: DirectionalNavigationAdapter? = null

    /** Held for removal in [onCleared]; registered second → receives center taps + drag. */
    private var navListener: ReaderNavInputListener? = null

    /** Mirrors KanomPreferences.pageAnimation; updated by the prefs collector in init. */
    private var animatePageTurns: Boolean = true

    /** Mirrors KanomPreferences.highlightOn; updated by the prefs collector in init. */
    private var highlightOn: Boolean = true

    /** Mirrors KanomPreferences.autoScroll; updated by the prefs collector in init. */
    private var autoScroll: Boolean = true

    /** Mirrors KanomPreferences.thaiWordBreak; the display DOM has ZWSP injected when on, so the
     *  TTS highlight quote must carry matching ZWSP for Readium to locate it. */
    private var thaiWordBreak: Boolean = true

    /** Buffer up to 1 pending locator; oldest dropped so tryEmit always keeps the latest value. */
    private val locatorChanges = MutableSharedFlow<Locator>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        load()
        // Debounce pipeline runs independently of load(); wires the save once navigator emits.
        // Uses partial-column update (saveReadingLocator) so the TTS locator is never
        // clobbered by a concurrent read-modify-write from this side. (I2)
        viewModelScope.launch {
            locatorChanges.debounce(800).collect { loc ->
                source.saveReadingLocator(
                    bookId = bookId,
                    locator = LocatorSerializer.encode(loc),
                    percent = (loc.locations.totalProgression ?: 0.0).toFloat(),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        // Live-apply preferences: each update submits to the navigator once it is bound,
        // and mirrors the latest KanomPreferences into Ready.prefs for the settings sheet UI.
        viewModelScope.launch {
            prefs.preferences.collect { p ->
                animatePageTurns = p.pageAnimation
                highlightOn = p.highlightOn
                // Task-9 follow-up: clear stale TTS highlight immediately when the user
                // turns highlight off so a rose highlight does not persist mid-pause.
                if (!p.highlightOn) decorations.applyTts(null)
                autoScroll = p.autoScroll
                thaiWordBreak = p.thaiWordBreak
                navigator?.submitPreferences(EpubPreferencesMapper.map(p))
                _uiState.update { s -> if (s is ReaderUiState.Ready) s.copy(prefs = p) else s }
                applyPublisherLineHeight()
            }
        }
        // Collect bookmarks and keep Ready.bookmarks up-to-date + push Decoration group.
        viewModelScope.launch {
            bookmarkStore.observe(bookId).collect { bookmarks ->
                _uiState.update { s ->
                    if (s is ReaderUiState.Ready) s.copy(bookmarks = bookmarks) else s
                }
                decorations.applyBookmarks(bookmarks)
            }
        }
        // Speech-driven sentence highlight + auto-scroll.
        // When highlightOn: apply/clear the "tts" decoration group to follow the spoken locator.
        // When autoScroll: scroll the page so the current sentence stays in view.
        // VisualHighlight.transformQuote rewrites the quote so Readium matches the highlight against
        // the displayed DOM text: visual-replacement text when on, and U+200B word breaks when
        // thaiWordBreak is on. Without the latter, a sentence spanning a page break fails the text
        // match and the page does not turn (navigation falls back to the paragraph start).
        viewModelScope.launch {
            ttsController.currentLocator.collect { loc ->
                val shown = VisualHighlight.transformQuote(loc, visualEngine, thaiWordBreak)
                if (highlightOn) decorations.applyTts(shown) else decorations.applyTts(null)
                if (autoScroll && loc != null) {
                    navigator?.takeIf { it.view != null }?.go(shown ?: loc, animated = false)
                }
            }
        }
    }

    private fun load() = viewModelScope.launch {
        val book = source.book(bookId)
        if (book == null) { _uiState.value = ReaderUiState.Error("Book not found"); return@launch }

        // Read prefs first so thaiWordBreak toggle is available before opening the display publication.
        val initialPrefs = prefs.preferences.first()

        // Snapshot visual replacement rules at open time (apply-on-next-open).
        // Only fetch when the pref is on; empty list = no injection (today's behaviour).
        val visualRules = if (initialPrefs.visualReplacement)
            replacementRuleRepository.observeVisualEffective(bookId).first() else emptyList()
        visualEngine = visualRules.takeIf { it.isNotEmpty() }?.let { ReplacementEngine(it) }

        // Display publication: optionally wraps content with replacement + Thai ZWSP injection.
        // The TTS/content path (Plan 07) will use source.open() for an un-transformed publication.
        val publication = source.openForDisplay(book.uri, initialPrefs.thaiWordBreak, visualRules)
        if (publication == null) { _uiState.value = ReaderUiState.Error("Could not open this EPUB"); return@launch }
        publicationToClose = publication

        val initialLocator = LocatorSerializer.decode(source.progress(bookId)?.locator)

        // No-op listener: external link handling is out of scope for Task 3.
        val listener = object : EpubNavigatorFragment.Listener {
            override fun onExternalLinkActivated(url: AbsoluteUrl) {}
        }

        // Register Sarabun font faces served from app/src/main/assets/fonts/.
        // servedAssets += "fonts/.*" is MANDATORY — without it the @font-face src 404s silently.
        val config = EpubNavigatorFragment.Configuration {
            // WebView textZoom handles EPUBs whose CSS uses fixed font sizes more reliably.
            useReadiumCssFontSize = false
            servedAssets += "fonts/.*"
            addFontFamilyDeclaration(FontFamily("Sarabun")) {
                addFontFace {
                    addSource("fonts/sarabun_regular.ttf")
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(FontWeight.NORMAL)
                }
                addFontFace {
                    addSource("fonts/sarabun_medium.ttf")
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(FontWeight.MEDIUM)
                }
                addFontFace {
                    addSource("fonts/sarabun_semibold.ttf")
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(FontWeight.SEMI_BOLD)
                }
                addFontFace {
                    addSource("fonts/sarabun_bold.ttf")
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(FontWeight.BOLD)
                }
            }
            // Selection ActionMode: four items — Start TTS · Copy · Translate · Add Replacement.
            // The Compose gesture overlay was removed (nav is via Readium InputListener), so a
            // long-press reaches the WebView and native text selection fires. Readium adds NO
            // system Copy/Select-All — the menu is exactly these four items.
            // mode.finish() is called INSIDE each coroutine, AFTER the suspend currentSelection()
            // read, so tearing down the ActionMode can never clear the selection before we read it.
            selectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(0, R.id.read_from_here, 0, context.getString(R.string.menu_start_tts))
                    menu.add(0, R.id.copy_selection, 1, context.getString(R.string.menu_copy))
                    menu.add(0, R.id.translate_selection, 2, context.getString(R.string.menu_translate))
                    menu.add(0, R.id.add_replacement, 3, context.getString(R.string.menu_add_replacement))
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    when (item.itemId) {
                        R.id.read_from_here -> viewModelScope.launch {
                            val nav = navigator as? SelectableNavigator
                            // fromSelection=true skips stream elements forward until the paragraph
                            // containing the selection text is found (precision fix); startAndPlay
                            // avoids the start()+play() race (C1).
                            nav?.currentSelection()?.locator
                                ?.let { ttsController.startAndPlay(bookId, it, fromSelection = true) }
                            nav?.clearSelection()
                            mode.finish()
                        }
                        R.id.copy_selection -> viewModelScope.launch {
                            val nav = navigator as? SelectableNavigator
                            val text = stripZwsp(
                                nav?.currentSelection()?.locator?.text?.highlight.orEmpty()
                            ).trim()
                            if (text.isNotEmpty()) {
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    ?.setPrimaryClip(android.content.ClipData.newPlainText("kanom", text))
                                toast(R.string.toast_copied)
                            }
                            nav?.clearSelection()
                            mode.finish()
                        }
                        R.id.translate_selection -> {
                            toast(R.string.toast_coming_soon)
                            mode.finish()
                        }
                        R.id.add_replacement -> viewModelScope.launch {
                            val nav = navigator as? SelectableNavigator
                            val text = stripZwsp(nav?.currentSelection()?.locator?.text?.highlight.orEmpty()).trim()
                            nav?.clearSelection()
                            mode.finish()
                            if (text.isNotEmpty()) _quickAddText.value = text
                        }
                        else -> return false
                    }
                    return true
                }
                override fun onDestroyActionMode(mode: ActionMode) {}
            }
        }

        val factory = EpubNavigatorFactory(publication)
            .createFragmentFactory(
                initialLocator = initialLocator,
                listener = listener,
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageLoaded() { applyPublisherLineHeight() }
                },
                initialPreferences = EpubPreferencesMapper.map(initialPrefs),
                configuration = config,
            )

        source.touchOpened(book)

        // Seed Ready with the CURRENT persisted state. The reactive prefs/bookmarks collectors
        // emit ONCE during init while the state is still Loading; the `if (s is Ready)` guard
        // drops those emissions and DataStore/Room do not re-emit without a change. Without
        // seeding here, a reopen would show default prefs (e.g. "สว่าง" instead of the persisted
        // "ซีเปีย") and an empty bookmarks list / missing in-page outlines.
        val initialBookmarks = bookmarkStore.observe(bookId).first()

        _uiState.value = ReaderUiState.Ready(
            book = book,
            publication = publication,
            fragmentFactory = factory,
            initialLocator = initialLocator,
            toc = publication.tableOfContents,
            prefs = initialPrefs,
            bookmarks = initialBookmarks,
            currentLocator = initialLocator,
        )
    }

    /**
     * Called by [ReaderContent] once the [EpubNavigatorFragment] is committed to the
     * FragmentManager. Collects [EpubNavigatorFragment.currentLocator] and feeds updates
     * into the debounced save pipeline. Safe to call multiple times; only the first binding
     * takes effect.
     *
     * Also re-applies bookmark decorations on bind so existing bookmarks appear after the
     * fragment is attached. Note: applyDecorations may not render until the WebView page is
     * fully loaded — this is a Task 10 device check, not addressed here.
     */
    fun bindNavigator(nav: EpubNavigatorFragment) {
        if (navigatorBound) return
        navigatorBound = true
        navigator = nav
        decorations.bind(nav)
        applyPublisherLineHeight(nav)
        navLocatorJob = viewModelScope.launch {
            nav.currentLocator.collect { onLocatorChanged(it) }
        }
        // Re-apply current bookmarks now that we have a navigator reference.
        val currentBookmarks = (_uiState.value as? ReaderUiState.Ready)?.bookmarks ?: emptyList()
        decorations.applyBookmarks(currentBookmarks)

        // Page-nav via Readium input (replaces the deleted Compose overlay).
        // DirectionalNavigationAdapter handles edge-tap + key page turns; the custom listener
        // handles center-tap chrome-toggle + swipe.
        val thresholdPx = 48f * context.resources.displayMetrics.density
        val edgeAdapter = DirectionalNavigationAdapter(
            navigator = nav,
            horizontalEdgeThresholdPercent = 0.333,
        )
        val navListener = ReaderNavInputListener(
            onToggleChrome = ::toggleChrome,
            onNextPage = ::nextPage,
            onPrevPage = ::previousPage,
            swipeThresholdPx = thresholdPx,
        )
        nav.addInputListener(edgeAdapter)   // FIRST → consumes edge taps
        nav.addInputListener(navListener)   // SECOND → center taps + drag reach here
        this.edgeAdapter = edgeAdapter
        this.navListener = navListener
    }

    /**
     * Detach from [nav] when the reader fragment's view is destroyed — e.g. the user navigates to
     * Now-Playing/Settings while the reader stays in the back stack, or on a configuration change.
     * Without this the auto-scroll collector keeps calling navigator.go() on a view-less fragment
     * (IllegalStateException: getView() null). Resets navigatorBound so a fresh fragment rebinds on
     * re-entry. No-op if we are not currently bound to [nav].
     */
    fun unbindNavigator(nav: EpubNavigatorFragment) {
        if (navigator !== nav) return
        navLocatorJob?.cancel()
        navLocatorJob = null
        edgeAdapter?.let { nav.removeInputListener(it) }
        navListener?.let { nav.removeInputListener(it) }
        edgeAdapter = null
        navListener = null
        decorations.unbind()
        navigator = null
        navigatorBound = false
    }

    /** Navigate to a ToC [link]. Fire-and-forget; result ignored. */
    fun goTo(link: Link) {
        navigator?.go(link, animated = animatePageTurns)
    }

    /** Go forward one page. Fire-and-forget; result ignored. */
    fun nextPage() { navigator?.goForward(animated = animatePageTurns) }

    /** Go backward one page. Fire-and-forget; result ignored. */
    fun previousPage() { navigator?.goBackward(animated = animatePageTurns) }

    /**
     * Feed a locator update into the debounced save pipeline and mirror the latest position into
     * [ReaderUiState.Ready.currentLocator] so the ToC sheet can focus + mark the current chapter.
     * Public for unit-testing.
     */
    fun onLocatorChanged(loc: Locator) {
        locatorChanges.tryEmit(loc)
        _uiState.update { if (it is ReaderUiState.Ready) it.copy(currentLocator = loc) else it }
    }

    /** Toggle chrome visibility from VM state; stable reference safe to pass as a callback. */
    fun toggleChrome() = _uiState.update {
        if (it is ReaderUiState.Ready) it.copy(chromeVisible = !it.chromeVisible) else it
    }

    /**
     * Play/pause toggle for TTS playback.
     *
     * - If [PlaybackState.Playing]: pause.
     * - If [PlaybackState.Paused]: resume (play).
     * - If Idle/Loading/Error: start from the first visible element on screen
     *   ([VisualNavigator.firstVisibleElementLocator]) falling back to
     *   [EpubNavigatorFragment.currentLocator], then play.
     *
     * [firstVisibleElementLocator] is `@ExperimentalReadiumApi` — opted in at class level.
     */
    fun toggleTts() {
        viewModelScope.launch {
            val st = ttsController.state.value
            if (st is PlaybackState.Playing) { ttsController.pause(); return@launch }
            if (st is PlaybackState.Paused)  { ttsController.play();  return@launch }
            val from = (navigator as? VisualNavigator)?.firstVisibleElementLocator()
                ?: navigator?.currentLocator?.value
            // startAndPlay avoids the start()+play() race (C1)
            ttsController.startAndPlay(bookId, from)
        }
    }

    // ─── Bookmark actions ─────────────────────────────────────────────────────

    /**
     * Add a bookmark at the navigator's current position. No-op if navigator not bound.
     *
     * The bookmark is inserted immediately with a cheap snippet (the on-screen highlight text,
     * if any, so it appears in the open bookmarks sheet right away). A slow content-excerpt
     * extraction then runs in the background and upgrades the snippet via [updateSnippet].
     * [resolveBookmarkLabel] already falls back to chapter title / "NN%" when snippet is blank,
     * so a freshly-inserted bookmark with an empty quick-snippet shows a meaningful label
     * instantly, then upgrades to the passage excerpt once [extractExcerpt] completes.
     */
    fun addBookmarkAtCurrent() {
        val loc = navigator?.currentLocator?.value ?: return
        val publication = (_uiState.value as? ReaderUiState.Ready)?.publication
        viewModelScope.launch {
            // Insert immediately with the on-screen highlight (instant) so the bookmark
            // appears in the open sheet without waiting for the slow excerpt extraction.
            val quick = cleanSnippet(loc.text.highlight.orEmpty())
            val id = bookmarkStore.add(
                ReaderBookmarkFactory.build(bookId, loc, quick, System.currentTimeMillis()),
            )
            // Enrich with a passage excerpt in the background; only overwrite if non-blank.
            val excerpt = extractExcerpt(publication, loc)
            if (!excerpt.isNullOrBlank() && excerpt != quick) {
                bookmarkStore.updateSnippet(id, excerpt)
            }
        }
    }

    /**
     * Extract a passage excerpt at [loc] via the Content-at-locator API.
     *
     * Returns the cleaned excerpt string, or null/blank when extraction produces nothing
     * (e.g. the resource has no text elements at that locator, or the API throws).
     * Does NOT include the title/% fallbacks — those are resolved at display time by
     * [resolveBookmarkLabel].
     *
     * The display [publication] may contain U+200B; [cleanSnippet] strips it, so reading
     * the display publication is acceptable here.
     */
    private suspend fun extractExcerpt(publication: Publication?, loc: Locator): String? =
        runCatching {
            publication
                ?.content(loc)
                ?.elements()
                ?.filterIsInstance<Content.TextElement>()
                ?.firstOrNull { it.text.isNotBlank() }
                ?.text
        }.onFailure {
            android.util.Log.w("ReaderViewModel", "bookmark excerpt extraction failed", it)
        }.getOrNull()
            ?.let(::cleanSnippet)
            ?.takeIf { it.isNotBlank() }

    /** Remove bookmark by id. Decoration group is updated by the bookmarks collector. */
    fun removeBookmark(id: Long) = viewModelScope.launch { bookmarkStore.remove(id) }

    /** Navigate to the locator encoded as JSON string. No-op on decode failure. */
    fun goToBookmark(locatorJson: String) {
        LocatorSerializer.decode(locatorJson)?.let { navigator?.go(it, animated = animatePageTurns) }
    }

    // ─── Debug probe ─────────────────────────────────────────────────────────

    /**
     * DEBUG-ONLY: apply a rose Highlight decoration for every tokenised sentence in the
     * current publication, using group "probe" (independent of "bookmarks").
     *
     * This validates that (a) [SentenceTokenizer] boundaries are correct on real Thai EPUBs and
     * (b) Readium's Decoration rendering works for Highlight style — the two preconditions for
     * Plan 07's karaoke highlight. No-op in release builds.
     */
    fun runSentenceProbe() {
        if (!BuildConfig.DEBUG) return
        val publication = (_uiState.value as? ReaderUiState.Ready)?.publication ?: return
        decorations.runProbe(publication)
    }

    /**
     * DEBUG-ONLY: clear the "probe" decoration group applied by [runSentenceProbe].
     * Safe to call even when no probe is active.
     */
    fun clearSentenceProbe() {
        if (!BuildConfig.DEBUG) return
        decorations.clearProbe()
    }

    // ─── Settings setters ─────────────────────────────────────────────────────
    // Each delegates to PreferencesRepository; the live-apply collector above submits
    // the updated EpubPreferences to the navigator automatically.

    /**
     * Readium disables its line-height preference while publisher styles are enabled.
     * Keep publisher CSS active, but apply the user's line-height as a narrowly-scoped
     * user override after each XHTML page finishes loading.
     */
    private fun applyPublisherLineHeight(nav: EpubNavigatorFragment? = navigator) {
        val target = nav ?: return
        val currentPrefs = (_uiState.value as? ReaderUiState.Ready)?.prefs ?: return
        val script = if (currentPrefs.usePublisherStyles) {
            val height = String.format(java.util.Locale.US, "%.2f", currentPrefs.lineHeight)
            "(function(){var id='kanom-reader-line-height';var s=document.getElementById(id);" +
                "if(!s){s=document.createElement('style');s.id=id;(document.head||document.documentElement).appendChild(s);}" +
                "s.textContent='body, body p, body li, body div, body dd, body dt, body blockquote {line-height: ${height} !important;}';})();"
        } else {
            "(function(){var s=document.getElementById('kanom-reader-line-height');if(s)s.remove();})();"
        }
        viewModelScope.launch {
            runCatching { target.evaluateJavascript(script) }
                .onFailure { android.util.Log.w("ReaderViewModel", "Could not apply publisher-compatible line height", it) }
        }
    }

    fun onThemeSelected(t: ReaderTheme) = viewModelScope.launch { prefs.setReaderTheme(t) }
    fun onFontSelected(f: ReadingFont) = viewModelScope.launch { prefs.setReadingFont(f) }
    fun onFontSize(sp: Int) = viewModelScope.launch { prefs.setFontSizeSp(sp) }
    fun onLineHeight(v: Float) = viewModelScope.launch { prefs.setLineHeight(v) }
    fun onMargin(dp: Int) = viewModelScope.launch { prefs.setMarginDp(dp) }
    fun onTopMargin(dp: Int) = viewModelScope.launch { prefs.setTopMarginDp(dp) }
    fun onBottomMargin(dp: Int) = viewModelScope.launch { prefs.setBottomMarginDp(dp) }
    fun onBoldText(enabled: Boolean) = viewModelScope.launch { prefs.setBoldText(enabled) }
    // Task 6 escape hatches
    fun onUsePublisherStyles(v: Boolean) = viewModelScope.launch { prefs.setUsePublisherStyles(v) }
    fun onForceFont(v: Boolean) = viewModelScope.launch { prefs.setForceFont(v) }
    fun onStripColors(v: Boolean) = viewModelScope.launch { prefs.setStripColors(v) }
    fun onThaiWordBreak(v: Boolean) = viewModelScope.launch { prefs.setThaiWordBreak(v) }
    fun onPageAnimation(v: Boolean) = viewModelScope.launch { prefs.setPageAnimation(v) }
    fun onVisualReplacement(v: Boolean) = viewModelScope.launch { prefs.setVisualReplacement(v) }

    /** Show a short Toast on the main thread. Safe to call from the ActionMode UI thread. */
    private fun toast(resId: Int) {
        android.widget.Toast.makeText(context, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        super.onCleared()
        navigator?.let { nav ->
            edgeAdapter?.let { nav.removeInputListener(it) }
            navListener?.let { nav.removeInputListener(it) }
        }
        navigator = null
        edgeAdapter = null
        navListener = null
        publicationToClose?.close()
        publicationToClose = null
    }
}
