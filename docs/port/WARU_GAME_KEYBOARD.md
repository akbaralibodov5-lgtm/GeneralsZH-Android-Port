# Waru Edition full PC keyboard overlay

The in-game overlay provides a draggable, translucent PC-style keyboard over the Android SDL game screen. Key presses are forwarded to SDL as key-down and key-up events, so the game's existing input bindings remain authoritative.

## Full keyboard

Includes Escape, F1–F12, Print Screen, Scroll Lock, Pause, the number row and punctuation, Tab, Caps Lock, QWERTY letters, Shift/Ctrl/Alt/Windows/Menu keys, Space, Enter, Backspace, Insert/Home/Page Up/Delete/End/Page Down, arrow keys, and the full numeric keypad.

## Controls

- Drag the WARU header to move the keyboard.
- Open ⚙ settings to choose Red, Blue, Gold, or Silver; change scale and opacity; and independently toggle every key.
- Disabled keys are removed from the keyboard layout. Rows reflow and the keyboard width/height shrinks as keys are disabled.
- Settings are paginated so every key can be switched individually.
- **–** switches to a compact WASD + arrow pad.
- **×** hides the full keyboard and leaves a small keyboard toggle; tap it to restore the keyboard.
- Press and release emits matching SDL key-down/key-up events. All held keys are released on touch cancellation and when the activity pauses.
- The selected theme, size, opacity, enabled keys, position and visibility are saved in app preferences.

## Validation checklist

Build the Android target and test on a real device. Verify all key labels and scancodes, per-key toggles and reflow, settings pagination, drag boundaries, screen rotation, modifier combinations, touch pass-through outside the visible board, app pause/resume, and multiplayer lobby/game input. A successful CI build is not a substitute for device testing.


## Single-player simulation speed

The speed control is a small button in the bottom-right corner so it does not permanently cover the game menu or battlefield. Tap it once to open the **− / speed / +** controls; tap it again to close them. Tap the displayed speed to return to 1×. Available speeds are 0.25×, 0.5×, 0.75×, 1×, 1.5×, 2×, 3× and 4×. When the panel is closed, touches outside the corner button pass through to the game. The selection is stored in the same app preferences as the keyboard. Native frame pacing applies the multiplier only in single-player and skirmish modes; LAN, Internet multiplayer and replay modes remain at 1×.

### Speed-control validation

- In a campaign mission and in skirmish, verify 0.25×, 0.5×, 1×, 2× and 4× against unit movement, construction, resource income, weapon fire and AI behavior.
- Verify the corner toggle opens and closes the panel; when closed, camera gestures, menu taps and keyboard input pass through everywhere except the small toggle.
- Enter LAN/Internet multiplayer and replay modes after selecting 4×. Confirm gameplay remains at 1× and no simulation desynchronization occurs.
- Reopen the game and verify the chosen single-player speed persists; confirm a multiplayer selection is forced back to 1×.
- Run the Android build and replay compatibility tests. Real-device gameplay validation is required before calling this feature fully verified.
