# nri — no reel instagram

InstaLimit is a companion Android app that keeps Instagram within a daily limit and replaces
Reels/stories with motivational messages.

**This is not a modified Instagram APK.** Instagram itself is untouched. This app uses
Android's usage-stat, accessibility, and overlay APIs to enforce your rules.

## Download

Prebuilt APK: https://github.com/12hrformat/nri/releases/latest

## Features

- Asks **"How much do you want to watch today?"** on first launch (1–6 hours).
- Tracks real Instagram foreground time and counts down your remaining budget.
- When the limit is reached: leaves Instagram, shows **"You've hit your limit."** plus a
  motivational message, and sends a notification.
- When Instagram's **Reels** tab is visible, an invisible overlay sits exactly on top of that
  tab and swallows taps — tapping it does nothing.
- If someone still gets into a Reel or someone else's story, it leaves that screen and shows
  a motivational message such as *"You're wasting your potential."*
- You can change today's limit at any time. There is no permanent app lock.
- Resets each local day. All data stays on the device.

## Setup after installing

1. Open InstaLimit and pick 1–6 hours.
2. Grant **Usage access** from the "Finish setup" card.
3. Enable **InstaLimit guard** under Settings → Accessibility → Downloaded services.
4. Grant **Display over other apps** (needed for the Reels tap shield).
5. Grant **Notifications** if prompted (Android 13+).

After that, open Instagram normally (or use the "Open Instagram" button). The guard runs in
the background.

## Build from source

Requires Android Studio Ladybug (2024.2.1)+ with Android SDK 35 and JDK 17+:

```bash
git clone https://github.com/12hrformat/nri.git
cd nri
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat assembleDebug`.

## Limitations

- Instagram's own Reels tab and story tray still exist inside Instagram. This app covers the
  Reels tab with a tap-swallowing overlay and closes Reels/story screens it detects; it cannot
  delete buttons from Instagram's UI.
- Detection uses screen class names and accessibility text, so it is heuristic and can miss
  or falsely trigger depending on Instagram's version. Individual blocks can be toggled off.
- Overlay tap-blocking requires the "Display over other apps" permission. Some phone makers
  (Samsung, Xiaomi, etc.) may also require allowing "Autostart" for the app.
- Accessibility services can be disabled at any time, so this is a self-control aid, not a
  security restriction.

## Source layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/instalimit/guard/MainActivity.kt` | Setup + dashboard |
| `app/src/main/java/com/instalimit/guard/BlockActivity.kt` | Motivational block screen |
| `app/src/main/java/com/instalimit/guard/InstaGuardService.kt` | Accessibility engine, usage polling, Reels tap shield |
| `app/src/main/java/com/instalimit/guard/Prefs.kt` | Local state and daily reset |
| `app/src/main/java/com/instalimit/guard/MotivationalQuotes.kt` | Block messages |
| `app/src/main/java/com/instalimit/guard/ui/Screens.kt` | Compose UI |
