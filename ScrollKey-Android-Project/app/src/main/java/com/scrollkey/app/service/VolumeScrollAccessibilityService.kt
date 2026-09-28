package com.scrollkey.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scrollkey.app.data.AppConfigRepository
import com.scrollkey.app.data.AppProfile
import com.scrollkey.app.data.ScrollMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * VolumeScrollAccessibilityService
 *
 * Intercepts physical hardware volume button events (KEYCODE_VOLUME_UP and KEYCODE_VOLUME_DOWN)
 * via onKeyEvent(). When the currently foregrounded package matches an enabled app profile,
 * the key event is consumed (returning true) and converted into an accessibility scroll or
 * fluid touch swipe gesture.
 */
class VolumeScrollAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentPackageName: String? = null
    private lateinit var configRepo: AppConfigRepository
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "VolumeScrollService"
        var instance: VolumeScrollAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        configRepo = AppConfigRepository.getInstance(applicationContext)
        vibrator = getSystemService(VibrATOR_SERVICE) as? Vibrator
        Log.i(TAG, "VolumeScrollAccessibilityService connected & active")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "VolumeScrollAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty() && !pkg.startsWith("com.android.systemui")) {
                currentPackageName = pkg
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service onInterrupt called")
    }

    /**
     * Intercepts physical hardware volume keys BEFORE system adjusts media/ringer volume.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // Only handle volume up and volume down
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        // Determine active package
        val activePkg = currentPackageName ?: rootInActiveWindow?.packageName?.toString() ?: ""
        val profile = configRepo.getProfile(activePkg)

        // If app is not configured or disabled, let the system handle normal volume change
        if (profile == null || !profile.enabled) {
            return super.onKeyEvent(event)
        }

        // Handle Long-press behavior
        if (event.isLongPress && profile.longPressAction == "system_volume") {
            // Allow long press to pass through and adjust volume
            return false
        }

        // Process only on KEY_DOWN (or repeat for fast scroll)
        if (action == KeyEvent.ACTION_DOWN) {
            val isVolumeDown = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            // Default: Volume Down = scroll downwards; Volume Up = scroll upwards
            val scrollDown = if (profile.invertDirection) !isVolumeDown else isVolumeDown

            performScroll(scrollDown, profile)

            if (profile.feedbackHaptic) {
                triggerHaptic()
            }
        }

        // Return true to consume the key event and prevent system volume dialog
        return true
    }

    /**
     * Performs scroll action using either synthetic swipe gesture or node accessibility action.
     */
    fun performScroll(scrollDown: Boolean, profile: AppProfile) {
        serviceScope.launch {
            when (profile.scrollMode) {
                ScrollMode.SNAP -> {
                    // For short videos / feed cards (TikTok, Reels, Shorts), execute a deliberate card swipe
                    dispatchSnapSwipe(scrollDown)
                }
                ScrollMode.PAGE -> {
                    // Page jump (Kindle, PDF reader, articles)
                    if (!tryNodeScroll(scrollDown)) {
                        dispatchPageSwipe(scrollDown)
                    }
                }
                ScrollMode.CONTINUOUS -> {
                    // Smooth pixel scrolling (Chrome, Reddit, Feed)
                    dispatchFluidSwipe(scrollDown, profile.stepSize, profile.smoothDurationMs)
                }
            }
        }
    }

    /**
     * Attempts standard AccessibilityNodeInfo scroll (ACTION_SCROLL_FORWARD / BACKWARD)
     */
    private fun tryNodeScroll(scrollDown: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(root)
        return if (scrollableNode != null) {
            val action = if (scrollDown) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val result = scrollableNode.performAction(action)
            scrollableNode.recycle()
            result
        } else {
            false
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val scrollable = findScrollableNode(child)
            if (scrollable != null) return scrollable
            child?.recycle()
        }
        return null
    }

    /**
     * Dispatches a synthetic touch swipe curve via GestureDescription.
     * Works across any app including Canvas, WebViews, Flutter, and video feeds.
     */
    private fun dispatchFluidSwipe(scrollDown: Boolean, stepSize: Int, durationMs: Long) {
        val dm: DisplayMetrics = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()

        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val delta = stepSize.coerceIn(100, (screenHeight * 0.7f).toInt()).toFloat()

        // To scroll down, user pulls finger UP from centerY + delta/2 to centerY - delta/2
        val startY: Float
        val endY: Float

        if (scrollDown) {
            startY = centerY + (delta / 2f)
            endY = centerY - (delta / 2f)
        } else {
            startY = centerY - (delta / 2f)
            endY = centerY + (delta / 2f)
        }

        val swipePath = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, durationMs.coerceAtLeast(100L)))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSnapSwipe(scrollDown: Boolean) {
        val dm: DisplayMetrics = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()

        val centerX = screenWidth / 2f
        val startY = if (scrollDown) screenHeight * 0.78f else screenHeight * 0.22f
        val endY = if (scrollDown) screenHeight * 0.18f else screenHeight * 0.82f

        val swipePath = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 180L))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun dispatchPageSwipe(scrollDown: Boolean) {
        val dm: DisplayMetrics = resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()

        val centerX = screenWidth / 2f
        val startY = if (scrollDown) screenHeight * 0.85f else screenHeight * 0.15f
        val endY = if (scrollDown) screenHeight * 0.15f else screenHeight * 0.85f

        val swipePath = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 240L))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun triggerHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20)
        }
    }
}