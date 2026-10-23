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

