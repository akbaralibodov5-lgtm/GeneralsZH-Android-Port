# Waru Edition in-game keyboard

The Android game screen has an optional, translucent PC-style touch keyboard overlay. It feeds SDL3 key-down/key-up events through the same queue used by physical keyboards, so existing engine key bindings remain the source of truth.

## In-game controls

- Tap **⚙** to open the overlay settings.
- Pick one of four palettes: Crimson Red, Waru Blue, Royal Gold, or Silver.
- Use **− / +** to change keyboard size and opacity.
- Press and drag the **WARU** title strip to move the keyboard anywhere on the landscape screen.
- **COMPACT** reduces the controls to a small floating keyboard button; tap it to expand again.
- **HIDE** removes the full keyboard and leaves a small toggle bubble. Tap the bubble to restore the keyboard.
- **×** also hides the full keyboard; the small bubble remains available.
- Key presses emit matching key-up events on release/cancel to avoid stuck modifiers.

The selected palette, position, scale, opacity, compact state, and visibility persist in app preferences. The overlay is click-through outside its visible bounds, so battlefield touches continue to reach the game.

## Included keys

Escape, Tab, Q/W/E/R, 1–5, Ctrl, A/S/D/F/G, Shift, Z/X/C/V, Space, Alt, arrows, and Enter. This is a touch overlay, not a replacement for a physical keyboard; game actions still depend on the game's existing bindings.

## Validation

Build the Android target and test on a real device in a skirmish and multiplayer lobby. In particular verify key down/up, multi-touch modifiers, touch pass-through outside the keyboard, rotation/insets, and no stuck key after app pause.
