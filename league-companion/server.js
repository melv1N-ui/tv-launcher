#!/usr/bin/env node
// Rift Remote – steuert den League Client vom Handy aus.
// Läuft auf dem Gaming-PC, spricht lokal mit dem Client und liefert eine Weboberfläche ins WLAN.

const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { exec } = require('child_process');
const { LCU } = require('./lcu');
const { DemoLCU } = require('./demo');

const DEMO = process.argv.includes('--demo');
const NO_OPEN = process.argv.includes('--no-open');
const CONFIG_FILE = path.join(__dirname, 'config.json');
const PUBLIC_DIR = path.join(__dirname, 'public');

// ---------------- Einstellungen ----------------

function loadConfig() {
  let cfg = {};
  try { cfg = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8')); } catch {}
  const defaults = { port: 8765, token: crypto.randomBytes(12).toString('base64url'), autoAccept: false, leaguePath: '', ntfyTopic: '' };
  cfg = { ...defaults, ...cfg };
  saveConfig(cfg);
  return cfg;
}

function saveConfig(cfg) {
  try { fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2)); } catch (e) { console.warn('config.json konnte nicht gespeichert werden:', e.message); }
}

const config = loadConfig();
if (process.env.PORT) config.port = Number(process.env.PORT);
const lcu = DEMO ? new DemoLCU() : new LCU(config.leaguePath);

// ---------------- Daten aus dem Client (zwischengespeichert) ----------------

const cache = { champions: null, spells: null, queues: null, summoner: null, gameMode: null };
const assetCache = new Map();
const PREFERRED_QUEUES = [420, 440, 400, 490, 480, 450];

async function loadStatic() {
  if (!cache.champions) {
    const list = await lcu.get('/lol-game-data/assets/v1/champion-summary.json');
    if (Array.isArray(list)) {
      cache.champions = list.filter((c) => c.id > 0).map(({ id, name, alias }) => ({ id, name, alias })).sort((a, b) => a.name.localeCompare(b.name, 'de'));
    }
  }
  if (!cache.spells) {
    const list = await lcu.get('/lol-game-data/assets/v1/summoner-spells.json');
    if (Array.isArray(list)) cache.spells = list.filter((s) => s.id > 0 && s.iconPath).map(({ id, name, iconPath, gameModes }) => ({ id, name, iconPath, gameModes: gameModes || [] }));
  }
  if (!cache.queues) {
    const list = await lcu.get('/lol-game-queues/v1/queues');
    if (Array.isArray(list)) cache.queues = list;
  }
  if (!cache.summoner) {
    const s = await lcu.get('/lol-summoner/v1/current-summoner');
    if (s) cache.summoner = { name: s.gameName || s.displayName || '', tag: s.tagLine || '', iconId: s.profileIconId };
  }
}

function resetCache() {
  for (const k of Object.keys(cache)) cache[k] = null;
  assetCache.clear();
}

function queueName(id) {
  const q = cache.queues && cache.queues.find((x) => x.id === id);
  return q ? q.description || q.shortName || q.name || `Queue ${id}` : id ? `Queue ${id}` : '';
}

function availableQueues() {
  if (!cache.queues) return [];
  return PREFERRED_QUEUES
    .map((id) => cache.queues.find((q) => q.id === id && (!q.queueAvailability || q.queueAvailability === 'Available')))
    .filter(Boolean)
    .map((q) => ({ id: q.id, name: queueName(q.id) }));
}

// ---------------- Zustand zusammenbauen ----------------

