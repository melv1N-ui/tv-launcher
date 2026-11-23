'use strict';

// ---------------- Hilfen ----------------

const $ = (id) => document.getElementById(id);
const asset = (p) => '/api/asset?p=' + encodeURIComponent(p);
const champIcon = (id) => asset(`/lol-game-data/assets/v1/champion-icons/${id}.png`);
const POS = { top: 'TOP', jungle: 'JUNGLE', middle: 'MID', bottom: 'ADC', utility: 'SUPPORT' };

function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'text') el.textContent = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) if (kid != null) el.append(kid);
  return el;
}

const norm = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]/gi, '').toLowerCase();
const mmss = (sec) => {
  sec = Math.max(0, Math.floor(sec));
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;
};

let state = {};
let offset = 0; // Uhr des PCs minus Uhr des Handys
const serverNow = () => Date.now() + offset;
let champions = [];
let spells = [];
const champById = new Map();
let sseOk = false;
let unauthorized = false;

function toast(msg, err = false) {
  const t = $('toast');
  t.textContent = msg;
  t.className = 'toast' + (err ? ' err' : '');
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.hidden = true), 2600);
}

async function act(type, data = {}, btn) {
  if (btn) btn.disabled = true;
  try {
    const r = await fetch('/api/action', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type, ...data }) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok || !j.ok) { toast(j.error || 'Hat nicht geklappt', true); return false; }
    return true;
  } catch {
    toast('Keine Verbindung zum PC', true);
    return false;
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function loadChampions() {
  if (loadChampions.busy || champions.length) return;
  loadChampions.busy = true;
  try {
    const j = await (await fetch('/api/champions')).json();
    champions = j.champions || [];
    spells = j.spells || [];
    for (const c of champions) champById.set(c.id, c);
    if (champions.length && view && view.update) view.update(state);
  } catch {}
  loadChampions.busy = false;
}

const recent = {
  get() { try { return JSON.parse(localStorage.getItem('rr_recent') || '[]'); } catch { return []; } },
  add(id) {
    try { localStorage.setItem('rr_recent', JSON.stringify([id, ...this.get().filter((x) => x !== id)].slice(0, 12))); } catch {}
  },
};

// ---------------- Ton, Vibration, Bildschirm anlassen ----------------

let audio = null;
document.addEventListener('pointerdown', () => {
  try {
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
    if (audio.state === 'suspended') audio.resume();
  } catch {}
  keepAwake();
}, { passive: true });

function alarm() {
  try { navigator.vibrate && navigator.vibrate([250, 120, 250, 120, 500]); } catch {}
  if (!audio) return;
  const t0 = audio.currentTime;
  [0, 0.18, 0.36].forEach((d, i) => {
    const o = audio.createOscillator();
    const g = audio.createGain();
    o.type = 'triangle';
    o.frequency.value = [660, 880, 1175][i];
    g.gain.setValueAtTime(0.0001, t0 + d);
    g.gain.exponentialRampToValueAtTime(0.35, t0 + d + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + d + 0.16);
    o.connect(g).connect(audio.destination);
    o.start(t0 + d);
    o.stop(t0 + d + 0.18);
  });
}

let wakeLock = null;
async function keepAwake() {
  try { if (!wakeLock && navigator.wakeLock) { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener('release', () => (wakeLock = null)); } } catch {}
}
document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && keepAwake());

// ---------------- Ansichten ----------------

const logoSvg = () => {
  const d = document.createElement('div');
  d.innerHTML = '<svg class="pulse-logo" viewBox="0 0 64 64"><path d="M32 4 56 18v28L32 60 8 46V18z" fill="none" stroke="currentColor" stroke-width="3"/><path d="M32 16 44 32 32 48 20 32z" fill="currentColor"/><circle cx="32" cy="32" r="4" fill="#0b0814"/></svg>';
  return d.firstChild;
};

