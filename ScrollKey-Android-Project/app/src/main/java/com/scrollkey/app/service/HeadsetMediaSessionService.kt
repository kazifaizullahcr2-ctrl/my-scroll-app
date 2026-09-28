package com.scrollkey.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.scrollkey.app.R
import com.scrollkey.app.data.AppConfigRepository

/**
 * HeadsetMediaSessionService
 *
 * Maintains an active MediaSessionCompat to receive wired 3.5mm/Type-C headset button clicks
 * and Bluetooth AVRCP media commands (Next Track, Previous Track, Hook / Play-Pause).
 */
class HeadsetMediaSessionService : Service() {

    private var mediaSession: MediaSessionCompat? = null
    private var lastHookClickTime = 0L
    private var hookClickCount = 0

    companion object {
        private const val TAG = "HeadsetService"
        private const val CHANNEL_ID = "scrollkey_headset_channel"
        private const val NOTIFICATION_ID = 2001
        private const val DOUBLE_CLICK_TIMEOUT = 350L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "ScrollKeyHeadsetSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .build()
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return super.onMediaButtonEvent(mediaButtonIntent)

                    if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    val service = VolumeScrollAccessibilityService.instance
                    val configRepo = AppConfigRepository.getInstance(applicationContext)
                    val headsetConfig = configRepo.getHeadsetConfig()

                    if (service == null || !headsetConfig.wiredEnabled) {
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            handleHookButton(service)
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            if (headsetConfig.bluetoothMediaKeysToScroll) {
                                // Default Next = scroll down
                                triggerActiveScroll(service, scrollDown = true)
                                return true
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            if (headsetConfig.bluetoothMediaKeysToScroll) {
                                // Default Prev = scroll up
                                triggerActiveScroll(service, scrollDown = false)
                                return true
                            }
                        }
                    }

                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })

            isActive = true
        }
        Log.i(TAG, "MediaSession active for headset interception")
    }

    private fun handleHookButton(service: VolumeScrollAccessibilityService) {
        val now = System.currentTimeMillis()
        if (now - lastHookClickTime < DOUBLE_CLICK_TIMEOUT) {
            hookClickCount++
        } else {
            hookClickCount = 1
        }
        lastHookClickTime = now

        // Check if double click or single click
        if (hookClickCount == 1) {
            // Single click: Scroll down
            triggerActiveScroll(service, scrollDown = true)
        } else if (hookClickCount == 2) {
            // Double click: Scroll up
            triggerActiveScroll(service, scrollDown = false)
        }
    }

    private fun triggerActiveScroll(service: VolumeScrollAccessibilityService, scrollDown: Boolean) {
        val configRepo = AppConfigRepository.getInstance(applicationContext)
        val profile = configRepo.getDefaultOrActiveProfile()
        service.performScroll(scrollDown, profile)
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Headset Button Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps headset button intercept active for target scrolling"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScrollKey Headset Listener")
            .setContentText("Volume & Headset buttons active for scrolling")
            .setSmallIcon(R.drawable.ic_notification_scroll)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}