# TTS Playback API Recon — Plan 07

- **Date:** 2026-06-24
- **Plan:** 07 (TTS playback) — planning-phase API recon
- **Purpose:** The Plan 02 spike verified open/content/tokenizer; this doc verifies the media-playback surface the custom TTS engine needs (media3 SimpleBasePlayer/MediaSessionService, android.speech.tts.TextToSpeech, Readium Content streaming + long-press→Locator). 3 parallel recon agents → cross-checked synthesis.

# Plan 07 — Custom TTS Playback: Consolidated API Reference

> Project coordinates verified against the live repo: `namespace/applicationId = com.nextz.kanomtts`, `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36` (minorApiLevel 1), AGP `9.2.1`, Kotlin `2.2.10` (bundled by AGP), Java 17 + core-library desugaring, Readium `3.3.0`. Launcher activity = `com.nextz.kanomtts.MainActivity`. media3 is NOT yet in `gradle/libs.versions.toml` (must be added). `readium-navigator-media-tts` / `readium-navigator-media-common` are CURRENTLY declared (`app/build.gradle.kts:88-89`) and are to be removed in this plan.

---

## 1. Gradle / dependency changes

### 1a. Add media3 to the version catalog (`gradle/libs.versions.toml`)

```toml
[versions]
# media3 1.10.1 — released 2026-05-12. minSdk raised to 23 in 1.9.0 (<= app minSdk 26, OK).
# Built/tested against current SDKs; no upper-bound conflict with compileSdk 36 / AGP 9.2.1.
media3 = "1.10.1"

[libraries]
# SimpleBasePlayer, MediaItem, MediaMetadata, Player.Commands, AudioAttributes live in -common.
androidx-media3-common  = { group = "androidx.media3", name = "media3-common",  version.ref = "media3" }
# MediaSession, MediaSessionService, MediaController, SessionToken.
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
```

### 1b. `app/build.gradle.kts` dependency edits

```kotlin
// ADD:
implementation(libs.androidx.media3.session)   // MediaSession / MediaSessionService / MediaController
implementation(libs.androidx.media3.common)    // SimpleBasePlayer + state types
// media3-ui is NOT needed (legacy PlayerView Views; notification/lock-screen UI is auto-generated
// by media3-session). Guava ListenableFuture arrives transitively via media3.

// REMOVE (verified leaf modules; nothing in shared/navigator/streamer needs them transitively):
//   implementation(libs.readium.navigator.media.tts)      // app/build.gradle.kts:88
//   implementation(libs.readium.navigator.media.common)   // app/build.gradle.kts:89
// Also delete the two catalog lines readium-navigator-media-tts / -media-common (libs.versions.toml:82-83).
```

Keep: `readium-shared`, `readium-streamer`, `readium-navigator` (core `readium-navigator` depends only on `api(readium-shared)`). NOTE: media3 is also pulled transitively by core `readium-navigator`; removing media-tts/media-common does NOT remove media3 — but declare it explicitly anyway for the custom player.

**Confidence: high.**

---

## 2. AndroidManifest — foreground service + permissions

`app/src/main/AndroidManifest.xml` currently has NO `<uses-permission>` and NO service. Add:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<!-- inside <application> -->
<service
    android:name=".playback.TtsPlaybackService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
  <intent-filter>
    <action android:name="androidx.media3.session.MediaSessionService"/>
  </intent-filter>
