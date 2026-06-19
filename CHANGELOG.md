# Changelog

All notable changes to this Android port are documented here.

This project follows the spirit of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Dates use `YYYY-MM-DD`.

## 2026-06-19 - 1.0.4

### Added

- Added a full SVG touch-overlay icon pipeline with 37 bundled icons, `iconset.json`, `iconmappings.json`, per-button icon sizing, and transform metadata.
- Added a separate editable Map Screen touch-overlay layout with its own presets and default buttons.
- Added mouse buttons to the Map Screen default layout, positioned like the tactical mouse buttons.
- Added sticky toggle-key support for modifier buttons, including visible active-state feedback.
- Added a Hardware mouse/keyboard input mode for direct physical input passthrough.
- Added a Reload Selected touch preset (`ALT+R`) with a dedicated reload icon.
- Added configurable Direct Tap Arbitration timing in touch settings.
- Added a one-time touch-layout reset notice after the forced 1.0.4 default-preset refresh.

### Changed

- Replaced the bundled touch-overlay default preset and force-refreshes older user layouts once for this default-preset version.
- Bumped the touch-overlay config schema to 15 and separated default-preset versioning from schema migrations.
- Reworked icon rendering so SVG icons render first and Canvas drawing is only a fallback.
- Reworked the Map Screen overlay defaults from legacy keyboard-style buttons to a compact SVG-backed layout.
- Renamed and localized several touch presets for clearer in-game editing.
- Rebuilt the touch-overlay lock button as an SVG-backed rounded square.
- Ordered Hardware mouse/keyboard mode directly below Modern Controls in the launcher dropdown.
- Kept the touch overlay visible in Shopkeeper/Vendor screens, using the regular tactical button layout.
- Improved widescreen placement for Auto-Bandage, Shopkeeper/Vendor UI, tactical NPC dialogue, subtitle text, civilian quotes, and tutorial panels.
- Excluded the D-pad from shape-aware hit-testing so its full touch area remains usable.
- Reworked the public project documentation.
- Moved Android build instructions into `docs/BUILDING_ANDROID.md`.
- Added the Android scaling plan to the public documentation and linked it from the README and Android feature overview.
- Added public release documentation, release notes, third-party notices, and a repository sanity workflow.
- Clarified that clean release builds produce `app-release-unsigned.apk` unless signing is configured locally.

### Fixed

- Fixed Map Screen buttons not appearing after update because migration only filled empty `mapScreenButtons` on schema bump.
- Fixed Map Screen Inventory toggling item highlight (key I) instead of opening the inventory panel (key ENTER).
- Fixed SVG icon alignment by correctly converting IconConverter offsets into Android Canvas coordinates.
- Fixed the touch-overlay lock button being too small and hard to see.
- Fixed legacy strafe presets triggering grab behavior by replacing them with the `alt_movement_hold` toggle preset.
- Fixed sticky-toggle key release so forced releases send KeyUp without toggling state again.
- Fixed sector exit dialog using incorrect dirty-rect coordinates (width/height instead of right/bottom).
- Fixed the touch-overlay editor showing tactical presets in the Map Screen and Map Screen presets in tactical screens.
- Fixed hardware mouse mode appearing at the wrong dropdown position.
- Fixed Auto-Bandage causing a temporary 4:3 layout jump on widescreen displays.
- Fixed Shopkeeper/Vendor menus being cropped or mis-scaled on wide displays.
- Fixed the touch-layout reset notice being cropped when shown in the main menu.
- Fixed direct-tap false positives after cursor movement in touchpad mode.
- Fixed shape-unaware hit-testing registering false touches in invisible button corners.
- Fixed D-pad corners becoming unresponsive after shape-aware hit-testing was introduced.

### Verified

- Ran `:app:testDebugUnitTest`.
- Ran `:app:externalNativeBuildDebug`.
- Built `:app:assembleDebug`.
- Installed the debug APK successfully on Android hardware with `adb install -r`.

## 2026-05-17

### Added

