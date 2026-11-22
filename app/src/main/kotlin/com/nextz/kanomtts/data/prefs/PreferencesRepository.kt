package com.nextz.kanomtts.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nextz.kanomtts.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PreferencesRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : ReaderPreferences {
    private object Keys {
        val theme = stringPreferencesKey("reader_theme")
        val font = stringPreferencesKey("reading_font")
        val fontSize = intPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height")
        val paragraphIndentEm = floatPreferencesKey("paragraph_indent_em")
        val paragraphSpacingEm = floatPreferencesKey("paragraph_spacing_em")
        val margin = intPreferencesKey("margin_dp")
        val topMargin = intPreferencesKey("reader_top_margin_dp")
        val bottomMargin = intPreferencesKey("reader_bottom_margin_dp")
        val boldText = booleanPreferencesKey("reader_bold_text")
        val reverseVolumeKeys = booleanPreferencesKey("reverse_volume_keys")
        val skipUrls = booleanPreferencesKey("skip_urls")
        val highlight = booleanPreferencesKey("highlight_on")
        val autoScroll = booleanPreferencesKey("auto_scroll")
        val sleep = intPreferencesKey("sleep_timer_default_min")
        val premium = booleanPreferencesKey("premium_unlocked")
        val usePublisherStyles = booleanPreferencesKey("use_publisher_styles")
        val forceFont = booleanPreferencesKey("force_font")
        val stripColors = booleanPreferencesKey("strip_colors")
        val thaiWordBreak = booleanPreferencesKey("thai_word_break")
        val pageAnimation = booleanPreferencesKey("page_animation")
        val visualReplacement = booleanPreferencesKey("visual_replacement")
        val readingUnit = stringPreferencesKey("reading_unit")
    }

    companion object {
        private val DEFAULTS = KanomPreferences()
    }

    override val preferences: Flow<KanomPreferences> = store.data.map { p ->
        KanomPreferences(
            readerTheme = p[Keys.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: DEFAULTS.readerTheme,
            readingFont = p[Keys.font]?.let { runCatching { ReadingFont.valueOf(it) }.getOrNull() } ?: DEFAULTS.readingFont,
            fontSizeSp = p[Keys.fontSize] ?: DEFAULTS.fontSizeSp,
            lineHeight = p[Keys.lineHeight] ?: DEFAULTS.lineHeight,
            paragraphIndentEm = p[Keys.paragraphIndentEm] ?: DEFAULTS.paragraphIndentEm,
            paragraphSpacingEm = p[Keys.paragraphSpacingEm] ?: DEFAULTS.paragraphSpacingEm,
            marginDp = p[Keys.margin] ?: DEFAULTS.marginDp,
            topMarginDp = p[Keys.topMargin] ?: DEFAULTS.topMarginDp,
            bottomMarginDp = p[Keys.bottomMargin] ?: DEFAULTS.bottomMarginDp,
            boldText = p[Keys.boldText] ?: DEFAULTS.boldText,
            reverseVolumeKeys = p[Keys.reverseVolumeKeys] ?: DEFAULTS.reverseVolumeKeys,
            skipUrls = p[Keys.skipUrls] ?: DEFAULTS.skipUrls,
            highlightOn = p[Keys.highlight] ?: DEFAULTS.highlightOn,
            autoScroll = p[Keys.autoScroll] ?: DEFAULTS.autoScroll,
            sleepTimerDefaultMin = p[Keys.sleep] ?: DEFAULTS.sleepTimerDefaultMin,
            premiumUnlocked = p[Keys.premium] ?: DEFAULTS.premiumUnlocked,
            usePublisherStyles = p[Keys.usePublisherStyles] ?: DEFAULTS.usePublisherStyles,
            forceFont = p[Keys.forceFont] ?: DEFAULTS.forceFont,
            stripColors = p[Keys.stripColors] ?: DEFAULTS.stripColors,
            thaiWordBreak = p[Keys.thaiWordBreak] ?: DEFAULTS.thaiWordBreak,
            pageAnimation = p[Keys.pageAnimation] ?: DEFAULTS.pageAnimation,
            visualReplacement = p[Keys.visualReplacement] ?: DEFAULTS.visualReplacement,
            readingUnit = p[Keys.readingUnit]?.let { runCatching { ReadingUnit.valueOf(it) }.getOrNull() } ?: DEFAULTS.readingUnit,
        )
    }

    override suspend fun setReaderTheme(v: ReaderTheme) { store.edit { it[Keys.theme] = v.name } }
    override suspend fun setReadingFont(v: ReadingFont) { store.edit { it[Keys.font] = v.name } }
    override suspend fun setFontSizeSp(v: Int) { store.edit { it[Keys.fontSize] = v } }
    override suspend fun setLineHeight(v: Float) { store.edit { it[Keys.lineHeight] = v } }
    override suspend fun setParagraphIndentEm(v: Float) { store.edit { it[Keys.paragraphIndentEm] = v } }
    override suspend fun setParagraphSpacingEm(v: Float) { store.edit { it[Keys.paragraphSpacingEm] = v } }
    override suspend fun setMarginDp(v: Int) { store.edit { it[Keys.margin] = v } }
    override suspend fun setTopMarginDp(v: Int) { store.edit { it[Keys.topMargin] = v } }
    override suspend fun setBottomMarginDp(v: Int) { store.edit { it[Keys.bottomMargin] = v } }
    override suspend fun setBoldText(v: Boolean) { store.edit { it[Keys.boldText] = v } }
    override suspend fun setReverseVolumeKeys(v: Boolean) { store.edit { it[Keys.reverseVolumeKeys] = v } }
    override suspend fun setSkipUrls(v: Boolean) { store.edit { it[Keys.skipUrls] = v } }
    override suspend fun setHighlightOn(v: Boolean) { store.edit { it[Keys.highlight] = v } }
    override suspend fun setAutoScroll(v: Boolean) { store.edit { it[Keys.autoScroll] = v } }
    override suspend fun setSleepTimerDefaultMin(v: Int) { store.edit { it[Keys.sleep] = v } }
    suspend fun setPremiumUnlocked(v: Boolean) { store.edit { it[Keys.premium] = v } }
    override suspend fun setUsePublisherStyles(v: Boolean) { store.edit { it[Keys.usePublisherStyles] = v } }
    override suspend fun setForceFont(v: Boolean) { store.edit { it[Keys.forceFont] = v } }
    override suspend fun setStripColors(v: Boolean) { store.edit { it[Keys.stripColors] = v } }
    override suspend fun setThaiWordBreak(v: Boolean) { store.edit { it[Keys.thaiWordBreak] = v } }
    override suspend fun setPageAnimation(v: Boolean) { store.edit { it[Keys.pageAnimation] = v } }
    override suspend fun setVisualReplacement(v: Boolean) { store.edit { it[Keys.visualReplacement] = v } }
    override suspend fun setReadingUnit(v: ReadingUnit) { store.edit { it[Keys.readingUnit] = v.name } }
}
