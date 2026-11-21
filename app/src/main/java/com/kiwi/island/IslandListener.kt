package com.kiwi.island

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

val NAV_PACKAGES = setOf("com.google.android.apps.maps", "com.waze")

class IslandListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this, IslandListener::class.java) }
    private val keys = HashMap<String, String>()
    private var controller: MediaController? = null
    private var settingsJob: Job? = null

    @Volatile
    private var tracked: Set<String> = DEFAULT_TRACKED

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onSessionDestroyed() = pick(manager.getActiveSessions(component))
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        pick(it.orEmpty())
    }

    override fun onListenerConnected() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            SettingsStore(this@IslandListener).flow.collect { tracked = it.trackedPackages }
        }
        manager.addOnActiveSessionsChangedListener(sessionsListener, component)
        pick(manager.getActiveSessions(component))
        activeNotifications?.forEach { handle(it, true) }
    }

    override fun onListenerDisconnected() {
        manager.removeOnActiveSessionsChangedListener(sessionsListener)
        controller?.unregisterCallback(callback)
        controller = null
        ActivityRepository.mediaController = null
        ActivityRepository.remove("media")
        keys.values.toSet().forEach { ActivityRepository.remove(it) }
        keys.clear()
        settingsJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, false)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        keys.remove(sbn.key)?.let { ActivityRepository.remove(it) }
    }

    private fun handle(sbn: StatusBarNotification, seed: Boolean) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        if (n.category == Notification.CATEGORY_CALL) {
            CallSource.setCaller(title.ifBlank { null })
            return
        }
        if (e.containsKey(Notification.EXTRA_MEDIA_SESSION) || n.category == Notification.CATEGORY_TRANSPORT) return

        // Whatever this notification showed before is replaced by what it shows now (or by nothing).
        val previous = keys[sbn.key]
        fun dropPrevious() {
            keys.remove(sbn.key)?.let { ActivityRepository.remove(it) }
        }
        if (title.isBlank() && text.isBlank()) return dropPrevious()

        val pkg = sbn.packageName
        val ongoing = (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0
        val max = e.getInt(Notification.EXTRA_PROGRESS_MAX)
        val progress = e.getInt(Notification.EXTRA_PROGRESS)
        val indeterminate = e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val hasProgress = max > 0 || indeterminate
        val fraction = if (max > 0 && !indeterminate) progress.toFloat() / max else null
        val sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val isNav = n.category == Notification.CATEGORY_NAVIGATION || (pkg in NAV_PACKAGES && ongoing)

        fun app(id: String, category: Category, priority: Int, accent: Color) = AppActivity(
            id, category, priority, appLabel(sbn, e), iconOf(n), title, text,
            fraction, indeterminate, accent, n.contentIntent
        )

        val activity: IslandActivity
        var lifetime = 0L
        when {
            isNav -> activity = NavigationActivity(title, text, sub, iconOf(n), n.contentIntent)
            pkg in tracked -> {
                activity = app("delivery:$pkg", Category.Delivery, 24, Orange)
                if (!ongoing && !hasProgress) {
                    if (seed) return dropPrevious()
                    lifetime = 10_000L
                }
            }
            hasProgress -> activity = app("download:$pkg:${sbn.id}", Category.Download, 15, Blue)
            ongoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0 -> return dropPrevious()
            seed -> return dropPrevious()
            else -> {
                activity = app("notif", Category.Notification, 12, Color.Unspecified)
                lifetime = 6_000L
            }
        }
        if (previous != null && previous != activity.id) ActivityRepository.remove(previous)
        keys[sbn.key] = activity.id
        if (lifetime > 0) ActivityRepository.putFor(activity, lifetime) else ActivityRepository.put(activity)
    }

    private fun appLabel(sbn: StatusBarNotification, extras: Bundle): String =
        extras.getString("android.substName") ?: runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

    private fun iconOf(n: Notification): Bitmap? = runCatching {
        val icon = n.getLargeIcon() ?: n.smallIcon
        icon?.loadDrawable(this)?.toBitmap(96, 96)
    }.getOrNull()

    private fun pick(list: List<MediaController>) {
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (controller != null && next?.sessionToken == controller?.sessionToken) {
            publishMedia()
            return
        }
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback)
        ActivityRepository.mediaController = next
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller ?: return ActivityRepository.remove("media")
        val meta = c.metadata ?: return ActivityRepository.remove("media")
        val state = c.playbackState ?: return ActivityRepository.remove("media")
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return ActivityRepository.remove("media")
        if (state.state == PlaybackState.STATE_NONE || state.state == PlaybackState.STATE_STOPPED) {
            return ActivityRepository.remove("media")
        }
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ActivityRepository.put(
            MediaActivity(
                title = title,
                artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                playing = state.state == PlaybackState.STATE_PLAYING,
                position = state.position,
                positionAt = state.lastPositionUpdateTime,
                speed = state.playbackSpeed,
                duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                art = art
            )
        )
    }
}
