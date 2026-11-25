# InstaLimit

A companion Android app for Instagram that enforces a daily watch limit and replaces
Reels/stories with motivational messages.

**It does not modify, patch, or repackage Instagram.** Your original Instagram APK stays
untouched. InstaLimit uses Android's own usage-stat and accessibility APIs to watch when
Instagram is open and to close it when you've had enough.

## What it does

- On first launch asks **"How much do you want to watch today?"** with options **1–6 hours**.
- Counts real time spent in Instagram (via `UsageStatsManager`, with an accessibility-based
  fallback if usage access is not granted).
- When the limit is reached it **leaves Instagram** and shows **"You've hit your limit."**
  plus a motivational message.
- When the **Reels** tab is visible, an invisible overlay sits exactly over that tab and
  swallows taps — tapping Reels does nothing. This needs the "Display over other apps"
  permission.
- If someone still gets into a Reel or story, it leaves that screen and shows a motivational
  message instead (e.g. *"You're wasting your potential."*).
- Sends a notification when it blocks something.
- Resets the timer automatically at local midnight. The chosen daily limit is remembered.
- Lets you **change today's limit at any time** — including after hitting it. There is no
  "app can never be opened again" lock.

## Permissions you must enable

1. **Usage access** – found in the app's "Finish setup" card. Required for accurate timing.
2. **Accessibility service** – find **InstaLimit guard** under
   Settings → Accessibility → Downloaded services, and switch it on. This is what allows
   the app to close Instagram and detect Reels/stories.
3. **Display over other apps** – required for the invisible Reels tap shield.
4. **Notifications** (Android 13+) – for alerts.

Nothing leaves your device. All state is stored in local `SharedPreferences`.

## How to build

You need **Android Studio Ladybug (2024.2.1) or newer**, Android SDK 35, and JDK 17
(Android Studio bundles a suitable JDK).

1. Open Android Studio → **Open** → select `C:\Users\dragon\Documents\instagram-control`.
2. Let Gradle sync and download dependencies.
3. Connect a device or start an emulator, then press **Run** (▶).
   - Or build an installable APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   - The APK appears in `app/build/outputs/apk/debug/app-debug.apk`.
4. Install it on your phone, open it, pick a limit, then grant the three permissions.

Command line (optional, if you have the Android SDK configured):

```powershell
cd C:\Users\dragon\Documents\instagram-control
.\gradlew assembleDebug
```

## Important limitations — please read

- **This is not a modified Instagram.** Instagram itself is untouched, so its own UI still
  contains the Reels tab and story tray. InstaLimit reacts to them and kicks you out. It
  cannot permanently remove those buttons from Instagram's interface.
- Reels/stories detection uses accessibility labels and screen class names. Instagram
  changes its UI often, so detection is **heuristic** and may occasionally miss a screen or
  trigger falsely. Use the toggles in the app to turn individual blocks on/off.
- Accessibility-based blocking is a self-control aid, not a security boundary. Android lets
  you disable any accessibility service at any time.
- Instagram's own notifications and links can still launch Instagram directly. The guard
  catches it as soon as the app appears, but a brief flash of the app may be visible.
- The "fallback" timer (used when usage access is off) counts time between Instagram opening
  and closing, so it can be slightly less accurate than usage stats.

## Files of interest

| File | Purpose |
| --- | --- |
| `app/src/main/java/com/instalimit/guard/MainActivity.kt` | Setup + dashboard UI |
| `app/src/main/java/com/instalimit/guard/BlockActivity.kt` | Motivational block screen |
| `app/src/main/java/com/instalimit/guard/InstaGuardService.kt` | Accessibility engine and usage polling |
| `app/src/main/java/com/instalimit/guard/Prefs.kt` | Local state and daily reset |
| `app/src/main/java/com/instalimit/guard/MotivationalQuotes.kt` | The messages shown when blocked |
| `app/src/main/java/com/instalimit/guard/ui/Screens.kt` | Compose screens |