- Added safe launcher resolution presets: Modern, High Res (More Map), and Retro (640x480).
- Added `resolution_mode` configuration with stable lowercase serialization and migration from older `res`-only configs.
- Added Expert Settings for manual resolution, scaling, and control mode selection.
- Added Android unit tests for resolution policy and resolution mode serialization.
- Added native crash log writing to `crashlog-latest.txt` alongside emergency savegame creation.
- Added a one-time main menu touch-control tutorial panel with persistent "do not show again" state.
- Added a touch-overlay setting to disable mouse-edge map scrolling while keeping keyboard and drag scrolling available.

### Changed

- Renamed the high-resolution preset to High Res (More Map) / High Res (Mehr Karte).
- Standard launcher mode now keeps users on safe resolution presets and applies Near Perfect scaling with Modern Controls.
- Modern and High Res modes calculate aspect-correct internal resolutions from the device's landscape display size.
- Ultrawide phones are treated as phones, not tablets, even when their pixel height is high.
- Non-game screens, menus, videos, map screen, and splash screens are presented in a centered 4:3 area in Modern and High Res modes.
- High Res touch overlay defaults now allow larger action panel scales, up to 180% on phones.
- Existing generated touch layouts are migrated to mode-aware defaults when they still match bundled defaults.

### Fixed

- Fixed stretched or horizontally squeezed menu, splash, video, and map-screen presentation in wide internal resolutions.
- Fixed the first splash screen being stretched across the widescreen render surface.
- Fixed stale game pixels appearing in side bars during near-perfect oversampling presentation.
- Fixed Android touch-overlay buttons blocking the native tutorial.
- Fixed native action-panel presentation scaling drawing over the tutorial.
- Fixed Retro 640x480 bottom-panel and merc-portrait touch mapping by remapping touches to the visible 4:3 game area.
- Fixed crash-report formatting on Android by avoiding a recursive `std::string_view` formatter path in the C++17 compatibility patch.

### Verified

- Built and checked `:app:compileDebugKotlin`.
- Built and checked `:app:compileDebugJavaWithJavac`.
- Built and checked `:app:externalNativeBuildDebug`.
- Ran `:app:testDebugUnitTest`.
- Built the release APK with `:app:assembleRelease`.
- Installed the signed 1.0.3 APK successfully on Android hardware during local verification.

## 2026-05-14

### Added

- Added tactical action-panel presentation scaling for Android, including safe input remapping back to the original JA2 panel coordinates.
- Added adaptive touch-overlay defaults for phone, tablet, and wide-screen Android layouts.

### Fixed

- Blocked touch-overlay input while the in-game tutorial is visible.

## 2026-05-13

### Fixed

- Fixed tactical name labels leaving white trails while scrolling the map during merc movement.
- Fixed a follow-up rendering regression by suppressing above-merc name rendering during active video scrolling instead of invalidating the full viewport.
- Improved long-press selection on team-panel portraits so all controllable in-sector mercs are selected consistently.
- Improved Modern Controls two-finger taps in the tactical field so right-click is recognized on the first pointer release and does not compete with deferred single-tap or double-tap handling.

### Verified

- Built the release APK successfully with `:app:assembleRelease --rerun-tasks`.
- Verified the touch fixes through device gameplay testing.

## 2026-05-09

### Added

- Added an in-game tutorial system with JNI entry points, localized panels, persistent "do not show again" state, and touch-overlay access.
- Added a help button to the touch overlay in edit mode.

### Changed

- Updated launcher copy for internal resolution, scaling, and mouse mode recommendations.
- Changed the default scaling mode to near-perfect oversampling.
- Reduced the Modern Controls double-tap-hold threshold for faster held-click actions.
- Updated the bundled default touch preset.

### Fixed

- Fixed game restart behavior after minimizing or configuration changes by expanding handled Android configuration changes and adding session continuation logic.

### Verified

- Built release APKs successfully after the tutorial, launcher, scaling, and touch timing changes.

## 2026-05-08

### Added

- Added Android 6-10 legacy storage permission fallback while keeping all-files access for Android 11+.
- Added bundled default touch overlay preset loading from app resources.
- Added direct bottom-panel touch handling in Modern Controls mode for tactical panel controls and inventory drag/drop.
- Added two-finger bottom-panel tap to toggle between team portraits and the single-merc inventory panel.
- Added hybrid direct-tap behavior for menus and map screens while preserving Modern Controls cursor movement.

