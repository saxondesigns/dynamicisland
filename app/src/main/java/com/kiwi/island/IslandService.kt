package com.kiwi.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

class IslandService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = OverlayOwner()
    private val animX = Animatable(0f)
    private val animY = Animatable(0f)
    private lateinit var vm: IslandViewModel
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var view: ComposeView
    private var attached = false
    private var layoutJob: Job? = null
    private var created = false
    private lateinit var display: Display
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val relayout = MutableStateFlow(0)

    // Rotation and fold changes move or remove the cutout, so re-place the island when the display changes.
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) relayout.value++
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        running.value = true
        owner.create()
        created = true
        vm = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[IslandViewModel::class.java]

        display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val windowContext = createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        windowManager = windowContext.getSystemService(WindowManager::class.java)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "island"
        }

        view = ComposeView(windowContext).apply {
            ViewTreeLifecycleOwner.set(this, owner)
            ViewTreeSavedStateRegistryOwner.set(this, owner)
            ViewTreeViewModelStoreOwner.set(this, owner)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) vm.setExpanded(false)
                false
            }
            setContent { Host() }
        }

        ChargingSource.start(this)
        CallSource.start(this)

        displayManager.registerDisplayListener(displayListener, null)

        scope.launch {
            vm.awaitSettings()
            combine(vm.settings, relayout) { settings, _ -> settings }.collect { settings ->
                place(settings)
                if (!attached) {
                    windowManager.addView(view, params)
                    owner.resume()
                    attached = true
                }
            }
        }
    }

    @Composable
    private fun Host() {
        val settings by vm.settings.collectAsState()
        val activities by vm.activities.collectAsState()
        val expanded by vm.expanded.collectAsState()
        val cutout by vm.cutout.collectAsState()
        IslandOverlay(
            settings = settings,
            activities = activities,
            expanded = expanded,
            cutout = cutout,
            onExpandedChange = vm::setExpanded,
            onPin = vm::pin,
            onOpen = { activity ->
                activity.open?.let { sendOpen(it) }
                vm.setExpanded(false)
            }
        )
    }

    private suspend fun place(s: IslandSettings) {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets
        val d = resources.displayMetrics.density
        val rect = topCutout(insets)
        val statusTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            .takeIf { it > 0 } ?: statusBarHeightFallback()
        val targetX: Float
        val targetY: Float

        if (s.cameraAware && rect != null) {
            val info = CutoutInfo(rect.width() / d, rect.height() / d)
            vm.cutout.value = info
            val pillHeightPx = max(s.height, info.heightDp) * d
            targetY = (rect.exactCenterY() - pillHeightPx / 2f).coerceAtLeast(0f)
            targetX = rect.exactCenterX() - metrics.bounds.width() / 2f
        } else {
            vm.cutout.value = null
            targetY = (max(rect?.bottom ?: 0, statusTop) + s.margin * d)
            targetX = 0f
        }

        if (!attached) {
            animX.snapTo(targetX)
            animY.snapTo(targetY)
            params.x = targetX.roundToInt()
            params.y = targetY.roundToInt()
        }

        layoutJob?.cancel()
        layoutJob = scope.launch {
            launch {
                animY.animateTo(targetY, spring(dampingRatio = 0.85f, stiffness = 300f)) {
                    params.y = value.roundToInt()
                    refresh()
                }
            }
            vm.expanded.collectLatest { open ->
                animX.animateTo(if (open) 0f else targetX, spring(dampingRatio = 0.8f, stiffness = 380f)) {
                    params.x = value.roundToInt()
                    refresh()
                }
            }
        }
    }

    /**
     * Overlay windows often report no cutout in their own insets, so fall back to the display's cutout.
     * Returns null in landscape or on devices without a top cutout, which drops back to independent mode.
     */
    private fun topCutout(insets: WindowInsets): Rect? {
        val fromWindow = insets.displayCutout?.boundingRectTop
        if (fromWindow != null && !fromWindow.isEmpty) return fromWindow
        return display.cutout?.boundingRectTop?.takeIf { !it.isEmpty }
    }

    private fun statusBarHeightFallback(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /**
     * Android 14+ only lets the sender of a PendingIntent start an activity from the background
     * if it opts in. The island is a visible overlay, so that is allowed here.
     */
    private fun sendOpen(pi: android.app.PendingIntent) {
        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= 34) {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        }
        runCatching { pi.send(this, 0, null, null, null, null, options.toBundle()) }
    }

    private fun refresh() {
        if (attached && view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("island", "Island", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, "island")
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("Island is running")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        layoutJob?.cancel()
        scope.cancel()
        if (::display.isInitialized) displayManager.unregisterDisplayListener(displayListener)
        ChargingSource.stop(this)
        CallSource.stop(this)
        if (::view.isInitialized) {
            view.disposeComposition()
            if (attached) runCatching { windowManager.removeView(view) }
        }
        // onCreate returns early without overlay permission; a lifecycle that was never created can't be destroyed.
        if (created) owner.destroy()
        running.value = false
        super.onDestroy()
    }

    companion object {
        val running = MutableStateFlow(false)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IslandService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandService::class.java))
        }
    }
}
