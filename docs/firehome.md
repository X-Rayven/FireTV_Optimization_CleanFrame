# firehome: the home screen

A small native Android app (Java, no Gradle, ~570 KB APK, ~37 MB RAM) that replaces what you see when you press Home,
without root. The look follows [CleanFrame](https://github.com/nthnclrk/CleanFrame): a photo, a large clock, a dark glass
dock with white marks and a light plate on the focused tile.

Fire OS will not hand Home to another app, so firehome works around Amazon's home instead of replacing it.

## How it works

| Part | File | What it does |
|---|---|---|
| `HomeWatcher` | `src/com/firehome/HomeWatcher.java` | Foreground service that follows `logcat` (`READ_LOGS`, granted over ADB by `install.sh`). When ActivityManager logs a start of `com.amazon.tv.launcher` with category HOME, it shows the cover and starts `HomeActivity`. `pause(ms)` lets Amazon's home through, for the "Fire TV home" and "Settings" buttons. `catchUp()` at start-up takes over only if Amazon's home was the last thing started, so a playing video is left alone. |
| `BootReceiver` | `src/com/firehome/BootReceiver.java` + manifest | Starts the watcher on `BOOT_COMPLETED` **and** on `CONNECTIVITY_CHANGE`, `wifi.STATE_CHANGE`, `TIME_SET`, which arrive about a minute earlier (see `docs/fire-os-5-findings.md`). Later broadcasts find the watcher running and change nothing. |
| `Cover` | `src/com/firehome/Cover.java` | Full-screen overlay over Amazon's home during the ~5 s Android holds back app starts after a Home press. Shows a snapshot of the home screen with a live clock, swallows keys, is removed when HomeActivity has drawn (safety timeout 8 s). Needs `SYSTEM_ALERT_WINDOW`. |
| `HomeActivity` | `src/com/firehome/HomeActivity.java` | The screen: clock, date, Wi-Fi signal, 4x2 app grid, three buttons (Settings, Appstore, Fire TV home), and the background cleanup. |

### Background cleanup

`HomeActivity.trimIdleApps()` calls `ActivityManager.killBackgroundProcesses()` (normal permission, only processes in
the background, never the app on screen):

- **On every Home, at most once a minute:** `IDLE_AMAZON`, about 32 Amazon services the owner does not use (Live TV,
  Kindle, Photos, Music, Alexa extras, Miracast sink, metrics, crash/perf reporting, IMDb, Silk, Gamehub …).
- **Once per boot, 5 minutes after power-on:** additionally `BOOT_STARTED_APPS`, the streaming apps that boot receivers
  started in the background. Not on every Home, so going back to an app you just left stays a warm start.
- **Never listed:** Amazon's launcher, Prime Video (`com.amazon.firebat`, `com.amazon.avod`), Appstore
  (`com.amazon.venezia`), `vizzini`, `logan`, `imp`, the keyboard, Bluetooth and remote services, `devicecontrol`,
  `tcomm`, `whisperjoin` (Wi-Fi setup), connectivity, identity/sync, device messaging, OTA, settings,
  `whisperlink`/discovery (the Fire TV phone app as a way in if ADB is ever off).

Amazon restarts some services later (network broadcasts, alarms); the cleanup is a relief, not a removal. Measured
5 minutes after boot: 187 → 170 processes, actually free RAM 62 → 211 MB.

### Focus

Fire TV remotes move focus with the D-pad. The buttons sit centred below the grid, outside the outer columns, so
Android's own focus search found nothing below the outer tiles; `linkFocus()` sets the paths explicitly: Down from
column 1 → Settings, columns 2-3 → Appstore, column 4 → Fire TV home, and back up.

## Change the apps

Edit `APPS` in `HomeActivity.java`. Each entry is a label, the package name and either a white mark drawable or a
text mark:

```java
new AppEntry("Prime Video", "com.amazon.firebat", R.drawable.logo_primevideo_clean),
new AppEntry("7TV", "de.prosiebensat1digital.seventv", "joyn"),   // no open mark: the name in type
```

Find package names with `adb shell pm list packages -3` (apps you installed) and, for Amazon's own apps, by opening
them from Amazon's home and reading `adb shell logcat -d | grep "START u0"`. On Fire OS 5 Prime Video is
`com.amazon.firebat`. The grid is 4 columns; with more than 8 apps, raise the dock height in `onCreate()`.

### Marks

White single-colour marks look best. [Simple Icons](https://simpleicons.org) (CC0) has most services:

```sh
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt -r tools/requirements-icons.txt
curl -sLo /tmp/max.svg https://cdn.jsdelivr.net/npm/simple-icons@13.21.0/icons/max.svg
tools/venv/bin/python -I tools/svg2vd.py /tmp/max.svg firehome/res/drawable/logo_max_clean.xml "Max: Simple Icons 13.21.0 (CC0)"
```

`svg2vd.py` crops to the shape, fills it white and rewrites arcs as cubic curves (Android 5.1 draws arcs wrongly).
`HomeActivity.markSize()` gives every mark the same area, so a tall N and a flat smile look equally heavy. Add each new
mark to `THIRD_PARTY_NOTICES.md`.

### Other things to change

- **Language:** the button labels and toasts are German ("Einstellungen", "Fire TV Startseite", "… lässt sich nicht
  öffnen"); search `HomeActivity.java` for the strings. The date follows the stick's locale.
- **Background:** replace `res/drawable-nodpi/home_bg_forest.jpg` with a 1920x1080 JPEG of the same name.
- **Cleanup lists:** `IDLE_AMAZON` and `BOOT_STARTED_APPS` in `HomeActivity.java`. Remove anything you use (e.g.
  `com.amazon.wcast.sink` for screen mirroring).

## Build and install

Needs a JDK (17 works) and the Android SDK build-tools and a platform (`~/Library/Android/sdk` or `ANDROID_HOME`).

```sh
PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH" firehome/build.sh     # → firehome/build/firehome.apk
TV_HOST=192.168.1.50 firehome/install.sh                            # build, install, grant READ_LOGS, open
TV_HOST=192.168.1.50 firehome/install.sh --remove                   # uninstall; Amazon's home is back
```

With the `adb` binary instead of `tools/tv.py`:

```sh
adb connect 192.168.1.50:5555
adb install -r firehome/build/firehome.apk
adb shell pm grant com.firehome android.permission.READ_LOGS
adb shell am start -n com.firehome/.HomeActivity
```

`pm install` normally takes seconds. If it hangs for minutes, the system is stuck (see the watchdog note in
`docs/fire-os-5-findings.md`); wait for it rather than starting a second install.

Bump `--version-code`/`--version-name` in `build.sh` for each release (1.3 = versionCode 4).
