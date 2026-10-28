# Waru cheat menu status

Branch: `feat/waru-cheat-menu-visible`
Base: `fix/unofficial-map-preview-slashes`

## What this branch changes

- Adds `WaruCheatMenuOverlay.java`, a visible in-game overlay with a persistent top-right `WARU CHEATS` launcher and an expandable panel.
- Instantiates the overlay from `GeneralsZHActivity.onCreate()` and attaches it to SDL's actual game layout (`mLayout`) after the keyboard overlay. This is intended to make the panel appear over gameplay rather than only in the launcher.
- The menu can be opened and hidden.

## Important limitation

The toggle and +100,000 cash controls are intentionally disabled. This branch fixes the missing menu UI; it does **not** yet implement native simulation cheats. Enabling UI toggles before the engine supports deterministic game-thread actions would falsely imply that units, money, or power have changed and could create multiplayer desync.

## Next engineering step

Implement and test native simulation commands for:
1. local/offline-only friendly-unit invulnerability,
2. a one-shot +100,000 money deposit per button tap,
3. infinite power in simulation state,
4. rejecting local-only cheats during network sessions.

Only after that should the controls be enabled. Synchronized private-LAN cheats need separate peer consent, protocol/version checks, and checksum/replay validation.

## Build check

This branch has not yet been built in GitHub Actions or tested on a device. Use Actions → Build Android → Run workflow, select `feat/waru-cheat-menu-visible`, and leave release publishing disabled. A successful compile only confirms packaging; device verification must confirm the visible button appears in an actual match.
