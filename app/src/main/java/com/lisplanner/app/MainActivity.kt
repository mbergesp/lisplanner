package com.lisplanner.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.lisplanner.app.notifications.Notifier
import com.lisplanner.app.platform.AndroidPlatform
import com.lisplanner.app.platform.LocalPlatform
import com.lisplanner.app.platform.PickedFile
import com.lisplanner.app.platform.readPickedFile
import com.lisplanner.app.ui.LisPlannerApp
import com.lisplanner.app.ui.theme.LisPlannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val platform by lazy { AndroidPlatform(applicationContext) }

    /** Proyecto a abrir al tocar una notificación. */
    private var openProjectRequest by mutableStateOf<String?>(null)

    /** Fichero .mpp / .xml abierto o compartido con la app. */
    private var importRequest by mutableStateOf<PickedFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            CompositionLocalProvider(LocalPlatform provides platform) {
                LisPlannerTheme {
                    LisPlannerApp(
                        openProjectRequest = openProjectRequest,
                        onOpenRequestHandled = { openProjectRequest = null },
                        importRequest = importRequest,
                        onImportRequestHandled = { importRequest = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        intent.getStringExtra(Notifier.EXTRA_PROJECT_ID)?.let { openProjectRequest = it }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) {
            lifecycleScope.launch {
                importRequest = withContext(Dispatchers.IO) { readPickedFile(applicationContext, uri) }
            }
        }
    }
}
