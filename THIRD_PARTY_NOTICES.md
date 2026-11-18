# Third-party notices

The repository's own code and documentation are MIT (see `LICENSE`). Parts come from other projects and keep their terms.

| Material | Where | Source and terms |
|---|---|---|
| ADB tools, backup/restore scripts, Fire OS 6/7 lists, tests | `tools/`, `examples/`, `tests/` | [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin23 and [debloat-fire-stick-claude-code](https://github.com/whyashthakker/debloat-fire-stick-claude-code), MIT |
| Look of the home screen (dark glass dock, mark-only tiles, light focus plate) | `firehome/` | [CleanFrame](https://github.com/nthnclrk/CleanFrame) by Nathan Clark, MIT, `firehome/licenses/CLEANFRAME-LICENSE.txt` |
| Netflix, YouTube marks; Prime Video smile | `firehome/res/drawable/logo_{netflix,youtube,primevideo}_clean.xml` | [Simple Icons](https://simpleicons.org) as cut in CleanFrame, CC0, `firehome/licenses/SIMPLE-ICONS-LICENSE.txt` |
| Disney+ mark | `firehome/res/drawable/logo_disneyplus_clean.xml` | [Custom Brand Icons](https://github.com/elax46/custom-brand-icons) as bundled in CleanFrame, MIT, `firehome/licenses/CUSTOM-BRAND-ICONS-LICENSE.txt` |
| RTL, Das Erste (ARD), ZDF marks | `firehome/res/drawable/logo_{rtl,ard,zdf}_clean.xml` | Simple Icons 13.21.0, CC0 |
| Background photo "Forest mist" | `firehome/res/drawable-nodpi/home_bg_forest.jpg` | Dejan Zakic, [Unsplash](https://unsplash.com/photos/rr2EF3J5F2Y), via CleanFrame's wallpaper set; [Unsplash License](https://unsplash.com/license) (cropped to 1920x1080) |

All marks were converted to Android vector drawables with `tools/svg2vd.py` (cropped, filled white, arcs turned into
cubic curves). Brand marks identify the services; their owners keep their trademark rights, and no endorsement or
affiliation is implied. The 7TV tile sets the word "joyn" in the system font because no open Joyn mark exists.

Projects that were studied but whose code is **not** included are listed in `docs/related-projects.md`.
