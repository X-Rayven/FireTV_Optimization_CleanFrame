# Fire OS 5 findings (Fire TV Stick 2nd gen, AFTT)

Everything here was measured on one Fire TV Stick 2nd gen (AFTT, "tank", 1 GB RAM) running Fire OS 5.2.9.5
(Android 5.1.1, build 688806320), over ADB on Wi-Fi without root. Other firmware may behave differently.

## What ADB may and may not do

| Tried | Result |
|---|---|
| `pm disable-user --user 0 <pkg>` | Refused for every package: `SecurityException: Permission Denial` |
| `pm hide <pkg>` | Works for apps you installed; refused for Amazon's system apps |
| `pm uninstall -k --user 0 <pkg>` | Refused (`DELETE_FAILED_INTERNAL_ERROR`); Android 5.1 could not undo it anyway |
| `settings put secure enabled_accessibility_services …` | Accepted, then silently dropped: no accessibility service |
| `appops get/set` | `uid 2000 does not have android.permission.GET_APP_OPS_STATS` |
| `settings list …` | Not available on Fire OS 5; use `content query --uri content://settings/{global,secure,system}` |
| `am start` of Amazon's settings screens | `requires com.amazon.tv.permission.LAUNCHER_SETTINGS`: privacy switches only through the menu |
| `pm grant <pkg> android.permission.READ_LOGS` | Allowed: this is how firehome sees Home presses |
| `settings put global *_animation_scale 0` | Allowed |
| `KILL_BACKGROUND_PROCESSES` (normal app permission) | Allowed: firehome uses it to end idle Amazon services |
| `SYSTEM_ALERT_WINDOW` (overlay window) | Granted at install on Android 5.1: used by the Home cover |

So none of the Amazon packages in the debloat lists can be switched off on this firmware. The live check after a
"debloat" showed all of them still `installed=true hidden=false`. What actually makes the stick lighter is the
replacement home screen, the settings in `docs/settings.md` and ending idle services.

## Boot: why the home screen came a minute late, and the fix

Event-log timeline (`adb shell logcat -d -b events -v time`, seconds since power-on):

| Milestone | Before | With firehome 1.2+ |
|---|---|---|
| `boot_progress_ams_ready` | 42 s | 42 s |
| firehome process started | 115 s (by `BOOT_COMPLETED`) | 44.7 s |
| `boot_progress_enable_screen` | 48.8 s | 47.4 s |
| firehome on screen | 66 s **after** the screen came on | 1.7 s **before** the screen came on |

`BOOT_COMPLETED` is only sent after a long queue of network and time broadcasts, each of which cold-starts dozens of
receivers one after another (WorkManager `ConstraintProxy$NetworkStateProxy` of Amazon services and of streaming apps).
firehome already sat near the front of `BOOT_COMPLETED` (priority 999); the broadcast itself was late. The fix:
`BootReceiver` also listens to `android.net.conn.CONNECTIVITY_CHANGE`, `android.net.wifi.STATE_CHANGE` and
`android.intent.action.TIME_SET`, which arrive right as the screen comes on. Reproduced over two reboots.

The receivers keep starting processes until about 4 minutes after power-on (adep, livetv, blackbird, Photos came up at
3:30-4:00). A cleanup earlier than that only makes them start again; firehome waits 5 minutes for its one-off boot cleanup.

## Home press: the 5 second gap, and the cover

After a Home press Android holds back activity starts from ordinary apps for 5 seconds (`APP_SWITCH_DELAY`;
logcat shows `W/ActivityManager: Activity start request from <uid> stopped`). firehome asked to come up 5 ms after
Home but appeared ~5.3 s later; until then Amazon's home was visible. Only apps with the signature permission
`STOP_APP_SWITCHES` are exempt.

Overlay windows are not held back. `Cover` puts a full-screen `TYPE_SYSTEM_ALERT` window over Amazon's home at
once: a picture of the home screen (taken 0.6 s after each time the home screen is shown, RGB_565, ~4 MB) with a
live clock and date drawn at the same place. The window takes focus, so keys pressed in those seconds do not land in
Amazon's home. HomeActivity removes it 150 ms after `onResume`; a safety timeout removes it after 8 s.
Verified with `dumpsys window windows` 1.2 s after Home: the firehome overlay was window #5 above Amazon's
`HomeActivity_vNext` (#4) and held `mCurrentFocus`.

Trade-off: for those ~5 s the screen looks ready but ignores keys.

## Package facts that were not obvious

- **Prime Video is `com.amazon.firebat`** on this firmware (Amazon's home starts it via `LEANBACK_LAUNCHER` →
  `com.amazon.firebatcore.deeplink.DeepLinkRoutingActivity` → `livingroom.landing.LandingActivity`).
  `com.amazon.avod` is the older app and the playback service; its `FireTvHomeScreenActivity` finishes itself after
  ~250 ms (`am_finish_activity … app-request`) when started on its own. Do not end or hide either.
- **Appstore**: the package's launcher activity (`com.amazon.venezia/.grid.AppsGridLauncherActivity`) is only
  "My Apps". The store front is `com.amazon.venezia/.ade.ADEHomeActivity` with action
  `com.amazon.venezia.ade.SHORTCUT_LAUNCH` (exported, no permission). The `appstore://` link Amazon's own tile uses
  needs `com.amazon.venezia.DEEP_LINK_TO_3P_APP`.
- **Settings**: `Settings.ACTION_SETTINGS` resolves to `com.amazon.tv.launcher/.ui.SettingsActivity`, which at once
  starts `HomeActivity_vNext` with category HOME to show the grid. A Home watcher that does not pause for ~6 s takes
  that over and the Settings button seems dead.
- **RTL+ 6.3.4** (2024) showed "Fehler 994 – App nicht erreichbar" with a working network; the Appstore update to
  7.11.0 fixed it. Old app versions can be blocked server-side; check for updates before debugging the stick.

## Android 5.1 rendering quirk

`VectorDrawable` on Android 5.1 draws some SVG arc commands wrongly (the Prime Video smile showed only its two ends),
and compact arc flags such as `0 00` are misread. `tools/svg2vd.py` rewrites every path in absolute form with arcs
turned into cubic curves.

## Working with the stick safely

- **Do not truncate `dumpsys` output on the device** (`dumpsys … | head`, `grep -m1` inside `adb shell '…'`). Read
  the full output and filter on the computer. Once, during this work, a BatteryStats dump on a binder thread held the
  BatteryStats lock for ~14 minutes; ActivityManager, PowerManager and NetworkStats waited on it until the Android
  watchdog restarted `system_server` (`system_server_watchdog` in `dumpsys dropbox`). The requester could not be
  identified; it was the only watchdog entry in the history.
- `screencap -p` can take ~14 s on a busy stick; it is no use for timing. Use `dumpsys window windows`
  (`mCurrentFocus`) and the event log for timing.
- The MediaTek chip keeps ~11 `hdmi_*` kernel threads in uninterruptible wait, so `/proc/loadavg` shows ~12 on an
  idle stick. Look at CPU idle in `top`, not the load average.
- A dump of `SYSTEM_TOMBSTONE` entries full of `grep` with `SIGPIPE` comes from shell pipelines on the stick, not from
  a failing app.
- Only one ADB connection at a time: don't run `tools/tv.py` and the `adb` binary against the stick in parallel.
