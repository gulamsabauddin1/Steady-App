# Steady for Android

Steady is an Android-first focus locker for people who want to protect a short window for a task. Pick a duration and selected apps. During an active session, opening a selected app returns you to Home. You can stop the session from Steady at any time.

## Prototype features

- User-created routines with a title, optional intention, minimum and usual actions, optional extra action and fallback plan, selected days, local start time, session length, and per-routine app block list.
- Routine schedules and focus history saved on-device; alarms are restored after device restart, app update, and local time or timezone changes.
- A five-section bottom menu for Today, Routines, Focus, Progress, and Settings.
- Routine completion check-ins for the small, usual, or extra action; skipped runs include a reason in Progress.
- A weekly summary and a gentle recovery prompt after a missed routine, with options to edit or pause it.
- Custom focus sessions from 5 to 180 minutes, with 15-, 25-, and 45-minute shortcuts.
- An optional intention shown during the session.
- Searchable list of installed user apps to pause.
- Live time remaining and progress indicator.
- Optional ongoing notification with a countdown and an **End focus** action. Notification permission can be enabled or disabled in the app.
- On-device history of the last 20 sessions, including early endings.
- Optional notification when a timer completes.
- A matching vector logo in the app header and launcher.

## Permissions and privacy

- **Accessibility Service:** required for app blocking. Android asks the user to enable it from Settings. Steady reads only the package name from window-change events while checking whether a focus session is active. It does not read screen content, upload app usage, or block system apps and the default dialer.
- **Alarms & reminders access:** on Android 12 and later, allow exact alarm access for routines and session-end alerts to run close to their selected times. If access is off, Steady falls back to inexact alarms, which Android may delay, especially in Doze or battery-saver modes. Use the **Allow exact routine times** button in Steady to open this setting.
- **Boot receiver:** used to restore enabled routine alarms and an unexpired session after reboot. Routine times follow the phone’s local timezone.
- **Notifications:** optional. When enabled, a quiet ongoing countdown appears during a session, with an action to end it. A completion note appears when the timer finishes. Focus sessions still work if notification access is denied.

The selected app list and active session are stored locally with Android SharedPreferences. The blocker is intentionally not tamper-proof: users retain access to Home, Settings, calls, and the in-app end-session control.

## Create an automatic routine

1. Enable Steady's Accessibility Service from Android Settings so app blocking can work.
2. Optionally enable the Focus notification switch and allow notifications for a quiet countdown and an **End focus** action.
3. Tap **Add** under **Your routines**. Choose a name, optional intention, local 24-hour start time, repeat days, duration, and apps to pause.
4. Save the routine. It is scheduled automatically and restored after restart or app update. Keep Steady installed and its Accessibility Service enabled for blocking to continue.

Exact alarms are used when Android's Alarms & reminders access is granted. Without it, routine and session-end alarms fall back to inexact alarms and may be delayed, especially during battery-saving or Doze periods. Routine times use the phone's local timezone. Routines also require the Accessibility service; if it is disabled, the alarm cannot begin app blocking.

## Build and test

Open this directory in Android Studio with JDK 17 or newer and Android SDK 35 installed, then run the `app` configuration on an Android 8.0+ device or emulator. From a shell, use the checked-in Gradle wrapper:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

The unit tests cover focus-session duration and input rules, routine scheduling, and weekly progress summaries. A device test is still needed for reboot delivery, the Android Accessibility Service, app switching, notification permission, and behavior across phone manufacturers.

The app ID remains `com.steady.app`; the current source version is `0.5.0` (version code 6). Android Studio writes a debug APK to `app/build/outputs/apk/debug/app-debug.apk`. Install over the previous APK to update in place and keep local data, provided both APKs use the same signing key. Do not uninstall first unless Android reports a signature mismatch, since uninstalling removes local session history, routines, and app selections.

## Current scope

The first build focuses on timed app blocking. Habit planning, cue reminders, recovery after a missed routine, and weekly reviews remain in the product brief as later product work.
