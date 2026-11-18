# Using this on other Fire TV sticks

🟢 tested with this repo · 🔴 not tested

| Status | Device | Model | Year | Fire OS / Android | What to expect |
|---|---|---|---|---|---|
| 🟢 | Fire TV Stick 2nd gen | AFTT | 2016 | Fire OS 5.2.9.5 / Android 5.1 | Everything in this repo, measured (see `fire-os-5-findings.md`) |
| 🔴 | Fire TV Stick 1st gen | AFTM | 2014 | Fire OS 5 / Android 5.1 | Probably the same as AFTT: same limits, same firehome |
| 🔴 | Fire TV Stick 4K | AFTMM | 2018 | Fire OS 6 / Android 7.1 | `pm disable-user` works (`examples/amazon-fire-tv-stick-4k-2018/`); firehome should run; check the cover and Amazon's package names |
| 🔴 | Fire TV Stick 3rd gen | AFTSS | 2020 | Fire OS 7 / Android 9 | Debloat with `tools/disable.sh`; cover needs the overlay permission (below); an accessibility service works here |
| 🔴 | Fire TV Stick Lite | AFTSSS | 2020 | Fire OS 7 / Android 9 | As 3rd gen; Homestead reports Fire OS 7.7.1.6 on this model |
| 🔴 | Fire TV Stick 4K Max | AFTKA | 2021 | Fire OS 7 / Android 9 | As 3rd gen |
| 🔴 | Fire TV Stick 4K (2nd gen) | AFTKM | 2023 | Fire OS 8 / Android 11 | Debloat works with protected exceptions; Homestead reports 8.1.8.5 |
| 🔴 | Fire TV Stick 4K Max (2nd gen) | AFTKRT | 2023 | Fire OS 8 / Android 11 | Glass and Firestick-Cleaner were tested here; an accessibility takeover flashes ~0.4 s |
| 🔴 | Fire TV Stick 4K Select | – | 2025 | reportedly Vega OS (not Android) | Android tools and APKs do not apply |

Find the model and firmware with `adb shell getprop ro.product.model` and `adb shell getprop ro.build.version.name`.

## What changes with newer Fire OS

| Topic | Fire OS 5 (tested) | Fire OS 6/7/8 (expected, untested here) |
|---|---|---|
| Debloat | Nothing switchable; firehome ends idle services | `HIDE=` unset, `tools/disable.sh <list>` (`pm disable-user`); protected packages are refused; re-check after reboots and Appstore updates |
| Takeover of Home | Log watcher (this repo) | Log watcher still works; an accessibility service (Homestead, Glass) reacts faster and is the robust choice on Fire OS 8 |
| Cover against the 5 s gap | `TYPE_SYSTEM_ALERT`, permission granted at install | Android 8+: switch to `TYPE_APPLICATION_OVERLAY` for `targetSdk >= 26`, and allow the overlay with `adb shell appops set com.firehome SYSTEM_ALERT_WINDOW allow` |
| `READ_LOGS` | `pm grant` works | `pm grant` works up to Android 12; Android 13+ asks the user on each log access |
| Settings | `content query`, no `settings list` | `settings list global|secure|system` available |
| Privacy switches | Menu only | Menu; firestrip also writes `limit_ad_tracking`, `amazon:acr_enabled`, `amazon:sidewalk_enabled` |
| Prime Video package | `com.amazon.firebat` | Check: open Prime from Amazon's home and read `logcat | grep "START u0"` |

## Steps for a new stick

1. Read `PROMPT.md`; run it with the stick's IP in Claude Code (or follow the steps by hand).
2. Copy the closest list from `examples/` to a new folder for your stick, comment out what you use, add a row to
   `examples/README.md` and to the TV table in `README.md` (the tests check both).
3. Adapt `APPS` in `firehome/src/com/firehome/HomeActivity.java` and the marks (`docs/firehome.md`).
4. Measure before and after: boot timeline, Home gap, free RAM. Change the dot in the tables above to 🟢 and send the
   numbers back as a pull request.

## Safety

- Nothing is uninstalled. `tools/disable.sh` records each package's state under `~/.cleanyourtv/` first;
  `tools/restore.sh` replays it. `firehome/install.sh --remove` brings Amazon's home back.
- **With no remote, Wi-Fi is the only way in.** Keep every Wi-Fi provisioning, remote, Bluetooth and pairing service.
  A factory reset is no fallback: setup after a reset needs a physical remote.
- Never end or hide the launcher, OTA, `vizzini`, `logan`, `imp`, the keyboard, the Appstore or Prime's packages.