function champSelectView(s, pickable, bannable, pages) {
  const me = s.localPlayerCellId;
  const actions = s.actions.flat().filter((a) => a.type === 'pick' || a.type === 'ban');
  const stage = (s.timer && s.timer.phase) || '';
  const active = actions.find((a) => a.actorCellId === me && a.isInProgress && !a.completed);
  const nextMine = actions.find((a) => a.actorCellId === me && !a.completed);
  const myPick = actions.find((a) => a.actorCellId === me && a.type === 'pick' && !a.completed);
  const acting = new Set(actions.filter((a) => a.isInProgress && !a.completed).map((a) => a.actorCellId));

  let mode = 'wait';
  let action = null;
  if (active) { mode = active.type; action = active; }
  else if (stage === 'PLANNING' && myPick) { mode = 'intent'; action = myPick; }
  else if (stage === 'FINALIZATION' || !nextMine) mode = 'done';

  const unavailable = new Set();
  for (const a of actions) if (a.completed && a.championId > 0) unavailable.add(a.championId);
  const bans = s.bans || {};
  for (const id of [...(bans.myTeamBans || []), ...(bans.theirTeamBans || [])]) if (id > 0) unavailable.add(id);
  const allyCells = new Set((s.myTeam || []).map((p) => p.cellId));
  const bannedMine = new Set((bans.myTeamBans || []).filter((id) => id > 0));
  const bannedTheirs = new Set((bans.theirTeamBans || []).filter((id) => id > 0));
  for (const a of actions) {
    if (a.type === 'ban' && a.completed && a.championId > 0) (allyCells.has(a.actorCellId) ? bannedMine : bannedTheirs).add(a.championId);
  }

  const mapPlayer = (p, ally) => ({
    cellId: p.cellId,
    championId: p.championId || 0,
    intent: p.championPickIntent || 0,
    position: (p.assignedPosition || '').toLowerCase(),
    name: ally ? p.gameName || p.summonerName || '' : '',
    isMe: p.cellId === me,
    acting: acting.has(p.cellId),
    spells: ally ? [p.spell1Id, p.spell2Id] : [],
  });
  const myTeam = (s.myTeam || []).map((p) => mapPlayer(p, true));
  const self = myTeam.find((p) => p.isMe);

  return {
    stage,
    timeLeftMs: (s.timer && s.timer.adjustedTimeLeftInPhase) || 0,
    totalMs: (s.timer && s.timer.totalTimeInPhase) || 0,
    timerAt: (s.timer && s.timer.internalNowInEpochMs) || Date.now(),
    mode,
    currentType: (actions.find((a) => a.isInProgress && !a.completed) || {}).type || null,
    action: action ? { id: action.id, type: action.type, championId: action.championId || 0 } : null,
    nextType: nextMine ? nextMine.type : null,
    myTeam,
    theirTeam: (s.theirTeam || []).map((p) => mapPlayer(p, false)),
    bans: { mine: [...bannedMine], theirs: [...bannedTheirs] },
    unavailable: [...unavailable],
    pickable: pickable || [],
    bannable: bannable || [],
    spells: self ? self.spells : [],
    gameMode: cache.gameMode,
    runePages: (pages || []).filter((p) => p.isValid !== false).map((p) => ({ id: p.id, name: p.name, current: !!p.current })),
  };
}

async function buildState() {
  const base = { demo: DEMO, autoAccept: config.autoAccept, serverNow: Date.now() };
  if (!(await lcu.ensure())) return { ...base, connected: false };

  let phase;
  try {
    phase = (await lcu.request('GET', '/lol-gameflow/v1/gameflow-phase')).body;
  } catch {
    return { ...base, connected: false };
  }
  if (typeof phase !== 'string') return { ...base, connected: false };
  await loadStatic().catch(() => {});

  const state = { ...base, connected: true, phase, summoner: cache.summoner, queues: availableQueues() };

  if (['None', 'Lobby', 'Matchmaking', 'ReadyCheck'].includes(phase)) {
    const lobby = await lcu.get('/lol-lobby/v2/lobby');
    if (lobby && lobby.gameConfig) state.lobby = { queueId: lobby.gameConfig.queueId, queueName: queueName(lobby.gameConfig.queueId) };
  }
  if (phase === 'Matchmaking') {
    const s = await lcu.get('/lol-matchmaking/v1/search');
    if (s) state.search = { state: s.searchState, timeInQueue: s.timeInQueue || 0, estimated: s.estimatedQueueTime || 0, error: (s.errors && s.errors[0] && s.errors[0].message) || '' };
  }
  if (phase === 'ReadyCheck') {
    const r = await lcu.get('/lol-matchmaking/v1/ready-check');
    if (r) state.readyCheck = { state: r.state, response: r.playerResponse, elapsed: r.timer || 0 };
  }
  if (phase === 'ChampSelect') {
    if (!cache.gameMode) {
      const flow = await lcu.get('/lol-gameflow/v1/session');
      cache.gameMode = (flow && flow.gameData && flow.gameData.queue && flow.gameData.queue.gameMode) || 'CLASSIC';
    }
    const [session, pickable, bannable, pages] = await Promise.all([
      lcu.get('/lol-champ-select/v1/session'),
      lcu.get('/lol-champ-select/v1/pickable-champion-ids'),
      lcu.get('/lol-champ-select/v1/bannable-champion-ids'),
      lcu.get('/lol-perks/v1/pages'),
    ]);
    if (session && session.actions) state.champSelect = champSelectView(session, pickable, bannable, pages);
  } else {
    cache.gameMode = null;
  }
  return state;
}

