# Related projects

Projects studied while building this setup. Only MIT/CC0 material is included in this repository (see
`THIRD_PARTY_NOTICES.md`); the others are linked for their ideas. Licences as reported by GitHub.

| Project | Licence | Approach | Tested on (per its README) | What this repo took | Why it does not apply to Fire OS 5 | When it helps on other sticks |
|---|---|---|---|---|---|---|
| [CleanFrame](https://github.com/nthnclrk/CleanFrame) | MIT | Calm launcher for Samsung The Frame (Tizen web app) | Samsung LS03D, Tizen 9 | The look (dark glass dock, mark-only tiles, light focus plate), the Netflix/YouTube/Disney+/Prime marks, the forest photo | Tizen, not Android: only the design transfers | Design reference for any launcher |
| [debloat-fire-stick-claude-code](https://github.com/whyashthakker/debloat-fire-stick-claude-code) / [CleanYourTV](https://github.com/Gathin23/cleanyourtv) | MIT | ADB tools, backup-first debloat, browser remote, first `firehome` | Fire TV Stick 2nd gen (AFTT), Fire OS 5.2.9.5 | `tools/`, `examples/`, `tests/`, the READ_LOGS watcher idea | – (this repo builds on it) | Fire OS 6/7 lists in `examples/` |
| [Homestead](https://github.com/Sarg338/Homestead) | GPL-3.0 (link only, no code copied) | Replacement home: accessibility service `HomeRedirectService` covers Amazon's home; separate `:homekey` process reads the log to catch Home earlier; on-device ADB via a VPN tunnel | Fire OS 7.7.1.5/7.7.1.6 (maintainer); 8.1.x and 14 (user reports); Home switch needs Fire OS 6+ | The idea of reacting to Home as early as possible | Fire OS 5 drops accessibility services set over ADB | Fire OS 7/8: accessibility + log watcher is the most robust takeover |
| [firestrip](https://github.com/WB2024/firestrip) | MIT | Linux TUI/CLI: `pm disable-user`, telemetry `settings` overrides, `cmd package set-home-activity`, tiers SAFE / RISKY / TELEMETRY / NEVER_TOUCH | Fire TV Box 4K (AFTBOXE1); AFTT profile exists but is empty | Grouping packages by risk, a never-touch list | `disable-user` refused; `set-home-activity` not on Android 5.1 | Fire OS 6+: tiers and telemetry keys (`limit_ad_tracking`, `amazon:acr_enabled`, `amazon:sidewalk_enabled`) |
| [Firestick-Cleaner-And-Launcher-Replacer](https://github.com/niftiest/Firestick-Cleaner-And-Launcher-Replacer) | none stated (link only) | Installs Projectivy Launcher, accessibility Home redirect, `deviceidle whitelist`, disable loop with reboot and re-apply, before/after RAM | Fire TV Stick 4K, Lite, 4K Max 2nd gen (AFTKRT, Fire OS 8.1.7.4) | The protected list (launcher, OTA, `vizzini`, `logan`, `imp`), checking persistence after a reboot, RAM before/after | Accessibility, `deviceidle` and `disable-user` not available | Fire OS 7/8: reboot-and-reapply; blocking `softwareupdates.amazon.com` at the router against OTA re-enabling |
| [firetv-glass-launcher](https://github.com/ivantrj/firetv-glass-launcher) | MIT | Native launcher "Glass" with an accessibility service that listens only to the Amazon launcher's window events; debloat scripts with one `pm` per call over `adb-shell` | Fire TV Stick 4K Max (AFTKRT), Fire OS 8 | Running one shell command per call in scripts | Accessibility not available | Fire OS 8: event-driven takeover with a ~0.4 s flash, no log reading |

## Takeover approaches compared

| Approach | Fire OS 5 | Fire OS 6-8 | Flash of Amazon's home |
|---|---|---|---|
| Log watcher (`READ_LOGS`) + activity start (firehome ≤ 1.2) | works | works | ~5 s (app-switch delay) |
| Log watcher + overlay cover (firehome 1.3) | works | needs `TYPE_APPLICATION_OVERLAY` + `appops set com.firehome SYSTEM_ALERT_WINDOW allow` on Android 8+ (untested) | none visible |
| Accessibility service on Amazon's window (Homestead, Glass, Projectivy) | blocked | works | ~0.4 s |
| `cmd package set-home-activity` / disable Amazon's launcher (firestrip) | not available | often ignored or refused on recent firmware | – |