### Changed

- Refined launcher defaults for internal resolution and scaling.
- Reworked Modern Controls double-tap and double-tap-hold behavior for more reliable running and drag selection.
- Reworked touch overlay reset behavior to offer restoring the bundled preset or deleting all buttons.

### Fixed

- Fixed touch overlay drag raw-coordinate handling on older Android/Fire OS devices.
- Fixed Modern Controls tap-to-click by holding synthetic mouse clicks briefly instead of sending down/up in the same event frame.
- Fixed double-tap-hold so it no longer emits an unwanted first single click before the held action.
- Fixed bottom-panel direct-touch boundary detection by querying native tactical panel geometry through JNI.

### Verified

- Built debug Kotlin, native CMake targets, and release APKs successfully during the touch and storage iterations.

## 2026-05-07

### Added

- Added the Android-native modular touch overlay system with persistent JSON configuration.
- Added configurable overlay buttons for mouse, keyboard, key combos, text, D-pad movement, sector exits, interface actions, and cheat actions.
- Added overlay edit mode with lock/unlock controls, draggable layout, grid snapping, import/export, reset, configurable button size, shape, opacity, icon, and action.
- Added in-game cheat overlay and persistent cheat configuration.
- Added native cheat system with runtime toggles for god mode, non-lethal player damage, full medical healing, unlimited ammo, no weapon jam, unlimited AP, unlimited breath, reveal enemies, reveal items, one-hit kill, and perfect hit chance.
- Added one-shot cheat actions for healing the team, reloading team weapons, reloading the selected merc, and granting money.
- Added native Android JNI bridges for screen ID, scroll speed, sector exits, team-panel portrait touch, and cheat actions.

### Changed

- Replaced the old hardcoded touch mouse buttons with the modular overlay.
- Added runtime-configurable tactical scroll speed.
- Moved overlay visibility to tactical-game-screen-only by default.
- Persisted overlay layout in locked mode so a fresh game start cannot be trapped in edit mode.

### Fixed

- Fixed touch overlay edit-mode event blocking when the overlay is hidden.
- Fixed touch overlay auto-hide screen ID handling.
- Fixed old touch overlay configs being discarded on schema upgrades by normalizing them instead.
- Fixed reveal enemy/item cheats being lost after savegame load by reapplying runtime flags.
- Fixed one-hit-kill checks so player-side friendly fire does not one-shot player mercs.
- Fixed one-hit-kill blade damage when base hand-to-hand impact was clamped.

### Verified

- Built release APKs successfully after native cheat hooks, overlay integration, and JNI changes.

## 2026-05-06

### Added

- Added Android-focused launcher modernization.
- Added Modern Controls mode with virtual cursor movement.
- Added legacy absolute mouse mode.
- Added legacy touchscreen mode selection.
- Added persistent mouse mode configuration.
- Added Android release signing support.

### Changed

- Updated Android Gradle Plugin, Gradle wrapper, Kotlin, and Android dependencies.
- Updated Android SDK targets to `compileSdk 35` and `targetSdk 35`.
- Updated the Android native build for NDK 27 / Clang 18 / C++20 compatibility.
- Linked SDL statically into `libja2.so`.
- Reworked app package and activities for the Android port.
- Replaced Android path picking with manually configured fixed game and save directories.
- Forced fullscreen immersive landscape presentation for launcher and game.
- Stabilized Android audio by selecting OpenSL ES and improving JA2 audio buffering.

### Fixed

- Fixed Android release startup with the synchronized SDL Java wrapper.
- Fixed missing `libSDL2.so` issues by loading only `libja2.so`.
- Fixed Modern Controls button transitions by sending the current Android SDL button-state bitmask on press and `0` on release.
- Fixed legacy absolute mouse release and cancel handling.
- Fixed Android audio stutter and clicking caused by AAudio underruns.

### Verified

- Built and installed release APKs successfully.
- Verified launcher startup, game startup, SDL JNI initialization, in-game rendering, and audio playback on Android hardware.
