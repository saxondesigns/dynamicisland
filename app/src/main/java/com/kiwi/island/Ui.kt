package com.kiwi.island

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

val Orange = Color(0xFFFF9F0A)
val Green = Color(0xFF30D158)
val Blue = Color(0xFF0A84FF)
val Red = Color(0xFFFF453A)
val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun rememberNow(intervalMs: Long): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(intervalMs)
        }
    }
    return now
}

fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun fmtFine(ms: Long): String {
    val c = (ms / 10).coerceAtLeast(0)
    return "%02d:%02d.%02d".format(c / 6000, c / 100 % 60, c % 100)
}

@Composable
fun RoundButton(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun FilledRoundButton(icon: ImageVector, container: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun Bar(fraction: Float, fill: Color, track: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(fill)
        )
    }
}

@Composable
fun Art(bitmap: Bitmap?, size: Dp, radius: Dp, fg: Color, fallback: ImageVector = Icons.Rounded.MusicNote) {
    val shape = RoundedCornerShape(radius)
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(image, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(
            Modifier.size(size).clip(shape).background(fg.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(fallback, null, tint = fg, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
fun Equaliser(active: Boolean, color: Color) {
    val heights = if (active) {
        val transition = rememberInfiniteTransition(label = "eq")
        listOf(380, 520, 440, 600, 460).map { ms ->
            transition.animateFloat(
                0.2f,
                1f,
                infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar"
            ).value
        }
    } else {
        listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
    }
    Row(
        Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { h ->
            Box(Modifier.width(2.5.dp).fillMaxHeight(h).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun ProgressRing(fraction: Float, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val arc = Size(size.width - stroke.width, size.height - stroke.width)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arc, style = stroke)
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = stroke)
    }
}
