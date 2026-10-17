# مسجّل إذاعة القرآن — Quran Radio Recorder

An Android app that records programs from **إذاعة القرآن الكريم من القاهرة** (98.2 FM, Cairo) at a time you set, and saves each recording under the program's name.

The one thing it does:

1. Add a program — name, time, duration.
2. It records every day at that time and saves to `Music/إذاعة القرآن/<name>/<name> 2026-10-18.mp3`.
3. When the station moves the program (Ramadan, summer, winter), tap the time on the program's card and pick the new one.

## How it works

- **Source:** the station's official internet simulcast (MP3), copied byte-for-byte to the file — no microphone, no re-encoding, no quality loss.
- **Timing:** one `AlarmManager.setAlarmClock` alarm per program, always aimed at its next airing. Times are stored as wall-clock times and converted with the phone's time zone at scheduling time, so Egypt's summer-time switch is handled automatically.
- **Recording:** a foreground service (`specialUse`) with a wake lock. On a dropped connection it reconnects with backoff and keeps appending to the same file; gaps are reported as "partial".
- **Resilience:** alarms are re-armed on reboot, app update, clock/time-zone change and every app launch. If the phone restarts mid-program, the rest of the program is still recorded. If the process is killed, what was recorded so far is still saved.
- **Padding:** starts 1 minute early and runs 2 minutes late by default (Settings).
- **Setup checks:** the home screen shows what still needs allowing (notifications, battery optimisation, autostart on Xiaomi/Oppo/Realme/Infinix/Tecno/Huawei phones) with a button for each.

Android 10 or newer.

## Getting the APK

Every push to `main` builds the app on GitHub Actions and publishes it as a release.
On the phone, open **Releases → latest**, download `RadioRecorder-N.apk`, and install it (allow "install unknown apps" for the browser once).

Builds are signed with the fixed key in `app/debug.keystore`, so each new build installs over the previous one and keeps your programs.

## Building locally

Android Studio (current stable), open the project, run. Or `./gradlew assembleDebug`.

## Code map

| Path | What |
|---|---|
| `schedule/Scheduler.kt` | Next-airing computation, alarm arming, catch-up |
| `schedule/Receivers.kt` | Alarm firing; reboot / time change re-arming |
| `record/RecorderService.kt` | Foreground service running all active recordings |
| `record/StreamRecorder.kt` | Stream → file copy, reconnects, gap accounting |
| `record/MediaFiles.kt` | MediaStore file creation, ID3 tag |
| `data/Repo.kt` | Programs, recordings, settings (JSON + SharedPreferences) |
| `ui/` | Jetpack Compose screens (Arabic, right-to-left) |

## Testing reliability

```
adb shell dumpsys deviceidle force-idle   # force Doze, then wait for a scheduled program
adb shell dumpsys alarm | grep -A3 radiorecorder
adb shell dumpsys deviceidle unforce
```

Use the in-app **«جرّب التسجيل الآن»** button to confirm the stream URL works on your network.
