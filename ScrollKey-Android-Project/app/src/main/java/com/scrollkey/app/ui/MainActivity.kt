package com.scrollkey.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollkey.app.data.AppConfigRepository
import com.scrollkey.app.data.AppProfile
import com.scrollkey.app.service.HeadsetMediaSessionService
import com.scrollkey.app.service.VolumeScrollAccessibilityService

class MainActivity : ComponentActivity() {

    private lateinit var configRepo: AppConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configRepo = AppConfigRepository.getInstance(applicationContext)

        // Start headset media service
        startService(Intent(this, HeadsetMediaSessionService::class.java))

        setContent {
            ScrollKeyTheme {
                MainScreen(
                    isServiceRunning = VolumeScrollAccessibilityService.instance != null,
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onIgnoreBattery = { requestIgnoreBatteryOptimization() },
                    profiles = configRepo.getAllProfiles(),
                    onToggleProfile = { profile, enabled ->
                        profile.enabled = enabled
                        configRepo.saveProfile(profile)
                    }
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestIgnoreBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    onOpenAccessibility: () -> Unit,
    onIgnoreBattery: () -> Unit,
    profiles: List<AppProfile>,
    onToggleProfile: (AppProfile, Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScrollKey Volume Controller") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Service status card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isServiceRunning) "✓ Accessibility Service Active" else "⚠ Service Inactive",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isServiceRunning)
                            "Physical volume and headset buttons are ready to scroll selected apps."
                        else
                            "Grant accessibility permissions so ScrollKey can detect volume keys.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!isServiceRunning) {
                        Button(onClick = onOpenAccessibility) {
                            Text("Enable in Accessibility Settings")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Selected Target Apps", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(profiles) { profile ->
                    var isEnabled by remember { mutableStateOf(profile.enabled) }
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.appName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${profile.packageName} • ${profile.scrollMode.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    isEnabled = checked
                                    onToggleProfile(profile, checked)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollKeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}