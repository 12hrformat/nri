# nri — no reel instagram

InstaLimit is a companion Android app that keeps Instagram within a daily limit and replaces
Reels/stories with motivational messages.

**This is not a modified Instagram APK.** Instagram itself is untouched. This app uses
Android's usage-stat and accessibility APIs to enforce your rules.

## Features

- Asks **"How much do you want to watch today?"** on first launch (1–6 hours).
- Tracks real Instagram foreground time and counts down your remaining budget.
- When the limit is reached: leaves Instagram, shows **"You've hit your limit."** and a
  motivational message, then sends a notification.
- When a Reel or someone else's story is detected: leaves that screen and shows a
  motivational message such as *"You're wasting your potential."*
- Lets you change today's limit at any time. There is no permanent app lock.
- Resets each local day. All data stays on the device.

## Download

Grab the latest prebuilt APK from the **Releases** page:
https://github.com/12hrformat/nri/releases

Install it on Android 8.0 (API 26) or newer, then:

1. Open the app and pick 1–6 hours.
2. Grant **Usage access** from the "Finish setup" card.
3. Enable **InstaLimit guard** under Settings → Accessibility → Downloaded services.
4. Grant **Notifications** if prompted (Android 13+).

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

- Instagram's own Reels tab and story tray still exist inside Instagram. This app reacts to
  them and closes those screens; it cannot delete buttons from Instagram's UI.
- Reels/stories detection uses screen class names and accessibility text, so it is heuristic
  and can miss or falsely trigger depending on Instagram's version. Individual blocks can be
  toggled off in the app.
- Accessibility services can be disabled by the user at any time, so this is a self-control
  aid rather than a security restriction.

## Source layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/instalimit/guard/MainActivity.kt` | Setup + dashboard |
| `app/src/main/java/com/instalimit/guard/BlockActivity.kt` | Motivational block screen |
| `app/src/main/java/com/instalimit/guard/InstaGuardService.kt` | Accessibility engine + usage polling |
| `app/src/main/java/com/instalimit/guard/Prefs.kt` | Local state and daily reset |
| `app/src/main/java/com/instalimit/guard/MotivationalQuotes.kt` | Block messages |
| `app/src/main/java/com/instalimit/guard/ui/Screens.kt` | Compose UI |