</service>
```

- `MediaSessionService` calls `startForeground()` ITSELF once the player has media items + `playWhenReady` — do NOT call `startForeground` manually.
- `POST_NOTIFICATIONS` is a RUNTIME permission on API 33+ (granted at install on 26–32). Request it before starting playback or the media notification won't show on 13+.

**Confidence: high.**

---

## 3. media3 — the custom `SimpleBasePlayer` (non-ExoPlayer, utterance-based)

### 3a. Imports

```kotlin
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
```

### 3b. Constructor + abstract `getState()`

```
SimpleBasePlayer(Looper applicationLooper)            // ctor — pass Looper.getMainLooper()
protected abstract SimpleBasePlayer.State getState()  // rebuild immutable State on every call
protected final void invalidateState()                // schedule a getState() re-pull + emit Player events
```

ALL `Player` calls (incl. from MediaController / notification taps) arrive on this Looper. Mutate backing state and call `invalidateState()` only on that thread; marshal async TTS callbacks back onto it.

### 3c. `State.Builder` (non-seekable utterance player)

```
Builder setAvailableCommands(Player.Commands availableCommands)
Builder setPlaybackState(@Player.State int playbackState)          // STATE_IDLE/STATE_READY/STATE_ENDED
Builder setPlayWhenReady(boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason)
Builder setPlaylist(List<SimpleBasePlayer.MediaItemData> playlist)
Builder setCurrentMediaItemIndex(int currentMediaItemIndex)
Builder setContentPositionMs(long positionMs)                      // C.TIME_UNSET = indeterminate
Builder setAudioAttributes(AudioAttributes audioAttributes)        // STATE REPORTING ONLY — does NOT request focus
State build()
```

### 3d. `MediaItemData.Builder` (one item per chapter/segment)

```
MediaItemData.Builder(Object uid)        // uid REQUIRED — stable & unique per item  [verify exact overload in 1.10.1 — see unresolved]
Builder setMediaItem(MediaItem mediaItem)
Builder setMediaMetadata(@Nullable MediaMetadata mediaMetadata)    // title/author/artwork -> notification + lock screen
Builder setIsSeekable(boolean isSeekable)                          // false
Builder setIsDynamic(boolean isDynamic)                            // false
Builder setDurationUs(long durationUs)                             // C.TIME_UNSET if unknown
MediaItemData build()
```

### 3e. `handle*` overrides (each returns Guava `ListenableFuture<?>`)

```
protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady)
protected ListenableFuture<?> handlePrepare()
protected ListenableFuture<?> handleStop()
protected ListenableFuture<?> handleRelease()
protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, @Player.Command int seekCommand)
protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs)
```

- next/previous are NOT separate methods: expose `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM` / `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM` in availableCommands; both arrive through `handleSeek` with the already-resolved target `mediaItemIndex` and the corresponding `seekCommand`.
- Use `Futures.immediateVoidFuture()` for synchronous ops.

### 3f. Minimal compiling skeleton

```kotlin
class TtsPlayer(
  looper: Looper,
  private val ttsController: TtsController,   // your @Singleton TTS+focus controller (sections 4–5)
) : SimpleBasePlayer(looper) {

  private val availableCommands = Player.Commands.Builder()
    .addAll(
      Player.COMMAND_PLAY_PAUSE,
      Player.COMMAND_PREPARE,
      Player.COMMAND_STOP,
      Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
      Player.COMMAND_GET_METADATA,
      Player.COMMAND_GET_TIMELINE,
      Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
      Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
      Player.COMMAND_RELEASE,
    ).build()
    // Omit COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM / COMMAND_SET_SPEED so the notification shows no scrubber.

  // backing state, mutated only on `looper`
  private var playWhenReady = false
  private var playbackState = Player.STATE_IDLE
  private var currentIndex = 0
  private var mediaItemDataList: List<MediaItemData> = emptyList()

  override fun getState(): State = State.Builder()
    .setAvailableCommands(availableCommands)
    .setPlaybackState(playbackState)
    .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    .setPlaylist(mediaItemDataList)
    .setCurrentMediaItemIndex(currentIndex)
    .setContentPositionMs(C.TIME_UNSET)
    .build()

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    this.playWhenReady = playWhenReady
    if (playWhenReady) ttsController.resume() else ttsController.pause()
    return Futures.immediateVoidFuture()
  }
  override fun handlePrepare(): ListenableFuture<*> {
    playbackState = Player.STATE_READY; return Futures.immediateVoidFuture()
  }
  override fun handleStop(): ListenableFuture<*> {
    ttsController.stop(); return Futures.immediateVoidFuture()
  }
  override fun handleRelease(): ListenableFuture<*> {
    ttsController.shutdown(); return Futures.immediateVoidFuture()
  }
  override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
    currentIndex = mediaItemIndex
    ttsController.jumpToSegment(mediaItemIndex)
    return Futures.immediateVoidFuture()
  }

  // call from TTS callbacks, ON `looper`, after mutating backing state:
  fun onSegmentAdvanced(newIndex: Int) { currentIndex = newIndex; invalidateState() }
}
```

### 3g. `MediaMetadata` (lock-screen title/author/artwork)

```kotlin
import androidx.media3.common.MediaMetadata
val metadata = MediaMetadata.Builder()
  .setTitle(chapterTitle)
  .setArtist(author)        // 'artist' line on lock screen
  .setAlbumTitle(bookTitle)
  .setArtworkUri(coverUri)  // or setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
  .setIsPlayable(true)
  .build()
