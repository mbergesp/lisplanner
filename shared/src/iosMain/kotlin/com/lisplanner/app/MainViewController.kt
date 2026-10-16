package com.lisplanner.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.lisplanner.app.platform.IosPlatform
import com.lisplanner.app.platform.LocalPlatform
import com.lisplanner.app.platform.iosPlatform
import com.lisplanner.app.platform.readPickedFile
import com.lisplanner.app.ui.LisPlannerApp
import com.lisplanner.app.ui.theme.LisPlannerTheme
import kotlinx.coroutines.delay
import platform.Foundation.NSURL
import platform.UIKit.UIViewController

/** Llamar al arrancar la app (antes de mostrar la interfaz) para recibir los toques en notificaciones. */
fun setupPlatform() {
    iosPlatform
}

/** Fichero abierto desde otra app (Archivos, Mail, AirDrop…) con «Abrir con LIS Planner». */
fun openFile(url: NSURL) {
    readPickedFile(url)?.let { iosPlatform.importRequest.value = it }
}

/** Punto de entrada que usa SwiftUI. */
fun MainViewController(): UIViewController {
    val platform = iosPlatform
    return ComposeUIViewController {
        val openRequest by platform.openProjectRequest.collectAsState()
        val importRequest by platform.importRequest.collectAsState()
        CompositionLocalProvider(LocalPlatform provides platform) {
            LisPlannerTheme {
                Box(Modifier.fillMaxSize()) {
                    LisPlannerApp(
                        openProjectRequest = openRequest,
                        onOpenRequestHandled = { platform.openProjectRequest.value = null },
                        importRequest = importRequest,
                        onImportRequestHandled = { platform.importRequest.value = null },
                    )
                    MessageOverlay(platform, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }.also { platform.rootController = it }
}

/** Equivalente al Toast de Android. */
@Composable
private fun MessageOverlay(platform: IosPlatform, modifier: Modifier) {
    val message by platform.message.collectAsState()
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        shown = m
        delay(2800)
        platform.message.value = null
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.padding(bottom = 110.dp, start = 24.dp, end = 24.dp),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
            Text(
                shown,
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
