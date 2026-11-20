package com.nextz.kanomtts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nextz.kanomtts.feature.library.ImportViewModel
import com.nextz.kanomtts.ui.navigation.KanomApp
import com.nextz.kanomtts.ui.theme.KanomTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    /** Active only while ReaderScreen is visible; forwards hardware/remote keys to its navigator. */
    private var readerKeyHandler: ((KeyEvent) -> Boolean)? = null

    fun setReaderKeyHandler(handler: ((KeyEvent) -> Boolean)?) {
        readerKeyHandler = handler
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = readerKeyHandler
        if (handler != null) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "KanomReaderKeys",
                    "action=${event.action} keyCode=${event.keyCode} scanCode=${event.scanCode} " +
                        "deviceId=${event.deviceId} source=${event.source} repeat=${event.repeatCount}"
                )
            }
            if (handler(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private val importViewModel: ImportViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KanomTheme { KanomApp() } }
        requestNotificationPermissionIfNeeded()
        handleEpubIntent(intent)
    }

    /**
     * On Android 13+ POST_NOTIFICATIONS is a runtime permission; the media-playback
     * notification (and its lock-screen controls) won't show without it. Request it
     * once on launch if not already granted. No-op on API < 33 (granted at install).
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEpubIntent(intent)
    }

    private fun handleEpubIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri = intent.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // file:// URIs and some providers don't support persistable permissions.
            }
            importViewModel.importFrom(uri)
        }
    }
}
