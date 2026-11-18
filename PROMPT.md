# Prompt: optimize a Fire TV stick with this repo

Clone the repository, open it in [Claude Code](https://claude.com/claude-code) (or another coding agent with shell
access on your computer) and paste the prompt below. Replace `<IP>` with the stick's address
(Settings → My Fire TV → About → Network). ADB debugging must be on (Settings → My Fire TV → Developer options);
the first connection shows an "Allow USB debugging?" prompt on the TV.

The agent needs: `adb` (Android platform-tools) or Python 3 for `tools/tv.py`, a JDK 17 and the Android SDK
build-tools to build `firehome/`.

---

```text
Optimize my Amazon Fire TV stick at <IP> with this repository, the way the README describes.
Read README.md, docs/fire-os-5-findings.md, docs/settings.md, docs/firehome.md, docs/adapting.md and
docs/related-projects.md first. Plan before you change anything and show me the plan.

1. Connect and look
   - Connect over ADB (adb connect <IP>:5555, or tools/tv.py --probe). Only one ADB connection at a time.
   - Read the model and firmware (getprop ro.product.model, ro.build.version.name) and find the stick in
     docs/adapting.md. Say what applies to this Fire OS version and what is untested.
   - Save a snapshot to backup/ (gitignored): pm list packages -f -i / -d / -u / -3, the three settings tables via
     content query, getprop, dumpsys meminfo, and the boot timeline from logcat -b events if it is still there.
   - Never truncate dumpsys output on the stick (no "dumpsys … | head" or "grep -m" inside adb shell).
     Pull the full output and filter it here.

2. Ask me
   - Which streaming apps I use (these become the home screen tiles, in my order).
   - Whether I use casting from the phone, screen mirroring, Alexa voice, Amazon Photos/Music/Kindle, Live TV.
   - Whether the TV switches itself off when idle (only then turn the screensaver off).

3. Settings (docs/settings.md)
   - In the menu, with a screenshot after each step: privacy switches off (cookies, device usage data, app usage
     data, interest-based ads), featured-content video/audio autoplay off, screensaver start time "Never" if agreed.
   - Over ADB: animation scales 0, cross_device_recents_sync_enabled 0.
   - Verify with content query and write down the undo for each.

4. Debloat, depending on the firmware
   - Fire OS 5: pm disable-user and pm hide are refused for Amazon's apps. Don't fight it; only hide apps I
     installed and don't use (HIDE=1 tools/disable.sh), and leave the rest to firehome's cleanup.
   - Fire OS 6+: copy the closest list from examples/ to a new folder for my stick, comment out what I use, run
     tools/disable.sh in small batches with a screenshot after each batch, re-check after a reboot.
   - Never touch: the launcher, OTA, vizzini, logan, imp, the keyboard, Appstore (com.amazon.venezia), Prime Video
     (on Fire OS 5: com.amazon.firebat and com.amazon.avod), Wi-Fi provisioning (whisperjoin), Bluetooth and remote
     services, tcomm, devicecontrol, whisperlink/discovery.

5. Home screen (docs/firehome.md)
   - Set APPS in firehome/src/com/firehome/HomeActivity.java to my apps. Find each package with pm list packages -3,
     or by opening the app from Amazon's home and reading "START u0" in logcat.
   - Make white marks for missing services with tools/svg2vd.py from Simple Icons (CC0); add them to
     THIRD_PARTY_NOTICES.md. Use a text mark if no open mark exists.
   - Adjust IDLE_AMAZON / BOOT_STARTED_APPS to what I said I don't use. Translate the button labels if I want.
   - On Android 8+ adapt Cover to TYPE_APPLICATION_OVERLAY and allow it with appops (docs/adapting.md).
   - Build with firehome/build.sh, install with firehome/install.sh (or adb install -r + pm grant READ_LOGS).

6. Verify and measure
   - Buttons: Settings opens and Home comes back; Appstore opens the store front; Fire TV home pauses the watcher.
   - Home from inside an app: within ~1 s dumpsys window windows shows the firehome cover above Amazon's home with
     mCurrentFocus on it, then HomeActivity.
   - Reboot: compare boot_progress_enable_screen with the firehome start in logcat -b events (goal: firehome
     before or right after the screen comes on).
   - Five minutes after boot: free RAM and process count against the snapshot.
   - Settings still set after the reboot.
   - Take a screenshot of the home screen.

7. Write it down
   - Add my stick's results to docs/adapting.md (turn its dot green only if everything was measured) and the list to
     examples/ with a row in examples/README.md and README.md; run python3 -m unittest discover -s tests -v.
   - Keep my IP, account names, profile names, Wi-Fi and MAC addresses out of every committed file.
   - Nothing is uninstalled; give me the undo commands at the end.
```
