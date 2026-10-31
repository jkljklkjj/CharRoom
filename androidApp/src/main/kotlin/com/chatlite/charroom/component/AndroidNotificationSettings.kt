package com.chatlite.charroom.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chatlite.charroom.core.shouldRequestNotificationPermission
import com.chatlite.i18n.LocalStrings

/** A single controller is shared by the onboarding dialog and the Android settings row. */
@Composable
fun rememberNotificationPreferences(authenticated: Boolean): @Composable () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    val preferences =
        remember(context) {
            context.getSharedPreferences("notification-permission", Context.MODE_PRIVATE)
        }
    val strings = LocalStrings.current
    fun notificationsEnabled(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return manager.areNotificationsEnabled() &&
            manager
                .getNotificationChannel(
                    com.chatlite.charroom.core.ChatNotificationManager.CHANNEL_ID_MESSAGES
                )
                ?.importance != android.app.NotificationManager.IMPORTANCE_NONE
    }
    var enabled by remember { mutableStateOf(notificationsEnabled()) }
    var education by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            enabled = notificationsEnabled()
            if (!granted)
                Toast.makeText(context, strings["android.notifications.denied"], Toast.LENGTH_SHORT)
                    .show()
        }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = notificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(authenticated) {
        education = authenticated && !enabled && !preferences.getBoolean("explained", false)
    }
    fun dismiss() {
        education = false
        preferences.edit { putBoolean("explained", true) }
    }
    fun enable() {
        dismiss()
        val granted =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        val requested = preferences.getBoolean("requested", false)
        val rationale =
            Build.VERSION.SDK_INT >= 33 &&
                activity?.shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS
                ) == true
        if (
            Build.VERSION.SDK_INT >= 33 &&
                shouldRequestNotificationPermission(
                    Build.VERSION.SDK_INT,
                    granted,
                    requested,
                    rationale,
                )
        ) {
            preferences.edit { putBoolean("requested", true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
    }
    if (education)
        AlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(strings["android.notifications"]) },
            text = { Text(strings["android.notifications.explain"]) },
            confirmButton = {
                TextButton(onClick = ::enable) { Text(strings["android.notifications.enable"]) }
            },
            dismissButton = {
                TextButton(onClick = ::dismiss) { Text(strings["android.notifications.later"]) }
            },
        )
    return {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(strings["android.notifications"], style = MaterialTheme.typography.subtitle1)
            Text(
                strings[
                    if (enabled) "android.notifications.enabled"
                    else "android.notifications.disabled"],
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = .65f),
            )
            TextButton(onClick = ::enable) {
                Text(
                    strings[
                        if (enabled) "android.notifications.settings"
                        else "android.notifications.enable"]
                )
            }
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = .08f))
        }
    }
}