// ---------------- Abfrage-Schleife & Live-Updates (Server-Sent Events) ----------------

const clients = new Set();
let current = { connected: false };
let lastJson = '';
let pollTimer = null;
let polling = false;
let wasConnected = false;

function broadcast(state) {
  const data = `data: ${JSON.stringify(state)}\n\n`;
  for (const res of clients) res.write(data);
}

async function poll() {
  if (polling) return;
  polling = true;
  clearTimeout(pollTimer);
  try {
    const prev = current;
    const next = await buildState();
    if (next.connected !== wasConnected) {
      wasConnected = next.connected;
      if (!next.connected) resetCache();
      console.log(next.connected ? '✔ League Client verbunden' : '… warte auf League Client');
    }
    current = next;
    const { serverNow, ...comparable } = next;
    const json = JSON.stringify(comparable);
    if (json !== lastJson) {
      lastJson = json;
      broadcast(next);
      onTransition(prev, next);
    }
  } catch (e) {
    console.warn('Fehler beim Abfragen:', e.message);
  } finally {
    polling = false;
    const fast = ['ReadyCheck', 'ChampSelect', 'Matchmaking'].includes(current.phase);
    pollTimer = setTimeout(poll, current.connected ? (fast ? 500 : 1500) : 3000);
  }
}

let autoAcceptTimer = null;
function onTransition(prev, next) {
  if (next.phase === 'ReadyCheck' && prev.phase !== 'ReadyCheck') {
    notify('Match gefunden!', config.autoAccept ? 'Wird automatisch angenommen.' : 'Tippe zum Annehmen.');
  }
  if (config.autoAccept && next.phase === 'ReadyCheck' && next.readyCheck && next.readyCheck.response === 'None' && !autoAcceptTimer) {
    autoAcceptTimer = setTimeout(async () => {
      autoAcceptTimer = null;
      await lcu.request('POST', '/lol-matchmaking/v1/ready-check/accept').catch(() => {});
      poll();
    }, 1200);
  }
  const a = next.champSelect && next.champSelect.mode;
  const pa = prev.champSelect && prev.champSelect.mode;
  if ((a === 'ban' || a === 'pick') && a !== pa) notify(a === 'ban' ? 'Du bist dran: Bannen' : 'Du bist dran: Champion wählen', '');
}

// Optional: echte Push-Nachrichten aufs gesperrte Handy über ntfy.sh (Thema in config.json eintragen).
function notify(title, message) {
  console.log(`🔔 ${title}`);
  if (!config.ntfyTopic) return;
  fetch(`https://ntfy.sh/${encodeURIComponent(config.ntfyTopic)}`, {
    method: 'POST',
    body: message || title,
    headers: { Title: encodeURIComponent(title).length === title.length ? title : 'Rift Remote', Priority: 'high', Tags: 'crossed_swords' },
  }).catch(() => {});
}

// ---------------- Aktionen vom Handy ----------------

async function lcuCall(method, p, body) {
  const r = await lcu.request(method, p, body);
  if (r.status >= 400) {
    const msg = (r.body && (r.body.message || r.body.errorCode)) || `Fehler ${r.status}`;
    throw new Error(msg);
  }
  return r.body;
}

