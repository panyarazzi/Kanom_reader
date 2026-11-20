package com.nextz.kanomtts.feature.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.repository.VoiceConfigRepository
import com.nextz.kanomtts.playback.Voice
import com.nextz.kanomtts.playback.VoiceCatalog
import com.nextz.kanomtts.playback.VoicePreviewer
import com.nextz.kanomtts.text.pipeline.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class VoiceSettingsViewModelTest {
    private lateinit var db: KanomDatabase
    private lateinit var repo: VoiceConfigRepository

    private val catalog = VoiceCatalog { lang ->
        listOf(
            Voice("low", "Low", lang, false, 100),
            Voice("hi", "High", lang, false, 500),
        )
    }
    private val previews = mutableListOf<Triple<Lang, Float, Float>>()
    private val previewer = VoicePreviewer { lang, _, speed, pitch ->
        previews += Triple(lang, speed, pitch)
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Use a direct (inline) executor so Room queries run synchronously on the calling thread.
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, KanomDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        repo = VoiceConfigRepository(db.voiceConfigDao())
    }
    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = VoiceSettingsViewModel(catalog, repo, previewer)

    @Test fun `loadVoices populates ordered voices for a language`() = runTest {
        val vm = vm()
        vm.uiState.first() // start collecting
        vm.loadVoices(Lang.TH)
        val th = vm.uiState.first().languages.first { it.lang == Lang.TH }
        assertEquals(listOf("hi", "low"), th.voices.map { it.id }) // quality desc
    }

    @Test fun `setSpeed clamps and persists`() = runTest {
        val vm = vm()
        vm.uiState.first()
        vm.setSpeed(Lang.TH, 9f)
        assertEquals(3.0f, repo.get("TH")!!.speed)
    }

    @Test fun `selectVoice persists the voice id`() = runTest {
        val vm = vm()
        vm.uiState.first()
        vm.selectVoice(Lang.EN, "hi")
        assertEquals("hi", repo.get("EN")!!.voiceId)
    }

    @Test fun `preview uses the saved effective config`() = runTest {
        val vm = vm()
        repo.set(VoiceConfigEntity("TH", "system", "hi", 2.0f, 0.8f))
        vm.preview(Lang.TH)
        assertEquals(Triple(Lang.TH, 2.0f, 0.8f), previews.single())
    }
}
