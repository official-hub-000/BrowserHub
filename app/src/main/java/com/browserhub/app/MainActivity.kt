package com.browserhub.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.browserhub.app.ai.configureAppCheck
import com.browserhub.app.ui.BrowserHubApp
import com.browserhub.app.ui.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleIncomingIntent(intent)
        setContent {
            BrowserHubApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }


    /**
     * Persists the last uncaught exception to filesDir/crash-last.txt and puts a
     * short form on the clipboard. The app cannot be built with a debugger here,
     * so this is how a crash reports itself: reopen the app and the file is
     * already on disk, and the clipboard has the one line needed to find it.
     *
     * It never swallows the crash - the previous handler still runs, so system
     * crash reporting and the normal force-close behaviour are unchanged.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val text = buildString {
                    appendLine(throwable.toString())
                    throwable.stackTrace.take(24).forEach { appendLine("  at $it") }
                }
                val file = java.io.File(filesDir, "crash-last.txt")
                file.writeText(text)

                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("BrowserHub crash", text.take(900))
                )
            } catch (_: Throwable) {
                // Logging must never make things worse.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val dataUrl = intent?.dataString
        if (intent?.action == Intent.ACTION_VIEW && !dataUrl.isNullOrBlank()) {
            viewModel.openNewTab(isIncognito = false, initialUrl = dataUrl)
        }
    }
}