async function handleAction(body) {
  const cs = current.champSelect;
  switch (body.type) {
    case 'accept': return lcuCall('POST', '/lol-matchmaking/v1/ready-check/accept');
    case 'decline': return lcuCall('POST', '/lol-matchmaking/v1/ready-check/decline');
    case 'startQueue': return lcuCall('POST', '/lol-lobby/v2/lobby/matchmaking/search');
    case 'cancelQueue': return lcuCall('DELETE', '/lol-lobby/v2/lobby/matchmaking/search');
    case 'createLobby': return lcuCall('POST', '/lol-lobby/v2/lobby', { queueId: Number(body.queueId) });
    case 'playAgain': return lcuCall('POST', '/lol-lobby/v2/play-again');
    case 'hover': {
      if (!cs || !cs.action) throw new Error('Gerade nicht dran');
      return lcuCall('PATCH', `/lol-champ-select/v1/session/actions/${cs.action.id}`, { championId: Number(body.championId) });
    }
    case 'lock': {
      if (!cs || !cs.action || cs.mode === 'intent') throw new Error('Gerade nicht dran');
      const championId = Number(body.championId) || cs.action.championId;
      if (!championId) throw new Error('Erst einen Champion auswählen');
      await lcuCall('PATCH', `/lol-champ-select/v1/session/actions/${cs.action.id}`, { championId });
      try {
        return await lcuCall('POST', `/lol-champ-select/v1/session/actions/${cs.action.id}/complete`);
      } catch {
        return lcuCall('PATCH', `/lol-champ-select/v1/session/actions/${cs.action.id}`, { championId, completed: true });
      }
    }
    case 'spells': return lcuCall('PATCH', '/lol-champ-select/v1/session/my-selection', { spell1Id: Number(body.spell1Id), spell2Id: Number(body.spell2Id) });
    case 'runePage': return lcuCall('PUT', '/lol-perks/v1/currentpage', Number(body.id));
    case 'autoAccept':
      config.autoAccept = !!body.value;
      saveConfig(config);
      return {};
    default: throw new Error('Unbekannte Aktion');
  }
}

// ---------------- HTTP ----------------

function lanAddresses() {
  const list = [];
  for (const [name, ifs] of Object.entries(os.networkInterfaces())) {
    for (const i of ifs || []) {
      if (i.family === 'IPv4' && !i.internal) list.push({ name, address: i.address });
    }
  }
  const rank = (a) => (a.startsWith('192.168.') ? 0 : a.startsWith('10.') ? 1 : a.startsWith('172.') ? 2 : a.startsWith('100.') ? 3 : 4);
  return list.sort((a, b) => rank(a.address) - rank(b.address));
}

function isLoopback(req) {
  const a = req.socket.remoteAddress || '';
  return a === '127.0.0.1' || a === '::1' || a === '::ffff:127.0.0.1';
}

function cookieToken(req) {
  const m = (req.headers.cookie || '').match(/(?:^|;\s*)rr_token=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : '';
}

function tokenMatches(t) {
  const a = Buffer.from(String(t || ''));
  const b = Buffer.from(config.token);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function authorized(req, url) {
  return isLoopback(req) || tokenMatches(cookieToken(req)) || tokenMatches(url.searchParams.get('t'));
}

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png' };

function send(res, status, body, type = 'application/json; charset=utf-8', extra = {}) {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store', ...extra });
  res.end(typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body));
}

function serveFile(res, file, extra = {}) {
  const full = path.join(PUBLIC_DIR, file);
  if (!full.startsWith(PUBLIC_DIR)) return send(res, 404, 'not found', 'text/plain');
  fs.readFile(full, (err, buf) => {
    if (err) return send(res, 404, 'not found', 'text/plain');
    send(res, 200, buf, MIME[path.extname(full)] || 'application/octet-stream', extra);
  });
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 1e5) req.destroy(); });
    req.on('end', () => { try { resolve(JSON.parse(data || '{}')); } catch { resolve({}); } });
  });
}

