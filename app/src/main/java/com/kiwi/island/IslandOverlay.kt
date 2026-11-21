package com.kiwi.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun IslandOverlay(
    settings: IslandSettings,
    activities: List<IslandActivity>,
    expanded: Boolean,
    cutout: CutoutInfo?,
    onExpandedChange: (Boolean) -> Unit,
    onPin: (String) -> Unit,
    onOpen: (IslandActivity) -> Unit
) {
    val primary = activities.firstOrNull()
    val secondary = activities.getOrNull(1)
    val lastSecondary = remember { arrayOfNulls<IslandActivity>(1) }
    if (secondary != null) lastSecondary[0] = secondary
    val bubbleActivity = lastSecondary[0]

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat()

    val damping = 0.95f - 0.45f * settings.intensity
    val stiffness = 380f * settings.speed
    val widthSpec = spring<Dp>(
        dampingRatio = if (expanded) damping else min(1f, damping + 0.15f),
        stiffness = if (expanded) stiffness else stiffness * 1.2f
    )
    val heightSpec = spring<Dp>(
        dampingRatio = if (expanded) max(0.3f, damping - 0.04f) else min(1f, damping + 0.12f),
        stiffness = (if (expanded) stiffness else stiffness * 1.2f) * 1.1f
    )

    val fg = if (settings.light) Color.Black else Color.White
    val sub = fg.copy(alpha = 0.6f)
    val bg = if (settings.light) Color(0xFFF2F2F7) else Color.Black

    val baseH = max(settings.height, cutout?.heightDp ?: 0f)
    val idleW = if (cutout != null) cutout.widthDp + 16f else settings.width
    // Centre gap is the camera (or a stand-in gap in independent mode); each side is sized to its content.
    val centreGap = if (cutout != null) cutout.widthDp + 12f else 44f
    val side = if (primary != null) max(primary.leadingWidth, primary.trailingWidth) else 0f
    val activeW = max(idleW, centreGap + 2f * (16f + side))
    val compactW = if (primary != null) activeW else idleW
    val expandedW = min(screenWidth - 24f, 372f)
    val expandedH = primary?.expandedHeight ?: 96f
    val topPad = if (cutout != null) baseH + 6f else 18f

    val width by animateDpAsState((if (expanded) expandedW else compactW).dp, widthSpec, label = "width")
    val height by animateDpAsState((if (expanded) expandedH else baseH).dp, heightSpec, label = "height")
    val corner by animateDpAsState(
        if (expanded) settings.radius.dp else (baseH / 2f).dp,
        widthSpec,
        label = "corner"
    )
    val bubbleSpaceRaw by animateDpAsState(
        if (secondary != null && !expanded) (baseH + 8f).dp else 0.dp,
        widthSpec,
        label = "bubble"
    )
    val progress by animateFloatAsState(
        if (expanded) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 450f * settings.speed),
        label = "progress"
    )
    // Underdamped springs can dip below the resting size; never let the pill get smaller than idle.
    val bubbleSpace = bubbleSpaceRaw.coerceAtLeast(0.dp)
    val shownWidth = width.coerceAtLeast(idleW.dp)
    val shownHeight = height.coerceAtLeast(baseH.dp)
    val showExpanded by remember { derivedStateOf { progress > 0.02f } }
    val showCompact by remember { derivedStateOf { progress < 0.98f } }

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(1f, 800f), label = "press")
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var raw by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { 28.dp.toPx() }
    val limitPx = with(density) { 20.dp.toPx() }
    val currentExpanded by rememberUpdatedState(expanded)
    val currentChange by rememberUpdatedState(onExpandedChange)
    val currentPrimary by rememberUpdatedState(primary)
    val currentOpen by rememberUpdatedState(onOpen)
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))

    Box {
        Row(verticalAlignment = Alignment.Top) {
            Spacer(Modifier.width(bubbleSpace))
            Box(
                Modifier
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .graphicsLayer {
                        val s = if (expanded) 1f - 0.02f * press else 1f + 0.04f * press
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .width(shownWidth)
                    .height(shownHeight)
                    .clip(shape)
                    .background(bg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onLongPress = { currentPrimary?.let { currentOpen(it) } },
                            onTap = { currentChange(!currentExpanded) }
                        )
                    }
                    .pointerInput(settings.swipeToExpand) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                raw = 0f
                                scope.launch { offsetY.stop() }
                            },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                raw += amount
                                val shown = if (raw > 0f) limitPx * (1f - 1f / (raw / limitPx + 1f)) else 0f
                                scope.launch { offsetY.snapTo(shown) }
                                if (!currentExpanded && settings.swipeToExpand && raw > thresholdPx) {
                                    currentChange(true)
                                    raw = 0f
                                } else if (currentExpanded && raw < -thresholdPx) {
                                    currentChange(false)
                                    raw = 0f
                                }
                            },
                            onDragEnd = {
                                raw = 0f
                                scope.launch { offsetY.animateTo(0f, spring(0.5f, 420f)) }
                            },
                            onDragCancel = {
                                raw = 0f
                                scope.launch { offsetY.animateTo(0f, spring(0.5f, 420f)) }
                            }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                if (showCompact) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (1f - progress * 2.5f).coerceIn(0f, 1f) * (1f - 0.15f * press) }
                    ) {
                        AnimatedContent(
                            targetState = primary,
                            contentKey = { it?.id },
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                            label = "compact"
                        ) { activity ->
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                activity?.Leading(fg)
                                Spacer(Modifier.weight(1f))
                                activity?.Trailing(fg)
                            }
                        }
                    }
                }
                if (showExpanded) {
                    Box(
                        Modifier
                            .requiredSize(expandedW.dp, expandedH.dp)
                            .graphicsLayer {
                                val a = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                                alpha = a * (1f - 0.15f * press)
                                scaleX = 0.9f + 0.1f * a
                                scaleY = 0.9f + 0.1f * a
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                    ) {
                        AnimatedContent(
                            targetState = primary,
                            contentKey = { it?.id },
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                            label = "expanded"
                        ) { activity ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(start = 22.dp, end = 22.dp, top = topPad.dp, bottom = 18.dp)
                            ) {
                                if (activity == null) {
                                    Text(
                                        "No live activities",
                                        color = sub,
                                        fontSize = 15.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else {
                                    activity.Expanded(fg, sub)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.width(bubbleSpace).height(baseH.dp)) {
                val scale = (bubbleSpace.value / (baseH + 8f)).coerceIn(0f, 1f)
                if (scale > 0.01f && bubbleActivity != null) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = scale
                            }
                            .size(baseH.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .clickable { onPin(bubbleActivity.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        bubbleActivity.Leading(fg)
                    }
                }
            }
        }
    }
}
