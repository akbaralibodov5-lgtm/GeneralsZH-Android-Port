import http from "node:http";
import crypto from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";

const env = process.env;
const HOST = env.HOST || "127.0.0.1";
const PORT = Number(env.PORT || 8787);
const MAX_CONNECTIONS = Number(env.MAX_CONNECTIONS || 500);
const MAX_MESSAGE_BYTES = Number(env.MAX_MESSAGE_BYTES || 65536);
const MAX_MESSAGES = Number(env.MAX_MESSAGES_PER_10_SECONDS || 80);
const MAX_PEERS_PER_ROOM = Number(env.MAX_PEERS_PER_ROOM || 16);
const ALLOWED_ORIGINS = new Set((env.ALLOWED_ORIGINS || "").split(",").map(s => s.trim()).filter(Boolean));
const rooms = new Map();
const clients = new Set();

function required(name) {
  if (!env[name]) throw new Error("Missing required environment variable: " + name);
  return env[name];
}
function send(ws, value) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value));
}
async function backendPost(url, payload) {
  const token = required("BACKEND_SHARED_TOKEN");
  const response = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", "authorization": "Bearer " + token },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(5000)
  });
  if (!response.ok) throw new Error("backend status " + response.status);
  return response.json();
}
async function verifySession(sessionToken) {
  const result = await backendPost(required("AUTH_VERIFY_URL"), { sessionToken });
  if (result?.valid !== true || typeof result.userId !== "string" || !result.userId) throw new Error("invalid session");
  return result.userId;
}
async function authorizeLobby(userId, roomId) {
  const result = await backendPost(required("LOBBY_AUTHORIZE_URL"), { userId, roomId });
  if (result?.allowed !== true || result.roomId !== roomId) throw new Error("not a lobby member");
  if (typeof result.participantId !== "string" || !result.participantId) throw new Error("missing participant id");
  return result.participantId;
}
function iceServers(userId) {
  const result = [];
  if (env.STUN_URL) result.push({ urls: env.STUN_URL });
  if (env.TURN_SHARED_SECRET && (env.TURN_URL || env.TURN_TLS_URL)) {
    const ttl = Math.max(300, Math.min(Number(env.TURN_CREDENTIAL_TTL_SECONDS || 3600), 86400));
    const username = (Math.floor(Date.now() / 1000) + ttl) + ":" + userId;
    const credential = crypto.createHmac("sha1", required("TURN_SHARED_SECRET")).update(username).digest("base64");
    result.push({ urls: [env.TURN_URL, env.TURN_TLS_URL].filter(Boolean), username, credential });
  }
  return result;
}
function removeFromRoom(client) {
  if (!client.roomId) return;
  const room = rooms.get(client.roomId);
  if (room) {
    room.delete(client.participantId);
    for (const peer of room.values()) send(peer.ws, { type: "peerLeft", participantId: client.participantId });
    if (room.size === 0) rooms.delete(client.roomId);
  }
  client.roomId = null;
  client.participantId = null;
}
function closeClient(client, code = 1008, reason = "policy violation") {
  removeFromRoom(client);
  try { client.ws.close(code, reason.slice(0, 120)); } catch {}
}
function error(client, code) { send(client.ws, { type: "error", code }); }