const LOCKED_PAGE = `<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Rift Remote</title><body style="margin:0;min-height:100vh;display:grid;place-items:center;background:#0b0814;color:#e9e4ff;font:16px system-ui;text-align:center;padding:24px">
<div><h1 style="font-size:22px">Nicht gekoppelt</h1><p style="opacity:.7;max-width:320px">Öffne auf dem PC <b>http://localhost:${config.port}/pair</b> und scanne den QR-Code mit diesem Handy.</p></div></body></html>`;

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;

  if (p === '/pair') {
    if (!isLoopback(req)) return send(res, 403, 'Nur am PC selbst erreichbar.', 'text/plain; charset=utf-8');
    return serveFile(res, 'pair.html');
  }
  if (p === '/api/pair-info') {
    if (!isLoopback(req)) return send(res, 403, {});
    return send(res, 200, { port: config.port, token: config.token, addresses: lanAddresses() });
  }
  if (p === '/manifest.webmanifest' || p === '/icon.svg') return serveFile(res, p.slice(1));
  if (p === '/qrcode.js' && isLoopback(req)) return serveFile(res, 'qrcode.js');

  if (!authorized(req, url)) {
    return p.startsWith('/api') || p === '/events' ? send(res, 401, { error: 'unauthorized' }) : send(res, 401, LOCKED_PAGE, 'text/html; charset=utf-8');
  }

  // Beim ersten Öffnen über den QR-Link das Token als Cookie merken.
  const setCookie = tokenMatches(url.searchParams.get('t'))
    ? { 'Set-Cookie': `rr_token=${encodeURIComponent(config.token)}; Path=/; Max-Age=31536000; HttpOnly; SameSite=Lax` }
    : {};

  if (p === '/' || p === '/index.html') return serveFile(res, 'index.html', setCookie);
  if (p === '/app.js' || p === '/style.css') return serveFile(res, p.slice(1));

  if (p === '/events') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
    res.write('retry: 2000\n\n');
    res.write(`data: ${JSON.stringify({ ...current, serverNow: Date.now() })}\n\n`);
    clients.add(res);
    const ping = setInterval(() => res.write(`: ping ${Date.now()}\n\n`), 15000);
    req.on('close', () => { clients.delete(res); clearInterval(ping); });
    return;
  }

  if (p === '/api/champions') {
    await loadStatic().catch(() => {});
    return send(res, 200, { champions: cache.champions || [], spells: cache.spells || [] });
  }

  if (p === '/api/asset') {
    const ap = url.searchParams.get('p') || '';
    if (!/^\/lol-game-data\/assets\/[\w\-./%]+$/.test(ap) || ap.includes('..')) return send(res, 400, {});
    if (assetCache.has(ap)) {
      const c = assetCache.get(ap);
      return send(res, 200, c.body, c.type, { 'Cache-Control': 'public, max-age=86400' });
    }
    try {
      const r = await lcu.request('GET', ap, undefined, { raw: true });
      if (r.status !== 200) return send(res, 404, {});
      assetCache.set(ap, { body: r.body, type: r.contentType || 'image/png' });
      return send(res, 200, r.body, r.contentType || 'image/png', { 'Cache-Control': 'public, max-age=86400' });
    } catch {
      return send(res, 502, {});
    }
  }

  if (p === '/api/action' && req.method === 'POST') {
    const body = await readBody(req);
    try {
      await handleAction(body);
      setTimeout(poll, 120);
      return send(res, 200, { ok: true });
    } catch (e) {
      setTimeout(poll, 120);
      return send(res, 200, { ok: false, error: e.message });
    }
  }

  send(res, 404, { error: 'not found' });
});

server.listen(config.port, '0.0.0.0', () => {
  const addrs = lanAddresses();
  const ip = addrs[0] ? addrs[0].address : 'localhost';
  console.log('');
  console.log(`  Rift Remote läuft${DEMO ? ' (DEMO-MODUS)' : ''}`);
  console.log(`  Koppeln (am PC öffnen):  http://localhost:${config.port}/pair`);
  console.log(`  Direkt-Link fürs Handy:  http://${ip}:${config.port}/?t=${config.token}`);
  console.log('');
  if (!NO_OPEN) openBrowser(`http://localhost:${config.port}/pair`);
  poll();
});

server.on('error', (e) => {
  if (e.code === 'EADDRINUSE') console.error(`Port ${config.port} ist belegt. Läuft Rift Remote schon? Sonst in config.json einen anderen Port eintragen.`);
  else console.error(e);
  process.exit(1);
});

function openBrowser(u) {
  const cmd = process.platform === 'win32' ? `start "" "${u}"` : process.platform === 'darwin' ? `open "${u}"` : `xdg-open "${u}"`;
  exec(cmd, () => {});
}
