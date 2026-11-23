// Verbindung zum League Client (LCU API) auf diesem PC.
// Der Client öffnet einen HTTPS-Server auf 127.0.0.1 mit zufälligem Port und Passwort.
// Beides steht in der "lockfile" im Installationsordner bzw. in der Kommandozeile von LeagueClientUx.

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execFile } = require('child_process');

// Der Client nutzt ein selbst signiertes Riot-Zertifikat. Die Prüfung wird nur für
// diese eine Verbindung zu 127.0.0.1 abgeschaltet, nirgendwo sonst.
const localAgent = new https.Agent({ rejectUnauthorized: false, keepAlive: true });

const COMMON_DIRS = [
  'C:\\Riot Games\\League of Legends',
  'D:\\Riot Games\\League of Legends',
  'E:\\Riot Games\\League of Legends',
  'C:\\Program Files\\Riot Games\\League of Legends',
  'C:\\Program Files (x86)\\Riot Games\\League of Legends',
  '/Applications/League of Legends.app/Contents/LoL',
];

function readLockfile(dir) {
  try {
    const raw = fs.readFileSync(path.join(dir, 'lockfile'), 'utf8').trim();
    const [, , port, password, protocol] = raw.split(':');
    if (port && password) return { port: Number(port), password, protocol: protocol || 'https' };
  } catch {}
  return null;
}

function installDirFromRiotMetadata() {
  const file = 'C:\\ProgramData\\Riot Games\\Metadata\\league_of_legends.live\\league_of_legends.live.product_settings.yaml';
  try {
    const m = fs.readFileSync(file, 'utf8').match(/product_install_full_path:\s*"?([^"\r\n]+)"?/);
    if (m) return m[1].trim();
  } catch {}
  return null;
}

function run(cmd, args) {
  return new Promise((resolve) => {
    execFile(cmd, args, { windowsHide: true, timeout: 5000, maxBuffer: 1024 * 1024 }, (err, stdout) => {
      resolve(err ? '' : String(stdout));
    });
  });
}

async function fromProcessArgs() {
  let out = '';
  if (process.platform === 'win32') {
    out = await run('powershell.exe', [
      '-NoProfile', '-Command',
      "(Get-CimInstance Win32_Process -Filter \"name='LeagueClientUx.exe'\").CommandLine",
    ]);
  } else {
    out = await run('ps', ['-A', '-o', 'args']);
    out = out.split('\n').filter((l) => l.includes('LeagueClientUx')).join('\n');
  }
  const port = out.match(/--app-port=["']?(\d+)/);
  const token = out.match(/--remoting-auth-token=["']?([\w-]+)/);
  if (port && token) return { port: Number(port[1]), password: token[1], protocol: 'https' };
  return null;
}

async function discover(configuredDir) {
  const dirs = [configuredDir, process.env.LEAGUE_PATH, installDirFromRiotMetadata(), ...COMMON_DIRS].filter(Boolean);
  for (const dir of dirs) {
    const creds = readLockfile(dir);
    if (creds) return creds;
  }
  return fromProcessArgs();
}

class LCU {
  constructor(configuredDir) {
    this.configuredDir = configuredDir;
    this.creds = null;
    this.lastDiscover = 0;
  }

  get connected() {
    return !!this.creds;
  }

  async ensure() {
    if (this.creds) return true;
    if (Date.now() - this.lastDiscover < 3000) return false;
    this.lastDiscover = Date.now();
    this.creds = await discover(this.configuredDir);
    return !!this.creds;
  }

  // Liefert { status, body, contentType }. body ist bei JSON bereits geparst.
  request(method, urlPath, data, { raw = false } = {}) {
    if (!this.creds) return Promise.reject(new Error('not connected'));
    const { port, password } = this.creds;
    const payload = data === undefined ? null : Buffer.from(JSON.stringify(data));
    return new Promise((resolve, reject) => {
      const req = https.request(
        {
          host: '127.0.0.1',
          port,
          path: urlPath,
          method,
          agent: localAgent,
          timeout: 4000,
          headers: {
            Authorization: 'Basic ' + Buffer.from('riot:' + password).toString('base64'),
            Accept: raw ? '*/*' : 'application/json',
            ...(payload ? { 'Content-Type': 'application/json', 'Content-Length': payload.length } : {}),
          },
        },
        (res) => {
          const chunks = [];
          res.on('data', (c) => chunks.push(c));
          res.on('end', () => {
            const buf = Buffer.concat(chunks);
            const contentType = res.headers['content-type'] || '';
            let body = buf;
            if (!raw) {
              try { body = buf.length ? JSON.parse(buf.toString('utf8')) : null; } catch { body = buf.toString('utf8'); }
            }
            resolve({ status: res.statusCode, body, contentType });
          });
        }
      );
      req.on('timeout', () => req.destroy(new Error('timeout')));
      req.on('error', (err) => {
        // Client wurde geschlossen oder neu gestartet: beim nächsten Durchlauf neu suchen.
        if (['ECONNREFUSED', 'ECONNRESET'].includes(err.code)) this.creds = null;
        reject(err);
      });
      if (payload) req.write(payload);
      req.end();
    });
  }

  async get(p) {
    const r = await this.request('GET', p);
    return r.status >= 200 && r.status < 300 ? r.body : null;
  }
}

module.exports = { LCU };
