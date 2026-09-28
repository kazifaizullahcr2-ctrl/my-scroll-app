package com.scrollkey.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class ScrollMode {
    CONTINUOUS, PAGE, SNAP
}

data class AppProfile(
    val packageName: String,
    val appName: String,
    var enabled: Boolean = true,
    var scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
    var stepSize: Int = 350,
    var invertDirection: Boolean = false,
    var smoothDurationMs: Long = 180L,
    var longPressAction: String = "turbo_scroll",
    var feedbackHaptic: Boolean = true
)

data class HeadsetConfig(
    var wiredEnabled: Boolean = true,
    var bluetoothEnabled: Boolean = true,
    var bluetoothMediaKeysToScroll: Boolean = true,
    var hookSingleClick: String = "scroll_down",
    var hookDoubleClick: String = "scroll_up"
)

class AppConfigRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("scrollkey_prefs", Context.MODE_PRIVATE)
    private val profiles = mutableMapOf<String, AppProfile>()

    init {
        loadProfiles()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppConfigRepository? = null

        fun getInstance(context: Context): AppConfigRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppConfigRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun loadProfiles() {
        val raw = prefs.getString("saved_profiles", null)
        if (raw == null) {
            // Seed defaults for popular target apps
            seedDefaults()
            return
        }

        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val profile = AppProfile(
                    packageName = obj.getString("packageName"),
                    appName = obj.getString("appName"),
                    enabled = obj.optBoolean("enabled", true),
                    scrollMode = ScrollMode.valueOf(obj.optString("scrollMode", "CONTINUOUS")),
                    stepSize = obj.optInt("stepSize", 350),
                    invertDirection = obj.optBoolean("invertDirection", false),
                    smoothDurationMs = obj.optLong("smoothDurationMs", 180L),
                    longPressAction = obj.optString("longPressAction", "turbo_scroll"),
                    feedbackHaptic = obj.optBoolean("feedbackHaptic", true)
                )
                profiles[profile.packageName] = profile
            }
        } catch (e: Exception) {
            seedDefaults()
        }
    }

    private fun seedDefaults() {
        val defaults = listOf(
            AppProfile("com.android.chrome", "Google Chrome", true, ScrollMode.CONTINUOUS, 400),
            AppProfile("com.google.android.youtube", "YouTube Shorts", true, ScrollMode.SNAP, 700),
            AppProfile("com.zhiliaoapp.musically", "TikTok", true, ScrollMode.SNAP, 750),
            AppProfile("com.reddit.frontpage", "Reddit", true, ScrollMode.CONTINUOUS, 380),
            AppProfile("com.amazon.kindle", "Amazon Kindle", true, ScrollMode.PAGE, 800),
            AppProfile("com.instagram.android", "Instagram", true, ScrollMode.CONTINUOUS, 450),
            AppProfile("com.twitter.android", "X / Twitter", true, ScrollMode.CONTINUOUS, 380)
        )
        defaults.forEach { profiles[it.packageName] = it }
        saveProfiles()
    }

    fun getProfile(packageName: String): AppProfile? = profiles[packageName]

    fun getAllProfiles(): List<AppProfile> = profiles.values.toList()

    fun saveProfile(profile: AppProfile) {
        profiles[profile.packageName] = profile
        saveProfiles()
    }

    fun getDefaultOrActiveProfile(): AppProfile {
        return profiles.values.firstOrNull { it.enabled }
            ?: AppProfile("com.android.chrome", "Default", true, ScrollMode.CONTINUOUS, 350)
    }

    fun getHeadsetConfig(): HeadsetConfig {
        return HeadsetConfig(
            wiredEnabled = prefs.getBoolean("headset_wired", true),
            bluetoothEnabled = prefs.getBoolean("headset_bt", true),
            bluetoothMediaKeysToScroll = prefs.getBoolean("headset_bt_media", true)
        )
    }

    private fun saveProfiles() {
        val array = JSONArray()
        for (profile in profiles.values) {
            val obj = JSONObject().apply {
                put("packageName", profile.packageName)
                put("appName", profile.appName)
                put("enabled", profile.enabled)
                put("scrollMode", profile.scrollMode.name)
                put("stepSize", profile.stepSize)
                put("invertDirection", profile.invertDirection)
                put("smoothDurationMs", profile.smoothDurationMs)
                put("longPressAction", profile.longPressAction)
                put("feedbackHaptic", profile.feedbackHaptic)
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_profiles", array.toString()).apply()
    }
}