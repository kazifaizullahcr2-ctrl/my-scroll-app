# ScrollKey: Android Volume Button & Headset Scroller

ScrollKey allows you to use your device's physical hardware **Volume Up / Volume Down** buttons, wired headsets (3.5mm / USB-C with inline controls), and connected **Bluetooth headsets / earbuds** to scroll up or down inside selected Android apps!

---

## ⚡ How It Works on Android

1. **Accessibility Service (`VolumeScrollAccessibilityService`)**:
   - Registered with `canRequestFilterKeyEvents="true"` and `canPerformGestures="true"`.
   - Intercepts `KeyEvent.KEYCODE_VOLUME_UP` and `KeyEvent.KEYCODE_VOLUME_DOWN` in `onKeyEvent()`.
   - Identifies the current foreground app's package name.
   - If the app is in the enabled list, consumes the key event (`return true;`), preventing the annoying system volume slider from popping up.
   - Injects smooth gestures via `dispatchGesture()` (fluid continuous swipe) or triggers `AccessibilityNodeInfo.ACTION_SCROLL_FORWARD / BACKWARD`.

2. **Headset & Bluetooth Controller (`HeadsetMediaSessionService`)**:
   - Hosts a foreground `MediaSessionCompat` that receives media button intents.
   - Handles wired headset hook clicks (single click = scroll down, double click = scroll up).
   - Handles Bluetooth headset AVRCP controls (`KEYCODE_MEDIA_NEXT` and `KEYCODE_MEDIA_PREVIOUS`).

---

## 🛠️ Building the Project in Android Studio

1. Unzip the downloaded `ScrollKey-Android-Project.zip`.
2. Open Android Studio (Hedgehog, Iguana, Jellyfish, or newer).
3. Select **File > Open** and choose the unzipped folder.
4. Let Gradle sync dependencies.
5. Connect your Android device via USB with USB Debugging enabled.
6. Click **Run > Run 'app'** (`Shift + F10`).

---

## 📱 Device Setup & Permissions

Once installed on your phone:
1. Open **ScrollKey**.
2. Tap **Enable in Accessibility Settings**.
3. Under *Downloaded Apps / Installed Services*, find **ScrollKey Service** and switch it **ON**.
4. Allow permission to *"View and control screen"* and *"Filter physical key events"*.
5. (Recommended) Whitelist ScrollKey from aggressive Android Battery Optimization so the service runs reliably in background.

### Optional: ADB Command for Direct Accessibility Grant
If configuring via command line or automated testing:
```bash
adb shell settings put secure enabled_accessibility_services com.scrollkey.app/com.scrollkey.app.service.VolumeScrollAccessibilityService
adb shell settings put secure accessibility_enabled 1
```
