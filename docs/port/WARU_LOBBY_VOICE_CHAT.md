# Waru Edition lobby voice chat (WebRTC)

## Goal

Add an optional, always-on microphone voice channel for players who are in the same Waru/GeneralsOnline lobby. Voice is an out-of-band feature: it must never enter the Generals simulation, game packets, replay stream, RNG, frame loop, or deterministic state.

## Important integration boundary

The Android launcher currently has the GeneralsOnline account/login screen, while actual game lobby membership is owned by the GeneralsOnline multiplayer client/backend. A voice room must be keyed by a server-authoritative lobby/match ID and authenticated player identity. Do not key a room by a display name, IP address, or user-entered room code. Do not infer room membership from the game process being open.

WebRTC does not provide signaling itself. The backend must authenticate the player, authorize room membership, and relay offer/answer/ICE messages. STUN/TURN configuration is also required for networks where direct peer connectivity fails. See https://webrtc.org/getting-started/peer-connections

## Proposed architecture

1. **Lobby event source (existing GeneralsOnline backend/client):** expose authenticated events for lobby joined, lobby left, kicked/banned, match started/ended, and peer presence. Include an opaque room ID and stable participant IDs.
2. **Voice signaling endpoint (backend work required):** secure WebSocket (WSS) endpoint. It must validate the user's existing session/token, confirm membership on every join/message, and relay only signaling messages between members of the same room. It must not trust a client-supplied room ID alone.
3. **Android voice controller:** a lifecycle-aware Java/Kotlin component owned by the launcher/activity layer, not C++ game logic. Use native WebRTC audio only (no camera/video). Connect when the user is in a lobby and voice is enabled; disconnect immediately on leave/kick/logout, match exit, or permission revocation.
4. **Audio:** request RECORD_AUDIO at runtime. Start microphone capture only after explicit user opt-in; show an unmistakable active-mic indicator and provide a one-tap mute/leave control. Use WebRTC audio processing/echo cancellation and Android communication audio routing where supported. Handle audio focus, headset/Bluetooth routing, phone calls, and activity pause/resume.
5. **UI:** lobby participant list shows voice-connected/muted state. Voice room includes a persistent mic toggle and speaker/headset route control. If signaling or voice fails, the game lobby continues normally.

## Suggested signaling contract

All messages are JSON over WSS and carry a protocol version. The server derives the sender ID from the authenticated session; clients cannot spoof it.

- Client -> server: join { protocolVersion, roomId }
- Server -> client: joined { roomId, selfId, participants, iceServers }
- Client -> server: offer { to, sdp }, answer { to, sdp }, ice { to, candidate, sdpMid, sdpMLineIndex }
- Server -> room: peerJoined { participantId }, peerLeft { participantId }
- Server -> client: offer/answer/ice { from, ... }, error { code }
- Client -> server: leave { roomId }

The server must reject room joins for non-members, limit message sizes/rates, validate recipient membership, and use short-lived TURN credentials. Use TLS/WSS and the project's established authentication/session mechanism; never embed a TURN secret in the APK.

## Multiplayer mismatch protection

- Do not add voice state to engine GameLogic, player state, simulation packets, replay records, checksums, or deterministic update functions.
- No voice callbacks may call gameplay/native engine functions.
- Run signaling and WebRTC on background threads; marshal UI updates to the Android main thread.
- Audio/network failures are non-fatal and must not stop, pause, or mutate the game.
- Voice is optional and disabled unless the user grants microphone permission and enables it.
- Test two clients in the same lobby, clients in different lobbies (must not hear each other), disconnect/kick/logout, packet loss, NAT requiring TURN, Android lifecycle, headset/Bluetooth, and gameplay checksum/replay parity.

## Implementation order

1. Add server-side authenticated lobby signaling and publish its WSS URL + message schema.
2. Add Android WebRTC dependency, RECORD_AUDIO permission and runtime permission flow.
3. Implement WaruLobbyVoiceController for signaling, peer lifecycle, audio-only tracks, mute/leave, and cleanup.
4. Connect it to authoritative lobby join/leave/presence events and add lobby UI.
5. Run Android lint/build and two-device integration tests. Verify no engine files or deterministic game protocol changed.

## Current status

This branch starts from fix/unofficial-map-preview-slashes. This document is the integration design. End-to-end voice cannot honestly be marked implemented until the existing lobby's authoritative room events and a compatible authenticated signaling/TURN service are available. Adding a client that guesses an endpoint or trusts a client-provided room ID would be unsafe and would not make cross-player voice actually work.
