package com.volumebutton

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

/**
 * Foreground overlay service running in an isolated process (:overlay).
 * Software-only rendering (android:hardwareAccelerated="false" in manifest),
 * eliminating GPU drivers, EGL/GL mtrack, and Skia native heap overhead.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: FloatingView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private lateinit var positionStore: PositionStore
    private lateinit var volumeController: VolumeController
    private lateinit var settingsStore: SettingsStore

    // Settings values cache
    private var currentIdleOpacity: Float = 0.85f
    private var showVolumePopup: Boolean = true
    private var hapticFeedbackEnabled: Boolean = true

    // Touch and drag tracking variables
    private var touchSlop: Int = 0
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var initialTouchRawX = 0f
    private var initialTouchRawY = 0f
    private var isDragging = false
    private var snapAnimator: ValueAnimator? = null

    // Hold-to-repeat handlers for volume up / down
    private lateinit var volumeUpRepeater: HoldToRepeatHandler
    private lateinit var volumeDownRepeater: HoldToRepeatHandler

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        positionStore = PositionStore(this)
        settingsStore = SettingsStore(this)
        settingsStore.isServiceRunning = true
        volumeController = VolumeController(this)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        // Load initial settings
        currentIdleOpacity = settingsStore.idleOpacity
        showVolumePopup = settingsStore.showVolumePopup
        hapticFeedbackEnabled = settingsStore.hapticFeedback

        setupRepeatHandlers()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlayView()
        broadcastState(true)
    }

    private fun setupRepeatHandlers() {
        volumeUpRepeater = HoldToRepeatHandler(
            initialDelayMs = HOLD_INITIAL_DELAY_MS,
            repeatIntervalMs = HOLD_REPEAT_INTERVAL_MS
        ) {
            volumeController.volumeUp(showUi = showVolumePopup)
        }

        volumeDownRepeater = HoldToRepeatHandler(
            initialDelayMs = HOLD_INITIAL_DELAY_MS,
            repeatIntervalMs = HOLD_REPEAT_INTERVAL_MS
        ) {
            volumeController.volumeDown(showUi = showVolumePopup)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_QUERY_STATE -> {
                broadcastState(true)
            }
            ACTION_UPDATE_SETTINGS -> {
                if (intent.hasExtra(EXTRA_IDLE_OPACITY)) {
                    currentIdleOpacity = intent.getFloatExtra(EXTRA_IDLE_OPACITY, currentIdleOpacity)
                    if (!isDragging) {
                        floatingView?.setOpacity(currentIdleOpacity)
                    }
                }
                if (intent.hasExtra(EXTRA_SHOW_VOLUME_POPUP)) {
                    showVolumePopup = intent.getBooleanExtra(EXTRA_SHOW_VOLUME_POPUP, showVolumePopup)
                }
                if (intent.hasExtra(EXTRA_HAPTIC_FEEDBACK)) {
                    hapticFeedbackEnabled = intent.getBooleanExtra(EXTRA_HAPTIC_FEEDBACK, hapticFeedbackEnabled)
                }
            }
        }
        return START_STICKY
    }

    private fun broadcastState(running: Boolean) {
        val intent = Intent(ACTION_SERVICE_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_RUNNING, running)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView() {
        if (floatingView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = FloatingView(this)
        floatingView = view
        view.setOpacity(currentIdleOpacity)

        val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Measure view dimensions
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val initialViewWidth = view.measuredWidth.coerceAtLeast(140)
        val initialViewHeight = view.measuredHeight.coerceAtLeast(300)

        // Calculate initial screen coordinates
        val edge = positionStore.getEdge()
        val bounds = getSafeBounds(initialViewWidth, initialViewHeight)
        val fraction = positionStore.getFraction()
        val (initX, initY) = EdgeSnapCalculator.calculateCoordinates(edge, fraction, bounds)

        params.x = initX
        params.y = initY
        layoutParams = params

        // Handle button touches with hold-to-repeat and drag fallback
        view.btnVolumeUp.setOnTouchListener { v, event ->
            handleVolumeButtonTouch(v, event, volumeUpRepeater)
        }

        view.btnVolumeDown.setOnTouchListener { v, event ->
            handleVolumeButtonTouch(v, event, volumeDownRepeater)
        }

        // Handle speaker icon touch: tap shows volume slider, drag moves button
        view.handleContainer.setOnTouchListener { _, event ->
            handleHandleTouch(event)
        }

        view.overlayRoot.setOnTouchListener { _, event ->
            handleHandleTouch(event)
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }

        // Trigger garbage collection shortly after startup to compact the ART heap
        view.postDelayed({
            System.gc()
        }, 500L)
    }

    private fun handleVolumeButtonTouch(
        view: View,
        event: MotionEvent,
        repeater: HoldToRepeatHandler
    ): Boolean {
        val currentParams = layoutParams ?: return false
        val wm = windowManager ?: return false
        val overlay = floatingView ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialWindowX = currentParams.x
                initialWindowY = currentParams.y
                initialTouchRawX = event.rawX
                initialTouchRawY = event.rawY
                isDragging = false

                view.isPressed = true
                if (hapticFeedbackEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                // Fix Bug 1: Only call repeater.start(), which dispatches onTrigger() immediately.
                // Do not call onSinglePress() here to avoid doubling volume increments.
                repeater.start()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchRawX
                val dy = event.rawY - initialTouchRawY

                if (!isDragging && hypot(dx, dy) > touchSlop) {
                    isDragging = true
                    view.isPressed = false
                    repeater.stop()
                    overlay.setOpacity(FloatingView.ACTIVE_OPACITY)
                }

                if (isDragging) {
                    val bounds = getSafeBounds(overlay.width, overlay.height)
                    currentParams.x = (initialWindowX + dx.toInt()).coerceIn(bounds.minX, bounds.maxX)
                    currentParams.y = (initialWindowY + dy.toInt()).coerceIn(bounds.minY, bounds.maxY)
                    wm.updateViewLayout(overlay, currentParams)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                repeater.stop()
                if (isDragging) {
                    animateSnapToNearestEdge()
                }
                return true
            }
        }
        return false
    }

    private fun handleHandleTouch(event: MotionEvent): Boolean {
        val currentView = floatingView ?: return false
        val currentParams = layoutParams ?: return false
        val wm = windowManager ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialWindowX = currentParams.x
                initialWindowY = currentParams.y
                initialTouchRawX = event.rawX
                initialTouchRawY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchRawX
                val dy = event.rawY - initialTouchRawY

                if (!isDragging && hypot(dx, dy) > touchSlop) {
                    isDragging = true
                    currentView.setOpacity(FloatingView.ACTIVE_OPACITY)
                }

                if (isDragging) {
                    val bounds = getSafeBounds(currentView.width, currentView.height)
                    currentParams.x = (initialWindowX + dx.toInt()).coerceIn(bounds.minX, bounds.maxX)
                    currentParams.y = (initialWindowY + dy.toInt()).coerceIn(bounds.minY, bounds.maxY)
                    wm.updateViewLayout(currentView, currentParams)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    animateSnapToNearestEdge()
                } else {
                    currentView.animateVisualTapFeedback()
                    if (hapticFeedbackEnabled) {
                        currentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    volumeController.showVolumeUi()
                }
                return true
            }
        }
        return false
    }

    private fun animateSnapToNearestEdge() {
        val view = floatingView ?: return
        val params = layoutParams ?: return
        val wm = windowManager ?: return

        val bounds = getSafeBounds(view.width, view.height)
        val snapResult = EdgeSnapCalculator.findNearestEdge(params.x, params.y, bounds)

        val startX = params.x
        val startY = params.y
        val targetX = snapResult.targetX
        val targetY = snapResult.targetY

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                try {
                    wm.updateViewLayout(view, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.setOpacity(currentIdleOpacity)
                    positionStore.savePosition(snapResult.edge, snapResult.fraction)
                }
            })
            start()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        snapAnimator?.cancel()
        isDragging = false

        floatingView?.post {
            val view = floatingView ?: return@post
            val params = layoutParams ?: return@post
            val wm = windowManager ?: return@post

            val bounds = getSafeBounds(view.width, view.height)
            val edge = positionStore.getEdge()
            val fraction = positionStore.getFraction()
            val (newX, newY) = EdgeSnapCalculator.calculateCoordinates(edge, fraction, bounds)

            params.x = newX
            params.y = newY
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getSafeBounds(viewWidth: Int, viewHeight: Int): SafeBounds {
        val actualViewWidth = if (viewWidth > 0) viewWidth else 140
        val actualViewHeight = if (viewHeight > 0) viewHeight else 300

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager?.currentWindowMetrics
            val screenBounds = metrics?.bounds
            val insets = metrics?.windowInsets?.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )

            val screenW = screenBounds?.width() ?: 1080
            val screenH = screenBounds?.height() ?: 2400

            val insetLeft = (insets?.left ?: 0).coerceAtLeast(GESTURE_MARGIN_PX)
            val insetRight = (insets?.right ?: 0).coerceAtLeast(GESTURE_MARGIN_PX)
            val insetTop = (insets?.top ?: 64).coerceAtLeast(64)
            val insetBottom = (insets?.bottom ?: 96).coerceAtLeast(96)

            EdgeSnapCalculator.calculateSafeBounds(
                screenWidth = screenW,
                screenHeight = screenH,
                viewWidth = actualViewWidth,
                viewHeight = actualViewHeight,
                insetLeft = insetLeft,
                insetTop = insetTop,
                insetRight = insetRight,
                insetBottom = insetBottom
            )
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)

            val screenW = displayMetrics.widthPixels
            val screenH = displayMetrics.heightPixels

            EdgeSnapCalculator.calculateSafeBounds(
                screenWidth = screenW,
                screenHeight = screenH,
                viewWidth = actualViewWidth,
                viewHeight = actualViewHeight,
                insetLeft = GESTURE_MARGIN_PX,
                insetTop = 72,
                insetRight = GESTURE_MARGIN_PX,
                insetBottom = 120
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_minus),
            getString(R.string.notification_stop_action),
            stopPendingIntent
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_volume_handle)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        settingsStore.isServiceRunning = false
        broadcastState(false)
        volumeUpRepeater.stop()
        volumeDownRepeater.stop()
        snapAnimator?.cancel()
        floatingView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        floatingView = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "floating_volume_overlay_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.volumebutton.action.STOP"
        const val ACTION_UPDATE_SETTINGS = "com.volumebutton.action.UPDATE_SETTINGS"
        const val ACTION_QUERY_STATE = "com.volumebutton.action.QUERY_STATE"
        const val ACTION_SERVICE_STATE_CHANGED = "com.volumebutton.action.SERVICE_STATE_CHANGED"

        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_IDLE_OPACITY = "extra_idle_opacity"
        const val EXTRA_SHOW_VOLUME_POPUP = "extra_show_volume_popup"
        const val EXTRA_HAPTIC_FEEDBACK = "extra_haptic_feedback"

        private const val SNAP_ANIMATION_DURATION_MS = 180L
        private const val HOLD_INITIAL_DELAY_MS = 400L
        private const val HOLD_REPEAT_INTERVAL_MS = 120L
        private const val GESTURE_MARGIN_PX = 8

        var isRunning: Boolean = false
            private set
    }
}
