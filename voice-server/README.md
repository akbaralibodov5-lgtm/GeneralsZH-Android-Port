# Waru Edition voice signaling server

Node.js/WebSocket signaling relay for audio-only WebRTC rooms. This service does not carry audio media itself; peers send audio directly when ICE permits, and coturn relays it when direct connectivity fails.

## Capacity and free hosting

A free VPS is suitable for a prototype, not a guarantee of 200 active voice users. Oracle Cloud documents Always Free compute resources, including an Ampere A1 allowance equivalent to up to 2 OCPUs and 12 GB RAM, but capacity depends on region and outbound network use remains subject to provider terms: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm

For 200 connected players, use separate rooms of 2–8 people. A full mesh of 8 peers means up to 7 outgoing audio streams per sender; one peer-to-peer room with 200 peers is unsuitable. For a large shared room, use an SFU such as mediasoup or LiveKit. TURN traffic can become the limiting cost even when the VM is free.

## Prerequisites

- Ubuntu 22.04/24.04 VPS with public IPv4, SSH and firewall control.
- DNS name such as voice.example.com.
- HTTPS/WSS TLS termination (Caddy is convenient).
- Node.js 20+ and coturn.
- Existing GeneralsOnline backend support for the two authenticated endpoints below.

## Required backend contract

The signaling service fails closed if these endpoints do not exist. Never trust client-supplied usernames or a room ID alone.

POST AUTH_VERIFY_URL, authenticated with Authorization: Bearer BACKEND_SHARED_TOKEN:
request: {"sessionToken":"<existing app session token>"}
response: {"valid":true,"userId":"stable-account-id"}

POST LOBBY_AUTHORIZE_URL, same server-to-server auth:
request: {"userId":"stable-account-id","roomId":"opaque-authoritative-lobby-id"}
response: {"allowed":true,"roomId":"same-id","participantId":"stable-lobby-member-id"}

The backend must check the existing login/session and current authoritative lobby membership. It must reject kicked users, stale sessions, mismatched room IDs and players from other lobbies.

## Install / run

    cd voice-server
    cp .env.example .env
    # Edit every URL, secret, and ICE server value before starting.
    npm install
    npm run check
    npm start

Keep Node bound to 127.0.0.1 behind Caddy/Nginx. Do not expose the plain Node port publicly. Configure a process manager, OS security updates, firewall rules and log rotation. /healthz reports counts only; restrict it at the reverse proxy.

## Caddy reverse proxy

    voice.example.com {
      reverse_proxy 127.0.0.1:8787
    }

Caddy obtains TLS certificates when DNS points to the VPS and inbound TCP 80/443 are reachable. Allow WSS at /ws.

## coturn

Use the official project: https://github.com/coturn/coturn

Generate a long random shared secret and set it in both TURN_SHARED_SECRET and coturn static-auth-secret. Use TURN REST API temporary credentials; never embed the shared secret in the APK. Configure realm, fingerprint, use-auth-secret, public/private IP as required, and a limited UDP relay range. Open UDP/TCP 3478, TCP 5349 if TLS TURN is enabled, and the configured relay UDP range in cloud rules and host firewall.

Example outline only; replace all placeholders:

    listening-port=3478
    tls-listening-port=5349
    fingerprint
    use-auth-secret
    static-auth-secret=REPLACE_WITH_LONG_RANDOM_SECRET
    realm=voice.example.com
    min-port=49160
    max-port=49200

Never deploy placeholder secrets. Restrict admin/CLI ports and do not expose coturn web-admin publicly.

## Signaling protocol

Client opens wss://voice.example.com/ws and sends:
1. {"type":"authenticate","sessionToken":"..."}
2. {"type":"join","roomId":"<server-issued-lobby-id>"}

Then relay offer, answer and ice messages using a participant ID returned by the server. On lobby leave send {"type":"leave"} and close peer connections. The server forwards only to participants in the same authorized room. Client creates a peer connection per peer and exchanges SDP/ICE.

## Android integration

- Add a tested Google WebRTC Android library to android/app/build.gradle; do not touch native C++ or game networking.
- Add android.permission.RECORD_AUDIO and request it at runtime after the player enables voice. For always-on-in-lobby mode, show a persistent microphone-on indicator and immediate mute/leave control.
- Implement a lifecycle-aware WaruLobbyVoiceController using PeerConnectionFactory, AudioSource, AudioTrack and PeerConnection. Handle peer join/leave, offer/answer/ICE, reconnect/backoff, mute, route selection and cleanup.
- Pass the existing authenticated GeneralsOnline session token and authoritative room ID. Do not invent a second identity or let users type arbitrary room IDs.
- Disconnect on lobby exit, kick, logout, permission revoke or activity teardown. Never call into the game simulation from audio callbacks.

The existing launcher/lobby must expose authoritative room join/leave events and the session token to the controller. The backend endpoints above are required work, not assumed to exist.

## Security and validation

- WSS only; rotate secrets; rate-limit auth/signaling; cap payloads and room size.
- Production integration should revoke active connections on kick/logout/session revocation.
- Do not log session tokens, SDP, ICE candidates or raw audio.
- Test same room (audio works), different rooms (no audio), denied joins, kick/leave, mute, Android background/foreground, Bluetooth, packet loss and TURN-only connectivity.
- Verify replay/checksum/multiplayer behavior is unchanged with voice enabled or disabled.
