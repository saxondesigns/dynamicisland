package com.kiwi.island

import android.app.PendingIntent
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class Category(val label: String) {
    Timer("Timers"),
    Stopwatch("Stopwatch"),
    Charging("Charging"),
    Battery("Low battery"),
    Call("Phone calls"),
    Navigation("Navigation"),
    Delivery("Delivery tracking"),
    Download("Downloads and progress"),
    Notification("Notifications"),
    Media("Media playback")
}

interface IslandActivity {
    val id: String
    val category: Category
    val priority: Int
    val expandedHeight: Float
    val open: PendingIntent? get() = null

    /** Width (dp) the compact leading / trailing content needs, so the pill can size itself to fit. */
    val leadingWidth: Float get() = 24f
    val trailingWidth: Float get() = 40f

    @Composable
    fun Leading(fg: Color)

    @Composable
    fun Trailing(fg: Color)

    @Composable
    fun Expanded(fg: Color, sub: Color)
}

data class CutoutInfo(val widthDp: Float, val heightDp: Float)

object ActivityRepository {
    private val items = MutableStateFlow<Map<String, IslandActivity>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    val activities = items.map { map -> map.values.sortedByDescending { it.priority } }

    @Volatile
    var mediaController: android.media.session.MediaController? = null

    fun get(id: String): IslandActivity? = items.value[id]

    /** Publishes an activity that stays until removed. Cancels any pending expiry for the same id. */
    fun put(activity: IslandActivity) {
        jobs.remove(activity.id)?.cancel()
        items.update { it + (activity.id to activity) }
    }

    /** Updates an activity only if it is already showing, keeping its expiry timer untouched. */
    fun refresh(activity: IslandActivity) {
        items.update { if (activity.id in it) it + (activity.id to activity) else it }
    }

    fun putFor(activity: IslandActivity, durationMs: Long) {
        jobs.remove(activity.id)?.cancel()
        items.update { it + (activity.id to activity) }
        jobs[activity.id] = scope.launch {
            delay(durationMs)
            jobs.remove(activity.id)
            items.update { it - activity.id }
        }
    }

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        items.update { it - id }
    }

    fun startTimer(durationMs: Long) {
        putFor(TimerActivity(SystemClock.elapsedRealtime() + durationMs, durationMs), durationMs)
    }

    fun cancelTimer() = remove("timer")

    fun startStopwatch() {
        put(StopwatchActivity(0L, SystemClock.elapsedRealtime()))
    }

    fun toggleStopwatch() {
        val current = get("stopwatch") as? StopwatchActivity ?: return
        val now = SystemClock.elapsedRealtime()
        put(
            if (current.startedAt != null) current.copy(baseMs = current.elapsed(now), startedAt = null)
            else current.copy(startedAt = now)
        )
    }

    fun stopStopwatch() = remove("stopwatch")
}

val AccentUnset: Color = Color.Unspecified
