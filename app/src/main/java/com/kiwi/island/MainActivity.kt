package com.kiwi.island

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(this)
        setContent { AppTheme { SettingsScreen(store) } }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun SettingsScreen(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by store.flow.collectAsState(IslandSettings())
    val running by IslandService.running.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val overlayOk = remember(tick) { Settings.canDrawOverlays(context) }
    val listenerOk = remember(tick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val notificationsOk = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }
    val phoneOk = remember(tick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }
    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        tick++
        if (IslandService.running.value) {
            IslandService.stop(context)
            IslandService.start(context)
        }
    }

    fun <T> save(key: Preferences.Key<T>, value: T) {
        scope.launch { store.set(key, value) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Island", style = MaterialTheme.typography.displaySmall)

        Section("Status") {
            SwitchRow("Show island", running, overlayOk) {
                if (it) IslandService.start(context) else IslandService.stop(context)
            }
            PermissionRow("Display over other apps", overlayOk, "Allow") {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            }
            PermissionRow("Media access (notification access)", listenerOk, "Allow") {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            PermissionRow("Phone state and call controls", phoneOk, "Allow") {
                phoneLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS))
            }
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionRow("Show the service notification", notificationsOk, "Allow") {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        Section("Position") {
            SwitchRow("Camera-aware mode", s.cameraAware) { save(Keys.cameraAware, it) }
            SliderRow("Gap below the camera", s.margin, 0f..24f, "dp") { save(Keys.margin, it) }
        }

        Section("Shape") {
            SliderRow("Width", s.width, 90f..200f, "dp") { save(Keys.width, it) }
            SliderRow("Height", s.height, 30f..48f, "dp") { save(Keys.height, it) }
            SliderRow("Expanded corner radius", s.radius, 24f..48f, "dp") { save(Keys.radius, it) }
            SwitchRow("Light appearance", s.light) { save(Keys.light, it) }
        }

        Section("Motion") {
            SliderRow("Bounce", s.intensity, 0f..1f, "") { save(Keys.intensity, it) }
            SliderRow("Speed", s.speed, 0.6f..1.6f, "x") { save(Keys.speed, it) }
        }

        Section("Activities") {
            Category.values().forEach { category ->
                SwitchRow(category.label, category.name !in s.disabled) { on ->
                    save(Keys.disabled, if (on) s.disabled - category.name else s.disabled + category.name)
                }
            }
            SwitchRow("Swipe down to expand", s.swipeToExpand) { save(Keys.swipeToExpand, it) }
        }

        Section("Timer and stopwatch") {
            val enabled = running
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10).forEach { minutes ->
                    FilledTonalButton(
                        onClick = { ActivityRepository.startTimer(minutes * 60_000L) },
                        enabled = enabled && Category.Timer.name !in s.disabled
                    ) { Text("$minutes min") }
                }
            }
            FilledTonalButton(
                onClick = { ActivityRepository.startStopwatch() },
                enabled = enabled && Category.Stopwatch.name !in s.disabled
            ) { Text("Start stopwatch") }
        }

        Section("Maps and delivery apps") {
            Text(
                "Navigation shows the live navigation notification from Google Maps or Waze. " +
                    "DoorDash, Talabat, Uber Eats and Deliveroo are tracked through their order notifications. " +
                    "Long press the island to open the source app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var extra by remember(s.tracked) { mutableStateOf(s.tracked) }
            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it },
                label = { Text("Extra app package ids, comma separated") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save(Keys.tracked, extra) }),
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).setPackage("com.google.android.apps.maps")
                    )
                }
            }) { Text("Open Google Maps") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange, enabled = enabled)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (granted) {
            Text("Granted", color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick) { Text(action) }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Text("%.1f%s".format(local, unit), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(local, { local = it }, valueRange = range, onValueChangeFinished = { onCommit(local) })
    }
}
