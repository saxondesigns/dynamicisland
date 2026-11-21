package com.kiwi.island

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TimerActivity(val endsAt: Long, val totalMs: Long) : IslandActivity {
    override val id = "timer"
    override val category = Category.Timer
    override val priority = 30
    override val expandedHeight = 150f
    override val trailingWidth get() = if (totalMs >= 3_600_000L) 58f else 40f

    @Composable
    override fun Leading(fg: Color) {
        Icon(Icons.Rounded.Timer, null, tint = Orange, modifier = Modifier.size(18.dp))
    }

    @Composable
    override fun Trailing(fg: Color) {
        val now = rememberNow(250)
        Text(fmt(endsAt - now), color = Orange, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = Tabular, maxLines = 1, softWrap = false)
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        val now = rememberNow(200)
        val remaining = (endsAt - now).coerceAtLeast(0)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Timer", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(fmt(remaining), color = fg, fontSize = 40.sp, fontWeight = FontWeight.Light, style = Tabular)
                }
                RoundButton(Icons.Rounded.Close, fg) { ActivityRepository.cancelTimer() }
            }
            Bar(1f - remaining.toFloat() / totalMs, Orange, fg.copy(alpha = 0.18f))
        }
    }
}

data class StopwatchActivity(val baseMs: Long, val startedAt: Long?) : IslandActivity {
    override val id = "stopwatch"
    override val category = Category.Stopwatch
    override val priority = 28
    override val expandedHeight = 150f
    override val trailingWidth get() = 44f

    fun elapsed(now: Long) = baseMs + (startedAt?.let { now - it } ?: 0L)

    @Composable
    override fun Leading(fg: Color) {
        Icon(Icons.Rounded.AvTimer, null, tint = Green, modifier = Modifier.size(18.dp))
    }

    @Composable
    override fun Trailing(fg: Color) {
        val now = if (startedAt != null) rememberNow(250) else 0L
        Text(fmt(elapsed(now)), color = Green, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = Tabular, maxLines = 1, softWrap = false)
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        val now = if (startedAt != null) rememberNow(33) else 0L
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stopwatch", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(fmtFine(elapsed(now)), color = fg, fontSize = 38.sp, fontWeight = FontWeight.Light, style = Tabular)
            }
            RoundButton(if (startedAt != null) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, fg) {
                ActivityRepository.toggleStopwatch()
            }
            RoundButton(Icons.Rounded.Close, fg) { ActivityRepository.stopStopwatch() }
        }
    }
}

data class ChargingActivity(val percent: Int) : IslandActivity {
    override val id = "charging"
    override val category = Category.Charging
    override val priority = 20
    override val expandedHeight = 120f

    @Composable
    override fun Leading(fg: Color) {
        Icon(Icons.Rounded.BatteryChargingFull, null, tint = Green, modifier = Modifier.size(18.dp))
    }

    @Composable
    override fun Trailing(fg: Color) {
        Text("$percent%", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Charging", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("$percent%", color = fg, fontSize = 36.sp, fontWeight = FontWeight.Light)
                }
                Icon(Icons.Rounded.BatteryChargingFull, null, tint = Green, modifier = Modifier.size(32.dp))
            }
            Bar(percent / 100f, Green, fg.copy(alpha = 0.18f))
        }
    }
}

data class BatteryActivity(val percent: Int) : IslandActivity {
    override val id = "battery"
    override val category = Category.Battery
    override val priority = 19
    override val expandedHeight = 120f

    @Composable
    override fun Leading(fg: Color) {
        Icon(Icons.Rounded.BatteryAlert, null, tint = Red, modifier = Modifier.size(18.dp))
    }

    @Composable
    override fun Trailing(fg: Color) {
        Text("$percent%", color = Red, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Low battery", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("$percent%", color = fg, fontSize = 36.sp, fontWeight = FontWeight.Light)
                }
                Icon(Icons.Rounded.BatteryAlert, null, tint = Red, modifier = Modifier.size(32.dp))
            }
            Bar(percent / 100f, Red, fg.copy(alpha = 0.18f))
        }
    }
}

data class CallActivity(val ringing: Boolean, val caller: String?, val startedAt: Long) : IslandActivity {
    override val id = "call"
    override val category = Category.Call
    override val priority = 50
    override val expandedHeight = 140f
    override val leadingWidth get() = if (ringing) 24f else 58f
    override val trailingWidth get() = if (ringing) 58f else 24f