async function handleMessage(client, raw) {
  if (raw.length > MAX_MESSAGE_BYTES) return closeClient(client, 1009, "message too large");
  const now = Date.now();
  client.messageTimes = client.messageTimes.filter(t => now - t < 10000);
  client.messageTimes.push(now);
  if (client.messageTimes.length > MAX_MESSAGES) return closeClient(client, 1008, "rate limit");
  let msg;
  try { msg = JSON.parse(raw.toString()); } catch { return error(client, "BAD_JSON"); }
  if (!msg || typeof msg.type !== "string") return error(client, "BAD_MESSAGE");

  if (msg.type === "authenticate") {
    if (client.userId) return error(client, "ALREADY_AUTHENTICATED");
    if (typeof msg.sessionToken !== "string" || msg.sessionToken.length < 16 || msg.sessionToken.length > 4096)
      return closeClient(client, 1008, "invalid session token");
    try {
      client.userId = await verifySession(msg.sessionToken);
      send(client.ws, { type: "authenticated", userId: client.userId });
    } catch {
      error(client, "AUTH_FAILED");
      closeClient(client, 1008, "authentication failed");
    }
    return;
  }
  if (!client.userId) return closeClient(client, 1008, "authenticate first");

  if (msg.type === "join") {
    if (client.roomId) return error(client, "ALREADY_IN_ROOM");
    if (typeof msg.roomId !== "string" || msg.roomId.length < 1 || msg.roomId.length > 160) return error(client, "BAD_ROOM_ID");
    try {
      const participantId = await authorizeLobby(client.userId, msg.roomId);
      let room = rooms.get(msg.roomId);
      if (!room) { room = new Map(); rooms.set(msg.roomId, room); }
      if (room.size >= MAX_PEERS_PER_ROOM) return error(client, "ROOM_FULL");
      if ([...room.values()].some(p => p.userId === client.userId)) return error(client, "DUPLICATE_SESSION");
      client.roomId = msg.roomId;
      client.participantId = participantId;
      room.set(participantId, { ws: client.ws, userId: client.userId });
      send(client.ws, { type: "joined", roomId: client.roomId, participantId,
        participants: [...room.keys()].filter(id => id !== participantId), iceServers: iceServers(client.userId) });
      for (const [id, peer] of room.entries()) if (id !== participantId) send(peer.ws, { type: "peerJoined", participantId });
    } catch { error(client, "LOBBY_AUTHORIZATION_FAILED"); }
    return;
  }
  if (msg.type === "leave") {
    removeFromRoom(client);
    send(client.ws, { type: "left" });
    return;
  }
  if (["offer", "answer", "ice"].includes(msg.type)) {
    if (!client.roomId || !client.participantId) return error(client, "NOT_IN_ROOM");
    const room = rooms.get(client.roomId);
    const target = typeof msg.to === "string" ? room?.get(msg.to) : null;
    if (!target || target.ws === client.ws) return error(client, "INVALID_RECIPIENT");
    const payload = { type: msg.type, from: client.participantId };
    if (msg.type === "offer" || msg.type === "answer") {
      if (typeof msg.sdp !== "string" || msg.sdp.length > 48000) return error(client, "BAD_SDP");
      payload.sdp = msg.sdp;
    } else {
      if (!msg.candidate || typeof msg.candidate.candidate !== "string" || msg.candidate.candidate.length > 4096)
        return error(client, "BAD_ICE");
      payload.candidate = msg.candidate;
    }
    send(target.ws, payload);
    return;
  }
  error(client, "UNKNOWN_MESSAGE");
}

const server = http.createServer((req, res) => {
  if (req.url === "/healthz") {
    res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
    res.end(JSON.stringify({ ok: true, connections: clients.size, rooms: rooms.size }));
    return;
  }
  res.writeHead(404);
  res.end("not found");
});
const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_MESSAGE_BYTES, perMessageDeflate: false });
server.on("upgrade", (req, socket, head) => {
  const origin = req.headers.origin;
  if (origin && ALLOWED_ORIGINS.size && !ALLOWED_ORIGINS.has(origin)) {
    socket.write("HTTP/1.1 403 Forbidden\r\n\r\n"); socket.destroy(); return;
  }
  if (clients.size >= MAX_CONNECTIONS) {
    socket.write("HTTP/1.1 503 Service Unavailable\r\n\r\n"); socket.destroy(); return;
  }
  if (req.url !== "/ws") {
    socket.write("HTTP/1.1 404 Not Found\r\n\r\n"); socket.destroy(); return;
  }
  wss.handleUpgrade(req, socket, head, ws => wss.emit("connection", ws, req));
});
wss.on("connection", ws => {
  const client = { ws, userId: null, roomId: null, participantId: null, messageTimes: [] };
  clients.add(client);
  ws.on("message", raw => { void handleMessage(client, raw).catch(() => closeClient(client, 1011, "internal error")); });
  ws.on("close", () => { removeFromRoom(client); clients.delete(client); });
  ws.on("error", () => { removeFromRoom(client); clients.delete(client); });
});
server.listen(PORT, HOST, () => console.log("Waru voice signaling listening on " + HOST + ":" + PORT));
function shutdown() {
  for (const client of clients) closeClient(client, 1001, "server shutting down");
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 5000).unref();
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