const views = {
  offline(root) {
    const title = h('div', { class: 'queue-name' });
    const sub = h('div', { class: 'sub' });
    root.append(h('div', { class: 'center' }, logoSvg(), title, sub));
    return {
      update() {
        if (unauthorized) { title.textContent = 'Nicht gekoppelt'; sub.textContent = 'Öffne am PC localhost:8765/pair und scanne den QR-Code.'; }
        else if (!sseOk) { title.textContent = 'PC nicht erreichbar'; sub.textContent = 'Läuft Rift Remote auf dem PC? Bist du im selben WLAN?'; }
        else { title.textContent = 'Warte auf League'; sub.textContent = 'Starte den League Client auf deinem PC.'; }
      },
    };
  },

  lobby(root) {
    const name = h('div', { class: 'queue-name' });
    const chips = h('div', { class: 'chips' });
    const start = h('button', { class: 'btn primary wide', onclick: (e) => act('startQueue', {}, e.currentTarget) }, 'Suche starten');
    root.append(h('div', { class: 'center' }, h('div', { class: 'card' }, h('div', { class: 'label' }, 'Lobby'), h('div', { style: 'height:8px' }), name, chips, start)));
    let chipsKey = '';
    return {
      update(s) {
        name.textContent = s.lobby ? s.lobby.queueName : 'Kein Modus gewählt';
        const key = JSON.stringify(s.queues || []);
        if (key !== chipsKey) {
          chipsKey = key;
          chips.replaceChildren(...(s.queues || []).map((q) => h('button', { class: 'chip', 'data-q': q.id, onclick: (e) => act('createLobby', { queueId: q.id }, e.currentTarget) }, q.name)));
        }
        for (const c of chips.children) c.classList.toggle('on', s.lobby && Number(c.dataset.q) === s.lobby.queueId);
        start.disabled = !s.lobby;
      },
    };
  },

  search(root) {
    const time = h('div', { class: 'big-time' }, '0:00');
    const est = h('div', { class: 'sub' });
    const name = h('div', { class: 'queue-name' });
    const cancel = h('button', { class: 'btn danger', onclick: (e) => act('cancelQueue', {}, e.currentTarget) }, 'Suche abbrechen');
    root.append(h('div', { class: 'center' },
      h('div', { class: 'card' }, h('div', { class: 'label' }, 'Suche läuft'), time, est), name, cancel));
    return {
      update(s) {
        name.textContent = s.lobby ? s.lobby.queueName : '';
        const e = s.search && s.search.estimated;
        est.textContent = s.search && s.search.error ? s.search.error : e ? `Geschätzt: ${mmss(e)}` : '';
        this.tick();
      },
      tick() {
        const s = state.search;
        if (!s) return;
        time.textContent = mmss(s.timeInQueue + (serverNow() - state.serverNow) / 1000);
      },
    };
  },

  ready(root) {
    const C = 2 * Math.PI * 104;
    const wrap = h('div', { class: 'accept-wrap' });
    wrap.innerHTML = `<svg viewBox="0 0 230 230"><circle class="ring-bg" cx="115" cy="115" r="104"/><circle class="ring" cx="115" cy="115" r="104" stroke-dasharray="${C}" stroke-dashoffset="0"/></svg>`;
    const ring = wrap.querySelector('.ring');
    const secs = h('small');
    const accept = h('button', { class: 'accept-btn', onclick: (e) => act('accept', {}, e.currentTarget) }, 'ANNEHMEN', secs);
    wrap.append(accept);
    const status = h('div', { class: 'accepted', hidden: true });
    const decline = h('button', { class: 'btn ghost', onclick: (e) => act('decline', {}, e.currentTarget) }, 'Ablehnen');
    root.append(h('div', { class: 'center' }, h('div', { class: 'label' }, 'Match gefunden'), wrap, status, decline));
    return {
      update(s) {
        const r = (s.readyCheck && s.readyCheck.response) || 'None';
        const done = r !== 'None';
        accept.hidden = done;
        decline.hidden = done;
        status.hidden = !done;
        status.textContent = r === 'Accepted' ? '✓ Angenommen' : r === 'Declined' ? 'Abgelehnt' : '';
        this.tick();
      },
      tick() {
        const rc = state.readyCheck;
        if (!rc) return;
        const left = Math.max(0, 12 - rc.elapsed - (serverNow() - state.serverNow) / 1000);
        ring.style.strokeDashoffset = String(C * (1 - left / 12));
        secs.textContent = `${Math.ceil(left)} Sek.`;
      },
    };
  },

  cs(root) {
    let selected = 0;
    let actionId = null;
    let lastMode = null;

    const stage = h('div', { class: 'cs-stage' });
    const time = h('div', { class: 'cs-time' });
    const barFill = h('i');
    const bansMine = h('div', { class: 'ban-row mine' });
    const bansTheirs = h('div', { class: 'ban-row theirs' });
    const ally = h('div', { class: 'team ally' });
    const enemy = h('div', { class: 'team enemy' });
    const turn = h('div', { class: 'turn' });

    const search = h('input', { class: 'search', type: 'search', placeholder: 'Champion suchen…', autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false' });
    const grid = h('div', { class: 'champ-grid' });
    const lockBtn = h('button', { class: 'btn primary' });
    const picker = h('div', { class: 'picker' }, search, h('div', { class: 'champ-scroll' }, grid), h('div', { class: 'lock-bar' }, lockBtn));

    const myChampImg = h('img', { alt: '' });
    const myChampName = h('div', { class: 'queue-name' });
    const spellBtns = [0, 1].map((i) => h('button', { class: 'spell-btn', 'aria-label': `Beschwörerzauber ${i + 1}`, onclick: () => openSpells(i) }, h('img', { alt: '' })));
    const runes = h('select', { class: 'search', 'aria-label': 'Runenseite', onchange: () => act('runePage', { id: Number(runes.value) }) });
    const final = h('div', { class: 'card final' },
      h('div', { class: 'final-champ' }, myChampImg, myChampName),
      h('div', { class: 'row' }, ...spellBtns, h('div', { style: 'flex:1' }, runes)));

    root.append(h('div', { class: 'cs' },
      h('div', { class: 'cs-head' }, stage, time),
      h('div', { class: 'bar' }, barFill),
      h('div', { class: 'bans' }, bansMine, bansTheirs),
      h('div', { class: 'teams' }, ally, enemy),
      turn, picker, final));

    const buttons = new Map();
    function buildGrid() {
      if (buttons.size || !champions.length) return;
      const rec = recent.get();
      const order = [...champions].sort((a, b) => {
        const ra = rec.indexOf(a.id), rb = rec.indexOf(b.id);
        if (ra !== rb) return (ra < 0 ? 99 : ra) - (rb < 0 ? 99 : rb);
        return 0;
      });
      for (const c of order) {
        const b = h('button', { class: 'champ', 'data-id': c.id, onclick: () => choose(c.id) },
          h('div', { class: 'pic' }, h('img', { src: champIcon(c.id), alt: '', loading: 'lazy' })),
          h('div', { class: 'n' }, c.name));
        b._key = norm(c.name) + ' ' + norm(c.alias || '');
        buttons.set(c.id, b);
        grid.append(b);
      }
    }

    function filter() {
      const q = norm(search.value);
      for (const b of buttons.values()) b.hidden = !!q && !b._key.includes(q);
    }
    search.addEventListener('input', filter);
    search.addEventListener('keydown', (e) => {
      if (e.key !== 'Enter') return;
      const vis = [...buttons.values()].filter((b) => !b.hidden && !b.classList.contains('off'));
      if (vis.length) { choose(Number(vis[0].dataset.id)); search.blur(); }
    });

    function choose(id) {
      selected = id;
      paintSelection();
      act('hover', { championId: id });
    }

    function paintSelection() {
      const cs = state.champSelect;
      const cur = selected || (cs && cs.action && cs.action.championId) || 0;
      for (const [id, b] of buttons) b.classList.toggle('sel', id === cur);
      const mode = cs && cs.mode;
      lockBtn.disabled = !cur;
      lockBtn.textContent = mode === 'ban' ? (cur ? `${champById.get(cur)?.name || ''} bannen` : 'Bannen') : (cur ? `${champById.get(cur)?.name || ''} festlegen` : 'Festlegen');
    }

    lockBtn.addEventListener('click', async () => {
      const cs = state.champSelect;
      const cur = selected || (cs && cs.action && cs.action.championId);
      if (!cur) return;
      const ok = await act('lock', { championId: cur }, lockBtn);
      if (ok && cs.mode === 'pick') recent.add(cur);
    });

    function openSpells(slot) {
      const cs = state.champSelect;
      if (!cs) return;
      const cur = cs.spells || [];
      const list = spells.filter((s) => !cs.gameMode || !s.gameModes.length || s.gameModes.includes(cs.gameMode));
      $('sheetTitle').textContent = `Zauber ${slot + 1}`;
      $('sheetGrid').replaceChildren(...list.map((sp) => h('button', {
        class: sp.id === cur[slot] ? 'on' : '',
        onclick: () => {
          const next = [...cur];
          const other = 1 - slot;
          if (next[other] === sp.id) next[other] = next[slot];
          next[slot] = sp.id;
          act('spells', { spell1Id: next[0], spell2Id: next[1] });
          $('sheet').hidden = true;
        },
      }, h('img', { src: asset(sp.iconPath), alt: '' }), sp.name)));
      $('sheet').hidden = false;
    }

    function slot(p, isAlly) {
      const champ = p.championId || p.intent;
      const face = h('div', { class: 'face' + (!p.championId && p.intent ? ' intent' : '') }, champ ? h('img', { src: champIcon(champ), alt: '' }) : null);
      const nm = champ ? champById.get(champ)?.name || '' : '';
      return h('div', { class: 'slot' + (p.acting ? ' acting' : '') + (p.isMe ? ' me' : '') }, face,
        h('div', { class: 'meta' },
          h('div', { class: 'pos' }, p.isMe ? 'DU' : POS[p.position] || (isAlly ? '' : '')),
          h('div', { class: 'nm' }, nm || (isAlly ? p.name : '') || '—')));
    }

    function banIcons(ids) {
      return Array.from({ length: Math.max(ids.length, 0) }, (_, i) => h('div', { class: 'ban' }, h('img', { src: champIcon(ids[i]), alt: '' })));
    }

    return {
      update(s) {
        const cs = s.champSelect;
        if (!cs) return;
        buildGrid();

        if (!cs.action || cs.action.id !== actionId) { actionId = cs.action && cs.action.id; selected = 0; }
        if (cs.mode !== lastMode) {
          if ((cs.mode === 'ban' || cs.mode === 'pick') && lastMode !== null) alarm();
          lastMode = cs.mode;
        }

        stage.textContent = cs.stage === 'PLANNING' ? 'Planung' : cs.stage === 'FINALIZATION' ? 'Vorbereitung' : cs.currentType === 'ban' ? 'Bannphase' : 'Wahlphase';
        bansMine.replaceChildren(...banIcons(cs.bans.mine));
        bansTheirs.replaceChildren(...banIcons(cs.bans.theirs));
        ally.replaceChildren(...cs.myTeam.map((p) => slot(p, true)));
        enemy.replaceChildren(...cs.theirTeam.map((p) => slot(p, false)));

        const texts = {
          ban: 'Du bist dran · Bannen',
          pick: 'Du bist dran · Wählen',
          intent: 'Planung · Wunsch-Champion zeigen',
          wait: cs.nextType ? `Warte · gleich ${cs.nextType === 'ban' ? 'bannen' : 'wählen'}` : 'Warte auf die anderen',
          done: cs.stage === 'FINALIZATION' ? 'Bereit fürs Spiel' : 'Gewählt · warte auf die anderen',
        };
        turn.textContent = texts[cs.mode];
        turn.className = 'turn ' + (cs.mode === 'ban' || cs.mode === 'pick' ? 'hot ' + cs.mode : 'cold');

        const picking = cs.mode === 'ban' || cs.mode === 'pick' || cs.mode === 'intent';
        picker.hidden = !picking;
        picker.style.display = picking ? '' : 'none';
        lockBtn.parentElement.hidden = cs.mode === 'intent';
        lockBtn.className = 'btn ' + (cs.mode === 'ban' ? 'ban-btn' : 'primary');

        const allowed = new Set(cs.mode === 'ban' ? cs.bannable : cs.pickable);
        const blocked = new Set(cs.unavailable);
        for (const [id, b] of buttons) b.classList.toggle('off', (allowed.size > 0 && !allowed.has(id)) || blocked.has(id));
        paintSelection();

        final.hidden = picking;
        final.style.display = picking ? 'none' : '';
        const me = cs.myTeam.find((p) => p.isMe) || {};
        const mine = me.championId || me.intent;
        myChampImg.src = mine ? champIcon(mine) : '/icon.svg';
        myChampName.textContent = mine ? champById.get(mine)?.name || '' : 'Noch kein Champion';
        cs.spells.forEach((id, i) => {
          const sp = spells.find((x) => x.id === id);
          spellBtns[i].firstChild.src = sp ? asset(sp.iconPath) : '';
        });
        const pagesKey = JSON.stringify(cs.runePages);
        if (runes._key !== pagesKey) {
          runes._key = pagesKey;
          runes.replaceChildren(...cs.runePages.map((p) => h('option', { value: p.id, selected: p.current }, p.name)));
          runes.hidden = !cs.runePages.length;
        }
        this.tick();
      },
      tick() {
        const cs = state.champSelect;
        if (!cs) return;
        const left = Math.max(0, cs.timeLeftMs - (serverNow() - cs.timerAt)) / 1000;
        time.textContent = Math.ceil(left);
        time.classList.toggle('low', left <= 8 && (cs.mode === 'ban' || cs.mode === 'pick'));
        barFill.style.width = cs.totalMs ? `${Math.min(100, (left * 1000 / cs.totalMs) * 100)}%` : '0%';
      },
    };
  },

  game(root) {
    const img = h('img', { alt: '', style: 'width:96px;height:96px;border-radius:20px;border:2px solid var(--accent);box-shadow:0 0 24px var(--glow)' });
    root.append(h('div', { class: 'center' }, img, h('div', { class: 'label' }, 'Im Spiel'), h('div', { class: 'sub' }, 'Viel Erfolg auf dem Rift.')));
    const last = lastPicked;
    img.src = last ? champIcon(last) : '/icon.svg';
    return {};
  },

  end(root) {
    const again = h('button', { class: 'btn primary', onclick: (e) => act('playAgain', {}, e.currentTarget) }, 'Nochmal spielen');
    root.append(h('div', { class: 'center' }, logoSvg(), h('div', { class: 'label' }, 'Spiel vorbei'), again));
    return {};
  },

  other(root) {
    const t = h('div', { class: 'queue-name' });
    root.append(h('div', { class: 'center' }, logoSvg(), t));
    return { update(s) { t.textContent = s.phase; } };
  },
};

function viewKeyFor(s) {
  if (!s.connected) return 'offline';
  switch (s.phase) {
    case 'None': case 'Lobby': return 'lobby';
    case 'Matchmaking': return 'search';
    case 'ReadyCheck': return 'ready';
    case 'ChampSelect': return 'cs';
    case 'GameStart': case 'InProgress': case 'Reconnect': return 'game';
    case 'WaitingForStats': case 'PreEndOfGame': case 'EndOfGame': return 'end';
    default: return 'other';
  }
}

// ---------------- Zustand vom PC ----------------

let view = null;
let currentKey = '';
let lastPicked = 0;

function render() {
  const key = viewKeyFor(state);
  if (key !== currentKey) {
    currentKey = key;
    const root = $('view');
    root.replaceChildren();
    root.scrollTop = 0;
    document.body.classList.toggle('in-cs', key === 'cs');
    $('sheet').hidden = true;
    view = views[key](root);
  }
  if (view.update) view.update(state);
}

function onState(s) {
  const prev = state;
  state = s;
  offset = s.serverNow - Date.now();

  $('dot').className = 'dot ' + (s.connected ? 'on' : sseOk ? 'wait' : '');
  $('summoner').textContent = s.summoner && s.summoner.name ? `${s.summoner.name}${s.summoner.tag ? ' #' + s.summoner.tag : ''}` : s.connected ? '' : 'nicht verbunden';
  $('autoAccept').checked = !!s.autoAccept;
  $('demoTag').hidden = !s.demo;

  if (s.connected) loadChampions();
  if (s.phase === 'ReadyCheck' && prev.phase !== 'ReadyCheck') {
    alarm();
    document.title = '⚔ Match gefunden!';
  } else if (s.phase !== 'ReadyCheck') {
    document.title = 'Rift Remote';
  }
  const me = s.champSelect && s.champSelect.myTeam.find((p) => p.isMe);
  if (me && me.championId) lastPicked = me.championId;

  render();
}

function connect() {
  const es = new EventSource('/events');
  es.onopen = () => { sseOk = true; unauthorized = false; };
  es.onmessage = (e) => { sseOk = true; onState(JSON.parse(e.data)); };
  es.onerror = async () => {
    sseOk = false;
    if (es.readyState === EventSource.CLOSED) {
      // Der Server hat die Verbindung abgelehnt (z. B. nicht gekoppelt) – prüfen und später neu versuchen.
      try { unauthorized = (await fetch('/api/champions')).status === 401; } catch {}
      setTimeout(connect, 4000);
    }
    onState({ ...state, connected: false, serverNow: Date.now() });
  };
}

$('autoAccept').addEventListener('change', (e) => act('autoAccept', { value: e.target.checked }));
$('sheetClose').addEventListener('click', () => ($('sheet').hidden = true));
$('sheet').addEventListener('click', (e) => { if (e.target === $('sheet')) $('sheet').hidden = true; });
setInterval(() => view && view.tick && view.tick(), 200);

render();
connect();