```
Signatures: `setTitle(CharSequence?)`, `setArtist(CharSequence?)`, `setAlbumTitle(CharSequence?)`, `setArtworkUri(Uri?)`, `setArtworkData(byte[]?, Integer?)`, `setIsPlayable(Boolean?)`, `build()`. Attach to each item via `MediaItemData.Builder.setMediaMetadata(...)`.

**Confidence: high** (uid overload medium — see unresolved).

---

## 4. media3 — `MediaSessionService`, `MediaSession`, in-app `MediaController`

### 4a. Imports

```kotlin
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
```

### 4b. Service (host the player; auto-generates notification + lock-screen UI)

```kotlin
@AndroidEntryPoint
class TtsPlaybackService : MediaSessionService() {
  @Inject lateinit var ttsPlayer: TtsPlayer          // @Singleton SimpleBasePlayer
  private var session: MediaSession? = null

  override fun onCreate() {
    super.onCreate()
    val activityPi = PendingIntent.getActivity(
      this, 0,
      Intent(this, com.nextz.kanomtts.MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE,
    )
    session = MediaSession.Builder(this, ttsPlayer)
      .setSessionActivity(activityPi)                // tap notification -> open app
      .build()
  }
  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
  override fun onDestroy() { session?.run { player.release(); release() }; session = null; super.onDestroy() }
  // Optional: override onTaskRemoved() to stop the service when the app is swiped away.
}
```

Signatures:
```
MediaSession.Builder(Context, Player) ; .setId(String) ; .setSessionActivity(PendingIntent) ; .build()
override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession?
```
The MediaStyle notification + lock-screen transport controls are produced AUTOMATICALLY from the session's Player state + `MediaMetadata` — no manual `NotificationCompat`. Override `onUpdateNotification(...)` only to customize.

### 4c. In-app Compose `MediaController`

```kotlin
val token = SessionToken(context, ComponentName(context, TtsPlaybackService::class.java))
val future = MediaController.Builder(context, token).buildAsync()
future.addListener({
  val controller: MediaController = future.get()    // controller IS a Player
}, ContextCompat.getMainExecutor(context))
// On dispose: MediaController.releaseFuture(future)
```
Signatures: `SessionToken(Context, ComponentName)`; `MediaController.Builder(Context, SessionToken).buildAsync(): ListenableFuture<MediaController>`.

Hilt: service is `@AndroidEntryPoint` and `@Inject`s the `@Singleton` `TtsPlayer`/`TtsController` so service + in-app UI share one source of truth. In-app UI may observe the `@Singleton` player directly OR connect via `MediaController` (use the latter for system/Assistant/Bluetooth/Wear routing).

**Confidence: high.**

---

## 5. media3 — AUDIO FOCUS (must be self-managed — primary gap)

CRITICAL: SimpleBasePlayer does NOT request/abandon `AudioManager` focus. `State.Builder.setAudioAttributes()` only REPORTS attributes; there is no `handleSetAudioAttributes` that wires focus. The automatic focus flow (`ExoPlayer.setAudioAttributes(attrs, handleAudioFocus=true)`) is ExoPlayer-internal (`AudioFocusManager`) and unavailable here (confirmed by androidx/media issues #2166, #1470).

Self-manage focus around `TtsController` start/stop:

```kotlin
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

val attrs = AudioAttributes.Builder()
  .setUsage(AudioAttributes.USAGE_MEDIA)
  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
  .build()
val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
  .setAudioAttributes(attrs)
  .setOnAudioFocusChangeListener { change ->
    when (change) {
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> ttsController.pause()
      AudioManager.AUDIOFOCUS_GAIN -> ttsController.resume()
      AudioManager.AUDIOFOCUS_LOSS -> ttsController.stop()
    }
  }.build()
// requestAudioFocus(focusRequest) before speaking; abandonAudioFocusRequest(focusRequest) on stop.
```

Also register a `BroadcastReceiver` for `AudioManager.ACTION_AUDIO_BECOMING_NOISY` to pause on headphone unplug. Use the SAME `AudioAttributes` (USAGE_MEDIA + CONTENT_TYPE_SPEECH) on both the focus request and `TextToSpeech.setAudioAttributes(...)` (section 6f) so routing/ducking stay consistent.

**Confidence: high** (gap confirmed via source + issue tracker).

---

## 6. android.speech.tts.TextToSpeech — speak/callback contract

### 6a. Imports

```kotlin
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.os.Bundle
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
```

### 6b. Constructor + init

```
TextToSpeech(Context, OnInitListener)
TextToSpeech(Context, OnInitListener, String engine)   // engine = pkg, e.g. "com.google.android.tts"
interface OnInitListener { fun onInit(status: Int) }    // SUCCESS=0, ERROR=-1, STOPPED=-2
fun shutdown()                                          // release native resources
```
`getVoices()`/`getDefaultVoice()`/`getAvailableLanguages()` return null/empty until `onInit` SUCCESS — gate all enumeration & speak() behind a `ready` StateFlow flipped on `status == SUCCESS`.

### 6c. Voice enumeration + selection

```
fun getVoices(): Set<Voice>                  // wrap with orEmpty()
fun getDefaultVoice(): Voice
fun setVoice(voice: Voice): Int              // SUCCESS(0)/ERROR(-1)  -- PREFER for multi-voice
fun setLanguage(loc: Locale): Int            // returns LANG_* (NOT SUCCESS/ERROR)
fun isLanguageAvailable(loc: Locale): Int    // LANG_* code
//   LANG_AVAILABLE=0, LANG_COUNTRY_AVAILABLE=1, LANG_COUNTRY_VAR_AVAILABLE=2,
//   LANG_MISSING_DATA=-1, LANG_NOT_SUPPORTED=-2     (negatives! do NOT test == SUCCESS)
// Voice: getName():String, getLocale():Locale, getQuality():Int, getLatency():Int,
//        isNetworkConnectionRequired():Boolean, getFeatures():Set<String>
//   QUALITY_* / LATENCY_*: VERY_LOW=100, LOW=200, NORMAL=300, HIGH=400, VERY_HIGH=500
TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA = "android.speech.tts.engine.INSTALL_TTS_DATA"
```

```kotlin
fun pickVoice(tts: TextToSpeech, target: Locale, offlineOnly: Boolean): Voice? =
  tts.voices.orEmpty()
    .filter { it.locale.language == target.language &&
              (target.country.isEmpty() || it.locale.country == target.country) }
    .filter { !offlineOnly || !it.isNetworkConnectionRequired }
    .maxByOrNull { it.quality }
// install missing data (guard with resolveActivity):
// startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(FLAG_ACTIVITY_NEW_TASK))
```

### 6d. speak() + rate/pitch/stop + param keys

```
fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String?): Int  // SUCCESS(0)/ERROR(-1)
//   QUEUE_FLUSH=0 (drop pending + interrupt) ; QUEUE_ADD=1 (append)
fun setSpeechRate(speechRate: Float): Int    // 1.0 normal
fun setPitch(pitch: Float): Int              // 1.0 normal
fun stop(): Int                              // clears queue + stops current; fires onStop
// Engine Bundle keys: KEY_PARAM_UTTERANCE_ID="utteranceId", KEY_PARAM_VOLUME="volume" (0..1),
//   KEY_PARAM_PAN="pan" (-1..1), KEY_PARAM_STREAM="streamType" (legacy; prefer setAudioAttributes)
```
The 4th `utteranceId` arg is AUTHORITATIVE (supersedes the Bundle key).

### 6e. `UtteranceProgressListener` callbacks

```
abstract class UtteranceProgressListener {
  abstract fun onStart(utteranceId: String)
  abstract fun onDone(utteranceId: String)
  @Deprecated abstract fun onError(utteranceId: String)                 // legacy but ABSTRACT — must override
  open fun onError(utteranceId: String, errorCode: Int)                 // API 21; real codes here
  open fun onStop(utteranceId: String, interrupted: Boolean)           // API 23
  open fun onBeginSynthesis(id: String, sampleRateHz: Int, audioFormat: Int, channelCount: Int) // API 24
  open fun onAudioAvailable(id: String, audio: ByteArray)              // API 24
  open fun onRangeStart(id: String, start: Int, end: Int, frame: Int)  // API 26 — word boundary
}
// onRangeStart: start=inclusive char index, end=exclusive char index, frame=audio frame.
// errorCodes: ERROR_SYNTHESIS=-3, ERROR_SERVICE=-4, ERROR_OUTPUT=-5, ERROR_NETWORK=-6,
//   ERROR_NETWORK_TIMEOUT=-7, ERROR_INVALID_REQUEST=-8, ERROR_NOT_INSTALLED_YET=-9
fun setOnUtteranceProgressListener(listener: UtteranceProgressListener): Int
```
Callbacks arrive on a binder/background thread, NOT main — marshal resumes/UI updates. `onRangeStart` (minSdk 26 OK) is BEST-EFFORT: Google TTS supports it, some OEM/Samsung engines never fire it — degrade gracefully.

### 6f. AudioAttributes for routing/ducking

```
fun setAudioAttributes(audioAttributes: AudioAttributes): Int   // SUCCESS(0)/ERROR(-1); applies to subsequent speak()
```
Use `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH` — same instance as the focus request (section 5).

### 6g. Suspend bridge (sequential segment playback)

```kotlin
private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
// listener: onDone -> pending.remove(id)?.resume(Unit)
//           onError(id,code) -> pending.remove(id)?.resumeWithException(TtsException(code))
//           onStop(id,_) -> pending.remove(id)?.cancel()