    @Composable
    override fun Leading(fg: Color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Call, null, tint = Green, modifier = Modifier.size(16.dp))
            if (!ringing) {
                Spacer(Modifier.width(5.dp))
                val now = rememberNow(500)
                Text(fmt(now - startedAt), color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = Tabular, maxLines = 1, softWrap = false)
            }
        }
    }

    @Composable
    override fun Trailing(fg: Color) {
        if (ringing) {
            Text("Incoming", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        } else {
            Equaliser(true, Green)
        }
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        val context = LocalContext.current
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (ringing) "Incoming call" else "On call", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (!caller.isNullOrBlank()) {
                    Text(caller, color = fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!ringing) {
                    val now = rememberNow(500)
                    Text(fmt(now - startedAt), color = sub, fontSize = 15.sp, style = Tabular)
                }
            }
            FilledRoundButton(Icons.Rounded.CallEnd, Red) { CallActions.end(context) }
            if (ringing) {
                Spacer(Modifier.width(12.dp))
                FilledRoundButton(Icons.Rounded.Call, Green) { CallActions.answer(context) }
            }
        }
    }
}

data class NavigationActivity(
    val title: String,
    val text: String,
    val sub: String?,
    val icon: Bitmap?,
    override val open: PendingIntent?
) : IslandActivity {
    override val id = "nav"
    override val category = Category.Navigation
    override val priority = 26
    override val expandedHeight = 150f
    override val trailingWidth get() = 64f

    @Composable
    override fun Leading(fg: Color) {
        Art(icon, 22.dp, 4.dp, Blue, Icons.Rounded.Navigation)
    }

    @Composable
    override fun Trailing(fg: Color) {
        Text(title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Art(icon, 56.dp, 8.dp, Blue, Icons.Rounded.Navigation)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text, color = sub, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!this@NavigationActivity.sub.isNullOrBlank()) {
                    Text(this@NavigationActivity.sub, color = sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

data class MediaActivity(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val position: Long,
    val positionAt: Long,
    val speed: Float,
    val duration: Long,
    val art: Bitmap?
) : IslandActivity {
    override val id = "media"
    override val category = Category.Media
    override val priority = 10
    override val expandedHeight = 190f
    override val trailingWidth get() = 24f

    @Composable
    override fun Leading(fg: Color) {
        Art(art, 22.dp, 11.dp, fg)
    }

    @Composable
    override fun Trailing(fg: Color) {
        Equaliser(playing, Orange)
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(art, 56.dp, 12.dp, fg)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Equaliser(playing, Orange)
            }
            if (duration > 0) {
                val now = rememberNow(500)
                val current = if (playing) position + ((now - positionAt) * speed).toLong() else position
                val shown = current.coerceIn(0, duration)
                Column {
                    Bar(shown.toFloat() / duration, fg, fg.copy(alpha = 0.18f))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmt(shown), color = sub, fontSize = 11.sp, style = Tabular)
                        Spacer(Modifier.weight(1f))
                        Text("-" + fmt(duration - shown), color = sub, fontSize = 11.sp, style = Tabular)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(Icons.Rounded.FastRewind, fg) {
                    ActivityRepository.mediaController?.transportControls?.skipToPrevious()
                }
                RoundButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, fg) {
                    val controls = ActivityRepository.mediaController?.transportControls
                    if (playing) controls?.pause() else controls?.play()
                }
                RoundButton(Icons.Rounded.FastForward, fg) {
                    ActivityRepository.mediaController?.transportControls?.skipToNext()
                }
            }
        }
    }
}

data class AppActivity(
    override val id: String,
    override val category: Category,
    override val priority: Int,
    val appLabel: String,
    val icon: Bitmap?,
    val title: String,
    val text: String,
    val progress: Float?,
    val indeterminate: Boolean,
    val accent: Color,
    override val open: PendingIntent?
) : IslandActivity {
    override val expandedHeight = if (progress != null) 140f else 120f

    @Composable
    override fun Leading(fg: Color) {
        Art(icon, 22.dp, 6.dp, if (accent == Color.Unspecified) fg else accent, Icons.Rounded.Notifications)
    }

    @Composable
    override fun Trailing(fg: Color) {
        val c = if (accent == Color.Unspecified) fg else accent
        when {
            progress != null -> ProgressRing(progress, c)
            indeterminate -> CircularProgressIndicator(Modifier.size(16.dp), color = c, strokeWidth = 2.dp)
            else -> Icon(Icons.Rounded.Notifications, null, tint = c, modifier = Modifier.size(14.dp))
        }
    }

    @Composable
    override fun Expanded(fg: Color, sub: Color) {
        val c = if (accent == Color.Unspecified) fg else accent
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(icon, 44.dp, 10.dp, c, Icons.Rounded.Notifications)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(appLabel, color = sub, fontSize = 12.sp, maxLines = 1)
                    Text(title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (text.isNotBlank()) {
                        Text(text, color = sub, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (progress != null) Bar(progress, c, fg.copy(alpha = 0.18f))
        }
    }
}
