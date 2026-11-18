# Settings

Settings that make the stick quieter and lighter. Amazon's privacy switches have to be set in the menu: ADB may not open
those screens, and values written with `settings put` can be overwritten by Amazon's settings sync. Menu labels are
given in English and German (the stick this was tested on runs in German). With no remote, use `tools/remote.py`,
or `adb shell input keyevent` plus a screenshot after each step.

Before changing anything, save the current values:

```sh
for n in global secure system; do adb shell content query --uri content://settings/$n > backup/settings_$n.txt; done
```

## Through the menu

| Menu path (English / German) | Set to | Setting it changes | Undo |
|---|---|---|---|
| Settings → Preferences → Privacy Settings → Cookie consent / Einstellungen → Benutzereinstellungen → Datenschutzeinstellungen → Zustimmung zu Cookies | Off (Decline / Ablehnen) | cookie consent | same menu, accept |
| … → Device Usage Data / Gerätenutzungsdaten | Off | `settingsApp:deviceUsageData:value` 1 → 0 | same menu |
| … → Collect App Usage Data / App-Nutzungsdaten sammeln | Off | `settingsApp:appUsageData:value` 1 → 0 | same menu |
| … → Interest-based Ads / Personalisierte Werbung | Off | `advertisingIdApp:interestBasedAds:value` 1 → 0, `limit_ad_tracking` 0 → 1 | same menu |
| Preferences → Featured Content / Benutzereinstellungen → Empfohlene Inhalte → video and audio autoplay | Off | autoplay on Amazon's home | same menu |
| Display & Sounds → Screensaver → Start Time / Töne und Bildschirm → Bildschirmschoner → Startzeit | Never / Nie | `screensaver_enabled` 1 → 0 | same menu, e.g. 5 minutes |

On the tested stick the screensaver process alone used 85 MB of RAM while idle. Turn it off only if the TV switches
itself off when idle; otherwise keep it to avoid burn-in.

Navigation: Amazon's home → gear icon → the grid. On Fire OS 5, `am start -a android.settings.SETTINGS` shows that grid
(firehome's "Settings" button does the same).

## With ADB

```sh
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put global cross_device_recents_sync_enabled 0   # Amazon's "recently used" sync
```

Undo: set the animation scales back to `1` (or `0.5`) and the recents sync to `1`.

Check afterwards:

```sh
adb shell content query --uri content://settings/secure > /tmp/secure.txt
grep -E "screensaver_enabled|deviceUsageData:value|appUsageData:value|interestBasedAds:value|name=limit_ad_tracking" /tmp/secure.txt
```

All of these survived a reboot on the tested stick, and Amazon's sync did not turn any of them back on.

## Keep ADB on

Network ADB (Settings → My Fire TV → Developer options → ADB debugging) has to stay on for `tools/` and
`firehome/install.sh`. It also gives full control of the stick to anyone on the network: switch it off when you are done,
once you have another way to control the stick (a remote or the Fire TV phone app).
