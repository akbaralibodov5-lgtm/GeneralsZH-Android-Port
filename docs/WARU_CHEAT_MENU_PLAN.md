# Waru Edition Cheat Menu — implementation plan

**Status: design/integration map only. Runtime cheat behavior has not been implemented.** Do not describe this branch as a working cheat menu or distribute an APK as containing the feature until the native engine integration and tests below pass.

## Modes

### 1. Local-only (offline/skirmish)
- Android in-game overlay can be opened, hidden, and fully disabled.
- Toggles: friendly-unit invulnerability, infinite power, and a cash button that adds exactly 100,000 per tap.
- Local-only cheats must be unavailable once a network session is active. Never silently apply local state edits to a network match.

### 2. Synchronized private-LAN mode
- Every participant must explicitly opt into the same private-match cheat rules.
- Cheat actions must be deterministic simulation commands, not local UI edits.
- Every peer must use a compatible build, data patch, simulation rate, and cheat protocol version.
- Use stable command IDs and deterministic player/target selection. Include cheat state in the engine's snapshot/CRC path where appropriate.
- Host rejects commands unless every peer advertises compatibility and has opted in. If agreement is absent, disable the synchronized feature for that match.
- Do not claim desync-free behavior until multi-device LAN tests and replay/checksum tests pass.

## Repository integration map (inspected)

- Android game activity: `android/app/src/main/java/com/generalsx/zerohour/GeneralsZHActivity.java`.
  - This activity already bridges Java UI and native code for touch/keyboard features; use its established lifecycle/bridge pattern rather than creating an unrelated floating window that may steal game input.
- Existing touch routing: `Core/GameEngineDevice/Source/SDL3Device/GameClient/TouchInput.cpp`.
  - Preserve ordinary touch, selection, and order handling while the cheat panel is hidden.
- Player state and money API: `Generals/Code/GameEngine/Include/Common/Player.h` exposes `Player::getMoney()`; `Generals/Code/GameEngine/Include/Common/Money.h` exposes `Money::deposit(UnsignedInt, Bool, Bool)`.
  - A cash button must enqueue one game-thread/simulation action and call deposit once per tap; it must not run from a UI redraw or background timer.
- Power API: `Generals/Code/GameEngine/Include/Common/Energy.h` exposes production/consumption and power-supply ratio APIs. The infinite-power behavior needs to be implemented in simulation logic and snapshot/CRC handling, not by editing only the displayed power value.
- Damage entry point: `Generals/Code/GameEngine/Include/GameLogic/Object.h` exposes `Object::attemptDamage(DamageInfo*)` and `Object::healCompletely()`. Invulnerability must be scoped to the correct player-owned objects and evaluated deterministically by the game logic.
- Android CI: `.github/workflows/build-android.yml` builds the 30 Hz and 60 Hz native engines, packages the APK, and checks required native libraries. It supports `workflow_dispatch` and pull-request builds.

These APIs are useful entry points, but their existence alone does not provide a safe complete implementation. The simulation-thread dispatch path, game-message protocol, player identity checks, and snapshot/CRC changes still need to be implemented and reviewed.

## Required implementation order

1. **Engine-safe local mode first**
   - Add a small cheat state owned by the game simulation and reset it on every new match.
   - Add simulation-thread actions for toggling invulnerability/power and depositing +100,000.
   - Add an explicit network-session guard so local-only actions are rejected in multiplayer.
   - Add the Android panel with a persistent open/close handle, draggable position, labels, and persisted visibility/position. Hidden means the panel consumes no touch input.
2. **Automated validation**
   - Add tests or a native test harness for +100,000 exactly once per tap, no money overflow, reset between matches, no effect on enemy units, and local-only rejection in network sessions.
   - Run `Build Android` in GitHub Actions against this branch and inspect the build logs and APK artifact. A green compile is necessary but not proof of gameplay correctness.
3. **Synchronized private LAN**
   - Introduce versioned, deterministic game commands and peer opt-in/compatibility negotiation.
   - Include the feature state in the relevant snapshot/checksum/replay path.
   - Reject the feature for public/online sessions unless that service explicitly supports the protocol.
   - Test two Android devices first, then Android-to-PC only if the PC client uses the same compatible protocol, data patch, and simulation rate.

## Acceptance criteria

- Local mode never silently activates in multiplayer.
- LAN mode cannot activate unless all players consent and protocol/build checks pass.
- Cash adds exactly 100,000 per tap, without duplicate additions from redraws.
- Invulnerability affects only intended friendly units and is deterministic across peers.
- Power remains available without divergent simulation state.
- The menu can be hidden/restored without blocking ordinary game input.
- Android CI build and real-device tests pass before calling the feature complete.

## How to run the Android build (no local toolchain required)

1. Open the repository's **Actions** tab.
2. Select **Build Android**.
3. Click **Run workflow**, select `feat/waru-multiplayer-cheat-menu`, and start the run.
4. Open the run and wait for the build job to finish. If it fails, open the failed step and copy the first meaningful compiler/configuration error.
5. If it succeeds, download the APK artifact from that run. Do not install it as a feature-complete cheat build until the native behavior and LAN acceptance tests are implemented and passed.

GitHub documents manual workflow runs here: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
