package com.lisplanner.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeXML
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class PickerDelegate(val onResult: (PickedFile?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onResult((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(::readPickedFile))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onResult(null)
}

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onPicked)
    // El selector guarda una referencia débil a su delegado: lo mantenemos vivo aquí.
    val delegate = remember { PickerDelegate { current(it) } }
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeXML, UTTypeItem), asCopy = true)
            picker.delegate = delegate
            iosPlatform.topController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@Composable
actual fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onResult)
    return remember {
        {
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { granted, _ ->
                dispatch_async(dispatch_get_main_queue()) {
                    iosPlatform.reminders.refreshPermission()
                    current(granted)
                }
            }
        }
    }
}

/** iOS no tiene botón «Atrás»: la app ya muestra flechas de vuelta en sus pantallas. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
