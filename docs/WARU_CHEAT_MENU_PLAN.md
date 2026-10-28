# Waru Edition Cheat Menu — implementation plan

This document defines the two requested modes. It is a plan, not a claim that the in-game cheats have already been implemented.

## Modes

### 1. Local-only (single-device) mode
- Android overlay can be opened, hidden, and fully disabled.
- Toggles: invulnerable friendly units, infinite power, and +100,000 cash button.
- This mode is for offline/skirmish or private testing only. Do not enable state-changing cheats in a network match: client-only state changes can cause desync/mismatch.

### 2. Synchronized private-LAN mode
- Cheats are available only when every participant explicitly opts in to the same private-match rules.
- Cheat actions must be represented as deterministic game commands processed in the simulation, not as local UI edits.
- All participants must use a compatible build/patch and the same cheat protocol/version.
- Each action needs a stable command ID and deterministic target/player selection; all toggles and cash changes must be included in simulation state/checksums.
- The host should reject a cheat action if a peer lacks the compatible feature/version or has not accepted the rules. If agreement is absent, disable synchronized cheats for the match.
- No claim of mismatch-free operation until LAN tests pass with checksum/desync logging.

## UI requirements
- Compact draggable Android overlay; explicit open/close button.
- A separate visibility toggle and master enable/disable switch.
- Per-cheat switches; cash action adds exactly 100,000 once per tap.
- Touch targets must not intercept ordinary game controls while hidden.
- Accessibility labels and persisted UI position/visibility preference.

## Engine work required
- Find the game-logic owner/player APIs for cash, object damage/health, and power production/consumption.
- Add deterministic simulation commands for each cheat, rather than modifying only Android-side state.
- Define and serialize synchronized cheat state; include it in snapshot/CRC paths as appropriate.
- Gate use by private-match opt-in and build/protocol compatibility.
- Test offline first, then two Android devices, then Android-to-PC LAN with matching simulation rate and data patch.
- Test toggling each feature mid-match, late-joining/reconnect behavior if supported, replay/snapshot consistency, and cheat disable/reenable.

## Acceptance criteria
- Local mode never silently activates in multiplayer.
- LAN mode cannot activate unless all players consent and protocol/build checks pass.
- Cash adds exactly 100,000 per tap; no repeated additions from UI redraws.
- Unit invulnerability affects only the intended friendly units and behaves deterministically on all peers.
- Power remains available without introducing divergent simulation state.
- Menu can be hidden and restored without blocking game input.
- CI build and real-device LAN tests pass before describing the feature as complete.
