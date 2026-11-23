// Demo-Modus: ein nachgebauter League Client, damit man die Handy-Oberfläche ohne
// laufendes Spiel ausprobieren kann (`npm run demo`). Spricht dieselben Pfade wie die echte LCU API.

const CHAMPS = [
  [1, 'Annie'], [11, 'Master Yi'], [17, 'Teemo'], [22, 'Ashe'], [25, 'Morgana'], [32, 'Amumu'],
  [51, 'Caitlyn'], [54, 'Malphite'], [55, 'Katarina'], [61, 'Orianna'], [64, 'Lee Sin'], [81, 'Ezreal'],
  [86, 'Garen'], [89, 'Leona'], [92, 'Riven'], [99, 'Lux'], [103, 'Ahri'], [117, 'Lulu'], [122, 'Darius'],
  [145, "Kai'Sa"], [157, 'Yasuo'], [222, 'Jinx'], [234, 'Viego'], [238, 'Zed'], [254, 'Vi'],
  [267, 'Nami'], [412, 'Thresh'], [517, 'Sylas'], [777, 'Yone'], [875, 'Sett'],
].map(([id, name]) => ({ id, name, alias: name.replace(/[^A-Za-z]/g, '') }));

const SPELLS = [
  [4, 'Blitz'], [14, 'Entzünden'], [12, 'Teleportation'], [7, 'Heilen'], [21, 'Barriere'],
  [3, 'Erschöpfung'], [11, 'Zerschmettern'], [6, 'Geist'], [1, 'Läuterung'],
].map(([id, name]) => ({ id, name, iconPath: `/lol-game-data/assets/demo/spell/${id}.svg`, gameModes: ['CLASSIC'] }));

const QUEUES = { 420: 'Ranked Solo/Duo', 440: 'Ranked Flex', 400: 'Normal Draft', 450: 'ARAM', 480: 'Swiftplay' };
const POSITIONS = ['top', 'jungle', 'middle', 'bottom', 'utility'];
const ME = 2;

const sec = (s) => s * 1000;
const rand = (a, b) => a + Math.random() * (b - a);

function icon(label, hue) {
  const text = label.slice(0, 2);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue},70%,45%)"/><stop offset="1" stop-color="hsl(${(hue + 50) % 360},70%,22%)"/></linearGradient></defs>