suspend fun speakSegment(tts: TextToSpeech, text: CharSequence, id: String, params: Bundle) =
  suspendCancellableCoroutine<Unit> { cont ->
    pending[id] = cont
    cont.invokeOnCancellation { pending.remove(id); tts.stop() }
    val rc = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
    if (rc != TextToSpeech.SUCCESS) {
      pending.remove(id); cont.resumeWithException(IllegalStateException("speak() rc=$rc"))
    }
  }
// Drive reading order by awaiting speakSegment(...) sequentially in a loop.
```

**Confidence: high** (onRangeStart per-device coverage is the only soft spot — see unresolved).

---

## 7. Readium 3.3.0 — Content streaming for TTS

### 7a. Imports

```kotlin
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content   // Publication.content(...) extension
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.ExperimentalReadiumApi
```

### 7b. Entry point + streaming iterator (the TTS primitive)

```
@OptIn(ExperimentalReadiumApi::class)
fun Publication.content(start: Locator? = null): Content?   // extension, NON-suspend, NULLABLE (null when no ContentService)

interface Content {
  operator fun iterator(): Iterator
  suspend fun elements(): List<Content.Element>     // EAGER — avoid for full-book TTS
  suspend fun text(separator: String = "\n"): String?  // EAGER
  interface Iterator {
    suspend operator fun hasNext(): Boolean          // SUSPEND
    operator fun next(): Element                     // NON-suspend; call only after hasNext()==true
    suspend fun hasPrevious(): Boolean
    fun previous(): Element
    suspend fun nextOrNull(): Element?               // cleanest single-call streaming primitive
    suspend fun previousOrNull(): Element?
  }
}
```

`Content.Element` subtypes incl. `Content.TextElement` (has `.locator: Locator` and `.segments`); each `Segment` has `.text`, `.locator` (decorable), `.language` (via AttributesHolder). Other subtypes (Image/Audio/Video) are skipped for TTS.

### 7c. Streaming loop + resource-boundary detection

```kotlin
@OptIn(ExperimentalReadiumApi::class)
suspend fun streamForTts(publication: Publication, start: Locator?) {
  val content: Content = publication.content(start) ?: return  // null => EPUB has no content service
  val it = content.iterator()
  var currentHref: Url? = null                                 // org.readium.r2.shared.util.Url
  while (it.hasNext()) {                                        // for-in won't compile (hasNext is suspend)
    val el = it.next()
    if (el is Content.TextElement) {
      if (el.locator.href != currentHref) { currentHref = el.locator.href; onNewResource(currentHref) }
      for (seg in el.segments) feed(seg.text, seg.locator)     // seg.locator -> decoration; seg.language -> per-segment locale
    }
  }
}
```

- `iterator().next()` is NON-suspend and must follow a true `hasNext()` (suspend) — use an explicit `while`, not Kotlin `for-in`. `nextOrNull()` is the cleanest alternative.
- Resource boundary = `TextElement.locator.href` changing (each href = one reading-order resource/spine item).
- Content service is `@ExperimentalReadiumApi` (opt-in required).
- Feed the NON-ZWSP Publication (Plan 06): use the TTS-original publication so injected U+200B word-break chars don't pollute extracted text.

**Confidence: high.**

---

## 8. Readium 3.3.0 — long-press → Locator (selection path + ActionMode fallback)

### 8a. Imports

```kotlin
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import android.view.ActionMode
```

### 8b. RECOMMENDED path — text selection → Locator

```
interface SelectableNavigator : VisualNavigator {
  suspend fun currentSelection(): Selection?   // SUSPEND
  fun clearSelection()
}
data class Selection(val locator: Locator, val rect: RectF?)   // rect nullable
```
`EpubNavigatorFragment` implements `SelectableNavigator`.

### 8c. Add a "Read from here" ActionMode item

```
// in EpubNavigatorFragment.Configuration DSL:
var selectionActionModeCallback: ActionMode.Callback?
var disableSelectionWhenProtected: Boolean   // @DelicateReadiumApi (DRM)
```

```kotlin
val cfg = EpubNavigatorFragment.Configuration {
  selectionActionModeCallback = object : ActionMode.Callback {
    override fun onCreateActionMode(m: ActionMode, menu: Menu): Boolean {
      menu.add(0, R.id.read_from_here, 0, "Read from here"); return true
    }
    override fun onPrepareActionMode(m: ActionMode, menu: Menu) = false
    override fun onActionItemClicked(m: ActionMode, item: MenuItem): Boolean {
      if (item.itemId == R.id.read_from_here) {
        lifecycleScope.launch {
          val sel = navigator.currentSelection() ?: return@launch
          startTtsAt(sel.locator)                 // -> publication.content(sel.locator)
          navigator.clearSelection()
        }
        m.finish(); return true
      }
      return false
    }
    override fun onDestroyActionMode(m: ActionMode) {}
  }
}
```

### 8d. "Play from top of screen" fallback (no selection needed)

```
@ExperimentalReadiumApi suspend fun VisualNavigator.firstVisibleElementLocator(): Locator?
// default impl returns currentLocator.value
```
```kotlin
val start = navigator.firstVisibleElementLocator() ?: navigator.currentLocator.value
startTtsAt(start)
```

### 8e. Why NOT tap-to-Locator

```
@ExperimentalReadiumApi fun VisualNavigator.addInputListener(listener: InputListener)
interface InputListener { fun onTap(event: TapEvent): Boolean = false; fun onDrag(...): Boolean; fun onKey(...): Boolean }
data class TapEvent(val point: PointF)   // org.readium.r2.navigator.input.TapEvent — POINT ONLY, no Locator
```
There is NO public point→Locator hit-test in 3.3.0. `TapEvent` carries only a `PointF`. The ONLY supported "press a paragraph → Locator" path is text SELECTION (8b/8c). There are THREE `TapEvent` classes in the repo — the one `EpubNavigatorFragment` emits is `org.readium.r2.navigator.input.TapEvent` (`val point: PointF`); the Compose-navigator ones are unrelated.

GESTURE CAVEAT: the existing Compose overlay consumes long-press via `detectTapGestures`, which SUPPRESSES Readium's native selection. To use the selection path, STOP consuming long-press over the WebView region (let it pass through so selection handles + ActionMode appear); single-tap can still be intercepted for transport UI.

**Confidence: high on the API surface; the gesture pass-through layering is the empirical risk (unresolved).**

---

## 9. Contradictions flagged

1. **Audio focus**: media3 background-playback docs imply automatic focus, but that automation is ExoPlayer-only. SimpleBasePlayer's `setAudioAttributes()` is state-reporting and does NOT manage focus. RESOLUTION: self-manage via `AudioManager.requestAudioFocus(AudioFocusRequest)` (section 5). Confirmed by androidx/media #2166, #1470.
2. **TextToSpeech stream routing**: `KEY_PARAM_STREAM`/`streamType` (legacy) vs `setAudioAttributes(USAGE_MEDIA)`. RESOLUTION: use `setAudioAttributes` with the SAME attributes as the focus request; do not also pass `KEY_PARAM_STREAM`.
3. **`utteranceId` source**: the `speak()` 4th-arg `utteranceId` vs Bundle `KEY_PARAM_UTTERANCE_ID`. RESOLUTION: the 4th arg is authoritative; do not rely on the Bundle key.
4. **media-tts removal vs media3**: removing `readium-navigator-media-tts`/`-media-common` does NOT remove media3 (core `readium-navigator` pulls media3 transitively). NOT a true contradiction — declare media3 explicitly regardless.
5. **`onError(String)` deprecated-but-abstract**: must still be overridden in Kotlin (delegate to `onError(id, ERROR)`); real codes arrive via `onError(String, Int)`.

---

## 10. Recommended file/package placement (verified package root `com.nextz.kanomtts`)

- Service: `com.nextz.kanomtts.playback.TtsPlaybackService` (matches manifest `.playback.TtsPlaybackService`).
- Player: `com.nextz.kanomtts.playback.TtsPlayer` (SimpleBasePlayer).
- TTS engine/controller: `com.nextz.kanomtts.playback` (or reuse `reader/engine`).
- Existing reader code lives under `com.nextz.kanomtts.reader.{engine,decoration}` — content-streaming + selection wiring belong alongside the navigator host there.
