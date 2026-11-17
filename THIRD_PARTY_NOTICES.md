# Third-party notices

ReSaver for Android bundles or derives from the following projects. Full license texts
are available at the linked URLs.

## ReSaver / FallrimTools

- Upstream: https://www.nexusmods.com/skyrimspecialedition/mods/5031
- Source version: 6.0.643
- Copyright 2016-2023 Mark Fairchild
- License: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)

The Java sources under `app/src/main/java/resaver/` and `mf/` are taken from
FallrimTools ReSaver. The Swing user interface (`resaver/gui`), the desktop entry
point, and desktop-only helpers were removed. `ModelBuilder` and `ProgressModel`
were rewritten as non-Swing classes, `Header` was stripped of AWT image handling,
and a few call sites were adapted for Android's Java 17 runtime. The save format
handling, cleaning logic, PEX/ESP readers and archive code are unchanged.

## lz4-pure-java

- Upstream: https://github.com/adrianwalker/lz4-pure-java (fork of lz4-java)
- Version: 1.7.0
- License: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
- Bundled as `app/libs/lz4-pure-java-1.7.0.jar`

Used by the vendored core for LZ4-compressed save bodies.

## j2html

- Upstream: https://j2html.com
- Version: 1.5.0
- License: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
- Bundled as `app/libs/j2html-1.5.0.jar`

Present because the vendored core imports it for HTML report generation; the
Android app does not expose that feature.

## juniversalchardet

- Upstream: https://code.google.com/archive/p/juniversalchardet/
- Version: 1.0.3
- License: Mozilla Public License 1.1 (https://www.mozilla.org/MPL/1.1/)
- Bundled as `app/libs/juniversalchardet-1.0.3.jar`

Used for encoding detection while reading plugin and save strings.

## skyrim-suite-ui

- Location in this tree: `suite-ui/`
- Shared Compose design system used by the Skyrim Android tool ports (Droid Mod
  Loader, Skyrim Anim Tool, LOOT Android, BodySlide Android)

Vendored as a Kotlin source root rather than a Maven artifact, matching how the
sibling apps consume it.

## AndroidX, Jetpack Compose, Kotlin

Distributed under the Apache License 2.0 and resolved from Google Maven and
Maven Central at build time. See `app/build.gradle` for the exact artifacts.

## Trademarks

Skyrim, The Elder Scrolls and related marks are trademarks of Bethesda Softworks
LLC. This project is an unofficial fan tool and is not affiliated with or
endorsed by Bethesda Softworks or the ReSaver author.