<rect width="96" height="96" fill="url(#g)"/><text x="48" y="60" font-family="Arial,sans-serif" font-size="34" font-weight="700" fill="#fff" text-anchor="middle">${text}</text></svg>`;
}

class DemoLCU {
  constructor() {
    this.connected = true;
    this.phase = 'Lobby';
    this.queueId = 420;
    this.t0 = Date.now();
    this.ready = null;
    this.cs = null;
    this.spells = [4, 14];
    this.pages = [
      { id: 1, name: 'Mid – Elektrisieren', isEditable: true, isValid: true, current: true },
      { id: 2, name: 'ADC – Eroberer', isEditable: true, isValid: true, current: false },
      { id: 3, name: 'Support – Wächter', isEditable: true, isValid: true, current: false },
    ];
  }

  async ensure() { return true; }

  setPhase(p) { this.phase = p; this.t0 = Date.now(); }

  // ---------------- Champ Select ----------------

  startChampSelect() {
    let id = 1;
    const mk = (cellId, type) => ({ id: id++, actorCellId: cellId, championId: 0, completed: false, isInProgress: false, type, isAllyAction: cellId < 5 });
    const groups = [];
    groups.push([0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((c) => mk(c, 'ban')));
    for (const g of [[0], [5, 6], [1, 2], [7, 8], [3, 4], [9]]) groups.push(g.map((c) => mk(c, 'pick')));
    const team = (offset) => POSITIONS.map((pos, i) => ({
      cellId: offset + i, championId: 0, championPickIntent: 0, assignedPosition: offset ? '' : pos,
      spell1Id: 4, spell2Id: [12, 11, 14, 7, 3][i], gameName: offset ? '' : ['Nachtfalke', 'Krähe', 'Du', 'Schatten', 'Mondlicht'][i],
    }));
    this.cs = { groups, groupIndex: -1, stage: 'PLANNING', stageStart: Date.now(), stageLen: sec(15), myTeam: team(0), theirTeam: team(5), bots: [] };
    // Bots zeigen in der Planungsphase schon, was sie spielen wollen.
    for (const p of this.cs.myTeam) if (p.cellId !== ME) p.championPickIntent = this.randomFree();
    this.setPhase('ChampSelect');
  }

  taken() {
    const ids = new Set();
    for (const g of this.cs.groups) for (const a of g) if (a.completed && a.championId) ids.add(a.championId);
    return ids;
  }

  randomFree() {
    const taken = this.taken();
    const free = CHAMPS.filter((c) => !taken.has(c.id));
    return free[Math.floor(Math.random() * free.length)].id;
  }

  playerOf(cellId) {
    return [...this.cs.myTeam, ...this.cs.theirTeam].find((p) => p.cellId === cellId);
  }

  completeAction(a, championId) {
    a.championId = championId || a.championId || this.randomFree();
    a.completed = true;
    a.isInProgress = false;
    if (a.type === 'pick') {
      const p = this.playerOf(a.actorCellId);
      p.championId = a.championId;
      p.championPickIntent = 0;
    }
  }

  openGroup(i) {
    const cs = this.cs;
    cs.groupIndex = i;
    cs.stage = 'BAN_PICK';
    cs.stageStart = Date.now();
    cs.stageLen = cs.groups[i][0].type === 'ban' ? sec(20) : sec(18);
    cs.bots = [];
    for (const a of cs.groups[i]) {
      a.isInProgress = true;
      if (a.actorCellId !== ME) cs.bots.push({ a, at: Date.now() + rand(2500, 9000) });
    }
  }

  tickChampSelect() {
    const cs = this.cs;
    const now = Date.now();
    for (const b of cs.bots) {
      if (!b.a.completed && now >= b.at) {
        const p = this.playerOf(b.a.actorCellId);
        const wanted = b.a.type === 'pick' && p.championPickIntent && !this.taken().has(p.championPickIntent) ? p.championPickIntent : 0;
        this.completeAction(b.a, wanted);
      }
    }
    const elapsed = now - cs.stageStart;
    if (cs.stage === 'PLANNING' && elapsed >= cs.stageLen) return this.openGroup(0);
    if (cs.stage === 'BAN_PICK') {
      const group = cs.groups[cs.groupIndex];
      const timeUp = elapsed >= cs.stageLen;
      if (timeUp) for (const a of group) if (!a.completed) this.completeAction(a);
      if (group.every((a) => a.completed)) {
        if (cs.groupIndex + 1 < cs.groups.length) return this.openGroup(cs.groupIndex + 1);
        cs.stage = 'FINALIZATION';
        cs.stageStart = now;
        cs.stageLen = sec(25);
      }
    }
    if (cs.stage === 'FINALIZATION' && elapsed >= cs.stageLen) this.setPhase('InProgress');
  }

  session() {
    const cs = this.cs;
    const actions = cs.groups.map((g) => g.map((a) => ({ ...a })));
    const bans = { myTeamBans: [], theirTeamBans: [] };
    if (cs.groupIndex > 0 || cs.stage === 'FINALIZATION') {
      for (const a of cs.groups[0]) (a.actorCellId < 5 ? bans.myTeamBans : bans.theirTeamBans).push(a.championId);
    }
    const myTeam = cs.myTeam.map((p) => {
      // Während man dran ist, sehen Verbündete den Hover als championId.
      const act = cs.groups.flat().find((a) => a.actorCellId === p.cellId && a.type === 'pick');
      const hovering = act && act.isInProgress && !act.completed ? act.championId : 0;
      return { ...p, championId: p.championId || hovering, spell1Id: p.cellId === ME ? this.spells[0] : p.spell1Id, spell2Id: p.cellId === ME ? this.spells[1] : p.spell2Id };
    });
    const theirTeam = cs.theirTeam.map((p) => ({ ...p, assignedPosition: '' }));
    return {
      localPlayerCellId: ME,
      actions,
      myTeam,
      theirTeam,
      bans,
      timer: {
        phase: cs.stage,
        adjustedTimeLeftInPhase: Math.max(0, cs.stageLen - (Date.now() - cs.stageStart)),
        totalTimeInPhase: cs.stageLen,
        internalNowInEpochMs: Date.now(),
      },
    };
  }

  myAction() {
    return this.cs.groups.flat().find((a) => a.actorCellId === ME && !a.completed && (a.isInProgress || this.cs.stage === 'PLANNING'));
  }

  // ---------------- Zeitabhängiger Ablauf ----------------

  tick() {
    const elapsed = Date.now() - this.t0;
    if (this.phase === 'Matchmaking' && elapsed > sec(7)) {
      this.ready = { response: 'None' };
      this.setPhase('ReadyCheck');
    } else if (this.phase === 'ReadyCheck') {
      if (this.ready.response === 'Accepted' && elapsed > sec(2.5)) this.startChampSelect();
      else if (elapsed > sec(12)) this.setPhase(this.ready.response === 'Declined' ? 'Lobby' : 'Matchmaking');
    } else if (this.phase === 'ChampSelect') {
      this.tickChampSelect();
    } else if (this.phase === 'InProgress' && elapsed > sec(20)) {
      this.setPhase('EndOfGame');
    }
  }

  // ---------------- Router ----------------

  async get(p) {
    const r = await this.request('GET', p);
    return r.status < 300 ? r.body : null;
  }

  async request(method, p, data, { raw = false } = {}) {
    this.tick();
    const ok = (body) => ({ status: 200, body, contentType: 'application/json' });
    const none = { status: 404, body: { message: 'not found' }, contentType: 'application/json' };
    const key = `${method} ${p.split('?')[0]}`;

    if (p.startsWith('/lol-game-data/assets/') && raw) {
      let svg;
      const champ = p.match(/champion-icons\/(\d+)/);
      const spell = p.match(/spell\/(\d+)/);
      if (champ) {
        const c = CHAMPS.find((x) => x.id === Number(champ[1]));
        svg = icon(c ? c.name : '?', (Number(champ[1]) * 47) % 360);
      } else if (spell) {
        const s = SPELLS.find((x) => x.id === Number(spell[1]));
        svg = icon(s ? s.name : '?', (Number(spell[1]) * 83) % 360);
      } else {
        svg = icon('D', 270);
      }
      return { status: 200, body: Buffer.from(svg), contentType: 'image/svg+xml' };
    }

    switch (key) {
      case 'GET /lol-gameflow/v1/gameflow-phase': return ok(this.phase);
      case 'GET /lol-summoner/v1/current-summoner': return ok({ gameName: 'Demo', tagLine: 'EUW', profileIconId: 29 });
      case 'GET /lol-game-data/assets/v1/champion-summary.json': return ok([{ id: -1, name: 'Keiner', alias: 'None' }, ...CHAMPS]);
      case 'GET /lol-game-data/assets/v1/summoner-spells.json': return ok(SPELLS);
      case 'GET /lol-lobby/v2/lobby':
        return ['Lobby', 'Matchmaking', 'ReadyCheck'].includes(this.phase) ? ok({ gameConfig: { queueId: this.queueId } }) : none;
      case 'GET /lol-game-queues/v1/queues':
        return ok(Object.entries(QUEUES).map(([id, description]) => ({ id: Number(id), description, shortName: description, queueAvailability: 'Available' })));
      case 'POST /lol-lobby/v2/lobby':
        this.queueId = data.queueId;
        this.setPhase('Lobby');
        return ok({});
      case 'POST /lol-lobby/v2/lobby/matchmaking/search':
        if (this.phase !== 'Lobby') return none;
        this.setPhase('Matchmaking');
        return ok({});
      case 'DELETE /lol-lobby/v2/lobby/matchmaking/search':
        if (this.phase === 'Matchmaking') this.setPhase('Lobby');
        return ok({});
      case 'GET /lol-matchmaking/v1/search':
        if (this.phase !== 'Matchmaking') return none;
        return ok({ searchState: 'Searching', timeInQueue: (Date.now() - this.t0) / 1000, estimatedQueueTime: 95 });
      case 'GET /lol-matchmaking/v1/ready-check':
        if (this.phase !== 'ReadyCheck') return none;
        return ok({ state: 'InProgress', playerResponse: this.ready.response, timer: (Date.now() - this.t0) / 1000 });
      case 'POST /lol-matchmaking/v1/ready-check/accept':
        if (this.phase === 'ReadyCheck') { this.ready.response = 'Accepted'; this.t0 = Date.now(); }
        return ok({});
      case 'POST /lol-matchmaking/v1/ready-check/decline':
        if (this.phase === 'ReadyCheck') this.ready.response = 'Declined';
        return ok({});
      case 'GET /lol-champ-select/v1/session':
        return this.phase === 'ChampSelect' ? ok(this.session()) : none;
      case 'GET /lol-champ-select/v1/pickable-champion-ids':
      case 'GET /lol-champ-select/v1/bannable-champion-ids':
        return ok(CHAMPS.map((c) => c.id));
      case 'PATCH /lol-champ-select/v1/session/my-selection':
        this.spells = [data.spell1Id ?? this.spells[0], data.spell2Id ?? this.spells[1]];
        return ok({});
      case 'POST /lol-lobby/v2/play-again':
        this.setPhase('Lobby');
        return ok({});
      case 'GET /lol-perks/v1/pages': return ok(this.pages);
      case 'PUT /lol-perks/v1/currentpage':
        for (const pg of this.pages) pg.current = pg.id === data;
        return ok({});
    }

    const act = p.match(/^\/lol-champ-select\/v1\/session\/actions\/(\d+)(\/complete)?$/);
    if (act && this.phase === 'ChampSelect') {
      const a = this.cs.groups.flat().find((x) => x.id === Number(act[1]));
      if (!a || a.actorCellId !== ME || a.completed) return { status: 400, body: { message: 'invalid action' } };
      if (method === 'PATCH') {
        if (data.championId !== undefined) {
          a.championId = data.championId;
          if (a.type === 'pick') this.cs.myTeam[ME].championPickIntent = this.cs.stage === 'PLANNING' ? data.championId : 0;
        }
        if (data.completed && this.cs.stage !== 'PLANNING') this.completeAction(a);
        return ok({});
      }
      if (method === 'POST' && act[2]) {
        if (this.cs.stage === 'PLANNING' || !a.championId) return { status: 400, body: { message: 'not your turn' } };
        this.completeAction(a);
        this.tickChampSelect();
        return ok({});
      }
    }
    return none;
  }
}

module.exports = { DemoLCU };
