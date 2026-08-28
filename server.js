const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const Database = require('better-sqlite3');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');
const os = require('os');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  maxHttpBufferSize: 100e6,
  pingInterval: 25000,
  pingTimeout: 60000,
  connectTimeout: 20000,
  cors: { origin: '*' }
});

const UPLOADS = path.join(__dirname, 'uploads');
if (!fs.existsSync(UPLOADS)) fs.mkdirSync(UPLOADS);

// --- Logging system ---
const LOGS_DIR = path.join(__dirname, 'logs');
if (!fs.existsSync(LOGS_DIR)) fs.mkdirSync(LOGS_DIR);
const LOG_MAX_SIZE = 5 * 1024 * 1024;

function logTimestamp() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return pad(d.getDate()) + '-' + pad(d.getMonth() + 1) + '-' + d.getFullYear()
    + '-' + pad(d.getHours()) + '-' + pad(d.getMinutes()) + '-' + pad(d.getSeconds());
}

let _logFile = path.join(LOGS_DIR, logTimestamp() + '.txt');
let _logStream = fs.createWriteStream(_logFile, { flags: 'a' });

function rotateLogIfNeeded() {
  try {
    const stat = fs.statSync(_logFile);
    if (stat.size >= LOG_MAX_SIZE) {
      _logStream.end();
      _logFile = path.join(LOGS_DIR, logTimestamp() + '.txt');
      _logStream = fs.createWriteStream(_logFile, { flags: 'a' });
    }
  } catch {}
}

function log(msg) {
  const ts = new Date().toISOString();
  const line = '[' + ts + '] ' + msg;
  console.log(line);
  rotateLogIfNeeded();
  _logStream.write(line + '\n');
}

// --- Key management ---
const KEY_FILE = path.join(__dirname, 'key.json');

function loadKeys() {
  try {
    if (fs.existsSync(KEY_FILE)) {
      const data = JSON.parse(fs.readFileSync(KEY_FILE, 'utf8'));
      if (data && data.keys) return data;
    }
  } catch {}
  return { keys: {} };
}

function saveKeys(data) {
  try {
    const tmp = KEY_FILE + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
    fs.renameSync(tmp, KEY_FILE);
  } catch (e) {
    try { fs.writeFileSync(KEY_FILE, JSON.stringify(data, null, 2)); } catch {}
  }
}

function generateKey() {
  return crypto.randomBytes(16).toString('hex');
}

function generateRecoveryKey() {
  return crypto.randomBytes(18).toString('base64').replace(/[^a-zA-Z0-9]/g, '').slice(0, 24);
}

function encryptRecovery(recoveryKey, loginKey) {
  const iv = crypto.randomBytes(12);
  const key = crypto.createHash('sha256').update(recoveryKey).digest();
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update(loginKey, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, encrypted]).toString('base64');
}

function decryptRecovery(recoveryKey, encryptedPayload) {
  try {
    const data = Buffer.from(encryptedPayload, 'base64');
    if (data.length < 28) return null;
    const iv = data.subarray(0, 12);
    const tag = data.subarray(12, 28);
    const encrypted = data.subarray(28);
    const key = crypto.createHash('sha256').update(recoveryKey).digest();
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
  } catch {
    return null;
  }
}

function hasMac(entry, mac) {
  if (!entry || !mac) return false;
  if (Array.isArray(entry.macs) && entry.macs.includes(mac)) return true;
  if (entry.mac === mac) return true;
  return false;
}

function addMac(entry, mac) {
  if (!entry || !mac) return;
  if (!Array.isArray(entry.macs)) {
    entry.macs = entry.mac ? [entry.mac] : [];
  }
  delete entry.mac;
  delete entry.ip;
  if (!entry.macs.includes(mac)) {
    entry.macs.push(mac);
  }
}

function removeMac(entry, mac) {
  if (!entry || !mac) return;
  if (Array.isArray(entry.macs)) {
    entry.macs = entry.macs.filter(m => m !== mac);
  }
  if (entry.mac === mac) entry.mac = null;
}

app.use(express.json({ limit: '100mb' }));
app.use(express.urlencoded({ limit: '100mb', extended: true }));

const MIME_MAP = {
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
  '.mov': 'video/quicktime',
  '.ogg': 'audio/ogg',
  '.mp3': 'audio/mpeg',
  '.wav': 'audio/wav',
  '.m4a': 'audio/mp4',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon'
};

// HTTP Range-supported streaming for media files (prevents playback errors midway)
app.get('/uploads/:filename', (req, res) => {
  const rawName = path.basename(req.params.filename || '');
  if (!rawName || rawName.includes('..')) return res.status(400).send('Invalid filename');
  const filePath = path.join(UPLOADS, rawName);

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) return res.status(404).send('Not found');

    const ext = path.extname(rawName).toLowerCase();
    const mimeType = MIME_MAP[ext] || 'application/octet-stream';
    const fileSize = stats.size;
    const range = req.headers.range;

    res.setHeader('Accept-Ranges', 'bytes');
    res.setHeader('Cache-Control', 'public, max-age=86400');

    if (range) {
      const parts = range.replace(/bytes=/, '').split('-');
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

      if (isNaN(start) || isNaN(end) || start >= fileSize || end >= fileSize || start > end) {
        res.setHeader('Content-Range', `bytes */${fileSize}`);
        return res.status(416).send('Requested range not satisfiable');
      }

      const chunkSize = (end - start) + 1;
      const stream = fs.createReadStream(filePath, { start, end });
      res.writeHead(206, {
        'Content-Range': `bytes ${start}-${end}/${fileSize}`,
        'Content-Length': chunkSize,
        'Content-Type': mimeType
      });
      stream.on('error', () => { if (!res.headersSent) res.status(500).end(); });
      req.on('close', () => { stream.destroy(); });
      stream.pipe(res);
    } else {
      res.writeHead(200, {
        'Content-Length': fileSize,
        'Content-Type': mimeType
      });
      const stream = fs.createReadStream(filePath);
      stream.on('error', () => { if (!res.headersSent) res.status(500).end(); });
      req.on('close', () => { stream.destroy(); });
      stream.pipe(res);
    }
  });
});

// Block access to sensitive files
app.use((req, res, next) => {
  const blocked = /\.(js|db|json|log|tmp)$/i;
  const blockedExact = /^\/(server\.js|chat\.db|hash\.db|key\.json|package\.json|package-lock\.json|\.env)/i;
  if (blocked.test(req.path) || blockedExact.test(req.path)) {
    if (req.path === '/patch_notes.json') return next();
    return res.status(404).end();
  }
  next();
});
app.use(express.static(__dirname));
app.use('/uploads', express.static(UPLOADS));

app.post('/api/upload', express.raw({ type: '*/*', limit: '100mb' }), (req, res) => {
  try {
    const qType = req.query.type || 'image';
    const qHash = req.query.hash || null;
    let rawBody = req.body;
    let clientHash = qHash && qHash !== 'null' && qHash !== 'undefined' ? qHash : null;

    // Direct binary upload handling (instant stream)
    if (Buffer.isBuffer(rawBody) && rawBody.length > 0) {
      const mime = (req.headers['content-type'] || '').toLowerCase();
      let ext = 'bin';
      if (mime.includes('video') || qType === 'video') {
        ext = mime.includes('webm') ? 'webm' : (mime.includes('mov') ? 'mov' : 'mp4');
      } else if (mime.includes('audio') || qType === 'audio') {
        ext = mime.includes('ogg') ? 'ogg' : (mime.includes('wav') ? 'wav' : 'mp3');
      } else {
        ext = mime.includes('png') ? 'png' : (mime.includes('gif') ? 'gif' : (mime.includes('webp') ? 'webp' : 'jpg'));
      }
      if (!clientHash) clientHash = computeBufferQuickHash(rawBody);
      const fn = saveMediaWithQuickHash(rawBody, ext, clientHash);
      if (!fn) return res.status(500).json({ error: 'Failed to save file' });
      return res.json({ success: true, filename: fn, url: '/uploads/' + fn, hash: clientHash });
    }

    // Fallback JSON handling
    let body = {};
    if (Buffer.isBuffer(rawBody)) {
      try { body = JSON.parse(rawBody.toString('utf8')); } catch {}
    } else if (req.body && typeof req.body === 'object') {
      body = req.body;
    }
    const { base64, type, hash } = body;
    if (!base64 || typeof base64 !== 'string') {
      return res.status(400).json({ error: 'Invalid upload payload' });
    }
    let fn = null;
    if (type === 'video') fn = saveVideo(base64, hash);
    else if (type === 'audio') fn = saveAudio(base64, hash);
    else fn = saveImage(base64, hash);

    if (!fn) return res.status(500).json({ error: 'Failed to save file' });
    return res.json({ success: true, filename: fn, url: '/uploads/' + fn, hash });
  } catch (e) {
    return res.status(500).json({ error: e.message });
  }
});

// Catch raw-body / request.aborted errors from aborted client uploads
app.use((err, req, res, next) => {
  if (err && (err.type === 'request.aborted' || err.message === 'request aborted' || err.code === 'ECONNRESET')) {
    if (!res.headersSent) res.status(400).json({ error: 'Upload request aborted' });
    return;
  }
  if (err && (err.status === 413 || err.type === 'entity.too.large')) {
    if (!res.headersSent) res.status(413).json({ error: 'File size exceeds limit' });
    return;
  }
  if (res.headersSent) return next(err);
  res.status(err.status || 500).json({ error: err.message || 'Server error' });
});

// SPA: serve index.html for topic paths (Express 5 compatible)
app.use((req, res, next) => {
  if (req.method !== 'GET') return next();
  if (req.path.startsWith('/uploads') || req.path.startsWith('/socket.io')) return next();
  if (req.path.includes('.') && !req.path.endsWith('/')) return next();
  res.sendFile(path.join(__dirname, 'index.html'));
});

const RAW_SECRET = process.env.CHAT_SECRET || 'anonymous-chat-secret-key-v1';
const SECRET_KEY = crypto.createHash('sha256').update(RAW_SECRET).digest();
function userUid(ip) {
  return crypto.createHash('sha256').update(String(ip || '') + '|' + RAW_SECRET).digest('hex').slice(0, 16);
}

function encrypt(text) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', SECRET_KEY, iv);
  const encrypted = Buffer.concat([cipher.update(text, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, encrypted]).toString('base64');
}

function decrypt(payload) {
  try {
    if (!payload) return '';
    const data = Buffer.from(payload, 'base64');
    if (data.length < 28) return '[invalid]';
    const iv = data.subarray(0, 12);
    const tag = data.subarray(12, 28);
    const encrypted = data.subarray(28);
    const decipher = crypto.createDecipheriv('aes-256-gcm', SECRET_KEY, iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
  } catch {
    return '[decrypt error]';
  }
}

// Strip zero-width, control, zalgo. NFC first so Vietnamese tones compose
// before leftover combining marks are removed.
function stripWeird(text) {
  return String(text)
    .normalize('NFC')
    .replace(/[\u200B-\u200D\uFEFF\u00AD\u2060\u180E\u2028\u2029\u202A-\u202E\u2066-\u2069]/g, '')
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F]/g, '')
    .replace(/\p{M}+/gu, '')
    .replace(/[\u0300-\u036F\u0483-\u0489\u1AB0-\u1AFF\u1DC0-\u1DFF\u20D0-\u20FF\uFE20-\uFE2F]/g, '');
}

const VN_EXTRA_LOWER = 'àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ';
const VN_EXTRA = VN_EXTRA_LOWER + VN_EXTRA_LOWER.toUpperCase();
const KEYBOARD_DISALLOWED = new RegExp('[^\\x20-\\x7E\\n\\r' + VN_EXTRA + ']', 'gu');
function keyboardOnly(text) {
  return String(text).normalize('NFC').replace(KEYBOARD_DISALLOWED, '');
}

function sanitize(text, maxLen = 2000) {
  if (typeof text !== 'string') return '';
  return keyboardOnly(stripWeird(text))
    .replace(/[<>]/g, '')
    .replace(/script|javascript|onerror|onload|eval/gi, '')
    .replace(/\r\n/g, '\n')
    .replace(/^\s+|\s+$/g, '')
    .slice(0, maxLen);
}

function sanitizeName(name) {
  if (typeof name !== 'string') return '';
  let s = keyboardOnly(stripWeird(name))
    .replace(/[<>{}[\]\\/`'";$]/g, '')
    .replace(/script|javascript/gi, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 40);
  // Require real printable length (letters/numbers ok, pure symbols alone rejected later)
  if ([...s].filter(ch => /\S/.test(ch)).length < 2) return '';
  return s;
}

function sanitizeTopic(name) {
  if (typeof name !== 'string') return '';
  return keyboardOnly(stripWeird(name))
    .replace(/[\r\n\t]/g, ' ')
    .replace(/[<>{}[\]\\`'"/;$]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 32);
}

// Rate limit: not too strict — fast chatters OK, spam blocked
// message: 15 / 10s | topic: 6 / 60s
const _actionBuckets = new Map();
function ACTION_LIMIT(ip, type) {
  const now = Date.now();
  let b = _actionBuckets.get(ip);
  if (!b) {
    b = { msg: [], topic: [], post: [], comment: [] };
    _actionBuckets.set(ip, b);
  }
  if (type === 'message') {
    b.msg = b.msg.filter(t => now - t < 10000);
    if (b.msg.length >= 15) return false;
    b.msg.push(now);
    return true;
  }
  if (type === 'topic') {
    b.topic = b.topic.filter(t => now - t < 60000);
    if (b.topic.length >= 6) return false;
    b.topic.push(now);
    return true;
  }
  if (type === 'post') {
    b.post = (b.post || []).filter(t => now - t < 60000);
    if (b.post.length >= 5) return false;
    b.post.push(now);
    return true;
  }
  if (type === 'comment') {
    b.comment = (b.comment || []).filter(t => now - t < 20000);
    if (b.comment.length >= 12) return false;
    b.comment.push(now);
    return true;
  }
  return true;
}
// occasional cleanup
setInterval(() => {
  const now = Date.now();
  for (const [ip, b] of _actionBuckets) {
    b.msg = (b.msg || []).filter(t => now - t < 10000);
    b.topic = (b.topic || []).filter(t => now - t < 60000);
    b.post = (b.post || []).filter(t => now - t < 60000);
    b.comment = (b.comment || []).filter(t => now - t < 20000);
    if (!b.msg.length && !b.topic.length && !b.post.length && !b.comment.length) _actionBuckets.delete(ip);
  }
}, 60000);


const ADJECTIVES = [
  'Swift','Silent','Cosmic','Lucky','Brave','Fuzzy','Rusty','Neon','Happy','Clever',
  'Mighty','Sneaky','Chill','Wild','Tiny','Epic','Sunny','Icy','Dusty','Rapid',
  'Gentle','Bold','Crazy','Quiet','Shadow','Golden','Crystal','Thunder','Frost','Blazing',
  'Mystic','Ancient','Stormy','Velvet','Iron','Silver','Emerald','Crimson','Azure','Amber',
  'Hidden','Flying','Dancing','Roaming','Sleepy','Hungry','Jolly','Noble','Savage','Radiant',
  'Obsidian','Lunar','Solar','Arctic','Tropical','Phantom','Eternal','Vivid','Calm','Fierce'
];
const NOUNS = [
  'Tiger','Panda','Falcon','Wolf','Otter','Fox','Bear','Hawk','Dragon','Phoenix',
  'Raven','Shark','Koala','Lynx','Moose','Crane','Viper','Badger','Heron','Cobra',
  'Gecko','Bison','Dove','Mantis','Comet','Nebula','River','Mountain','Forest','Ocean',
  'Echo','Spark','Blade','Shield','Crown','Arrow','Storm','Flame','Wave','Stone',
  'Lotus','Orchid','Maple','Cedar','Willow','Bamboo','Coral','Pearl','Jade','Opal',
  'Nova','Quasar','Meteor','Galaxy','Pixel','Cipher','Nexus','Vortex','Prism','Orbit'
];
const GRADIENTS = [
  '#ff6b6b,#feca57','#48dbfb,#0abde3','#1dd1a1,#10ac84','#f368e0,#ff9ff3',
  '#ff9f43,#ee5a24','#a29bfe,#6c5ce7','#fd79a8,#e84393','#00b894,#10ac84','#00cec9,#74b9ff',
  '#e17055,#d63031','#74b9ff,#0984e3','#55efc4,#00b894','#ffeaa7,#fdcb6e',
  '#6c5ce7,#a29bfe','#fd79a8,#e84393','#ffeaa7,#fdcb6e','#00cec9,#74b9ff','#fab1a0,#e17055',
  '#ff7675,#d63031','#74b9ff,#0984e3','#a29bfe,#6c5ce7','#fd79a8,#e84393',
  '#55efc4,#00b894','#ffeaa7,#fdcb6e','#81ecec,#00cec9','#fab1a0,#e17055',
  '#df9,#3a7','#f90,#c30','#09f,#06c','#c0f,#80c','#fc0,#f80',
  '#0ff,#08a','#f0f,#a0a','#ff8,#fa0','#8f8,#0a0','#88f,#44a',
  '#f88,#a22','#8ff,#2aa','#ffc,#ca0','#c8f,#84a','#fc8,#c60',
  '#4ecdc4,#556270','#c7f464,#556270','#ff6b6b,#556270','#4ecdc4,#c44d58',
  '#f7fff7,#343434','#ffe66d,#1a535c','#ff6b6b,#1a535c','#4ecdc4,#1a535c'
];

function hashStr(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) { h = ((h << 5) - h) + s.charCodeAt(i); h |= 0; }
  return Math.abs(h);
}
function generateName(seed) {
  const h = hashStr(seed);
  return ADJECTIVES[h % ADJECTIVES.length] + NOUNS[(h >> 3) % NOUNS.length] + (h % 100);
}
function getColor(seed) {
  return GRADIENTS[hashStr(seed) % GRADIENTS.length];
}
function getClientIP(socket) {
  const f = socket.handshake.headers['x-forwarded-for'];
  if (f) return f.split(',')[0].trim();
  return socket.handshake.address || 'unknown';
}

const dbPath = path.join(__dirname, 'chat.db');
const db = new Database(dbPath);

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    ip TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    color TEXT NOT NULL,
    created_at TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS topics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT ''
  )
`);

function addColumnIfMissing(table, column, definition) {
  const cols = db.prepare('PRAGMA table_info(' + table + ')').all().map(c => c.name);
  if (!cols.includes(column)) {
    console.log('Adding column:', table + '.' + column);
    db.exec('ALTER TABLE ' + table + ' ADD COLUMN ' + column + ' ' + definition);
  }
}

addColumnIfMissing('messages', 'user_name', "TEXT NOT NULL DEFAULT 'Anon'");
addColumnIfMissing('messages', 'user_color', "TEXT NOT NULL DEFAULT '#666,#999'");
addColumnIfMissing('messages', 'image', 'TEXT');
addColumnIfMissing('messages', 'reply_name', 'TEXT');
addColumnIfMissing('messages', 'reply_text', 'TEXT');
addColumnIfMissing('messages', 'reply_msg_id', 'INTEGER');
addColumnIfMissing('messages', 'topic_id', 'INTEGER DEFAULT 1');
addColumnIfMissing('messages', 'avatar', 'TEXT');
addColumnIfMissing('topics', 'creator_ip', 'TEXT');
addColumnIfMissing('topics', 'locked', 'INTEGER DEFAULT 0');
addColumnIfMissing('topics', 'locked_by', 'TEXT'); // user | moderator
addColumnIfMissing('topics', 'recommended', 'INTEGER DEFAULT 0');

db.exec(`
  CREATE TABLE IF NOT EXISTS posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT,
    user_name TEXT NOT NULL,
    user_color TEXT,
    avatar TEXT,
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    tags TEXT DEFAULT '[]',
    images TEXT DEFAULT '[]',
    likes_count INTEGER NOT NULL DEFAULT 0,
    comments_count INTEGER NOT NULL DEFAULT 0,
    shares_count INTEGER NOT NULL DEFAULT 0,
    ip TEXT,
    created_at TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS post_likes (
    post_id INTEGER NOT NULL,
    ip TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (post_id, ip)
  );
  CREATE TABLE IF NOT EXISTS post_comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    post_id INTEGER NOT NULL,
    user_id TEXT,
    user_name TEXT NOT NULL,
    user_color TEXT,
    avatar TEXT,
    body TEXT NOT NULL,
    ip TEXT,
    created_at TEXT NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_posts_created ON posts(id DESC);
  CREATE INDEX IF NOT EXISTS idx_comments_post ON post_comments(post_id, id);
`);
addColumnIfMissing('posts', 'video', 'TEXT');
addColumnIfMissing('post_comments', 'parent_id', 'INTEGER');
addColumnIfMissing('post_comments', 'reply_name', 'TEXT');
addColumnIfMissing('post_comments', 'reply_text', 'TEXT');
addColumnIfMissing('post_comments', 'image', 'TEXT');
addColumnIfMissing('posts', 'upvotes', 'INTEGER NOT NULL DEFAULT 0');
addColumnIfMissing('posts', 'downvotes', 'INTEGER NOT NULL DEFAULT 0');
addColumnIfMissing('posts', 'views', 'INTEGER NOT NULL DEFAULT 0');
addColumnIfMissing('posts', 'audio', 'TEXT');
db.exec(`
  CREATE TABLE IF NOT EXISTS post_votes (
    post_id INTEGER NOT NULL,
    ip TEXT NOT NULL,
    vote INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (post_id, ip)
  );
`);

addColumnIfMissing('users', 'avatar', 'TEXT');
addColumnIfMissing('users', 'name_changed_at', 'TEXT');
addColumnIfMissing('users', 'avatar_changed_at', 'TEXT');
addColumnIfMissing('users', 'uid', 'TEXT');
addColumnIfMissing('messages', 'user_uid', 'TEXT');
addColumnIfMissing('messages', 'user_ip', 'TEXT');
addColumnIfMissing('messages', 'video', 'TEXT');
addColumnIfMissing('messages', 'audio', 'TEXT');

// Fix empty / blank / zalgo-only names left by older clients (runs every startup)
(() => {
  const all = db.prepare('SELECT ip, name FROM users').all();
  const fixName = db.prepare('UPDATE users SET name = ? WHERE ip = ?');
  let fixed = 0;
  all.forEach(u => {
    const cleaned = sanitizeName(u.name || '');
    if (!cleaned || cleaned.length < 2) {
      const n = generateName(u.ip || crypto.randomBytes(4).toString('hex'));
      fixName.run(n, u.ip);
      fixed++;
      console.log('Fixed empty/invalid name for', u.ip, '->', n);
    } else if (cleaned !== u.name) {
      fixName.run(cleaned, u.ip);
      fixed++;
    }
  });
  // Fix message display names
  const msgs = db.prepare("SELECT id, user_name FROM messages WHERE user_name IS NULL OR TRIM(user_name) = '' OR LENGTH(TRIM(user_name)) < 1").all();
  const fixMsg = db.prepare("UPDATE messages SET user_name = 'Anon' WHERE id = ?");
  msgs.forEach(m => fixMsg.run(m.id));
  // Also scrub zalgo-only message names
  const allMsgNames = db.prepare('SELECT id, user_name FROM messages').all();
  allMsgNames.forEach(m => {
    const c = sanitizeName(m.user_name || '');
    if (!c) fixMsg.run(m.id);
  });
  if (fixed) console.log('Startup name fixes:', fixed);
})();

// System topics (cannot delete / lock)
const SYSTEM_TOPIC_NAMES = ['General', 'Archive', 'Patch notes'];
function isSystemTopicName(name) {
  return SYSTEM_TOPIC_NAMES.some(n => n.toLowerCase() === String(name || '').toLowerCase());
}
SYSTEM_TOPIC_NAMES.forEach(n => {
  const row = db.prepare('SELECT id FROM topics WHERE name = ? COLLATE NOCASE').get(n);
  if (!row) {
    db.prepare('INSERT INTO topics (name, created_at) VALUES (?, ?)').run(n, new Date().toISOString());
    console.log('Created system topic:', n);
  }
});
const generalId = db.prepare('SELECT id FROM topics WHERE name = ? COLLATE NOCASE').get('General').id;

// Assign old messages without topic to General
db.prepare('UPDATE messages SET topic_id = ? WHERE topic_id IS NULL').run(generalId);

const getUser = db.prepare('SELECT name, color, avatar, name_changed_at, avatar_changed_at, uid FROM users WHERE ip = ?');
const getUserByUid = db.prepare('SELECT ip, name, color, avatar, uid FROM users WHERE uid = ?');
const insertUser = db.prepare('INSERT OR IGNORE INTO users (ip, name, color, created_at) VALUES (?, ?, ?, ?)');
const updateUserName = db.prepare('UPDATE users SET name = ?, name_changed_at = ? WHERE ip = ?');
const updateUserAvatar = db.prepare('UPDATE users SET avatar = ?, avatar_changed_at = ? WHERE ip = ?');

const COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000; // 7 days for name only

function cooldownRemaining(iso) {
  if (!iso) return 0;
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return 0;
  const left = t + COOLDOWN_MS - Date.now();
  return left > 0 ? left : 0;
}

function formatCooldown(ms) {
  const d = Math.ceil(ms / (24 * 60 * 60 * 1000));
  if (d >= 2) return d + ' days';
  const h = Math.ceil(ms / (60 * 60 * 1000));
  if (h >= 2) return h + ' hours';
  const m = Math.max(1, Math.ceil(ms / (60 * 1000)));
  return m + ' min';
}

const listTopics = db.prepare('SELECT id, name, creator_ip, locked, locked_by, recommended FROM topics');
const getTopicByName = db.prepare('SELECT id, name, creator_ip, locked, locked_by, recommended FROM topics WHERE name = ? COLLATE NOCASE');
const getTopicById = db.prepare('SELECT id, name, creator_ip, locked, locked_by, recommended FROM topics WHERE id = ?');
const createTopicStmt = db.prepare('INSERT INTO topics (name, created_at, creator_ip, locked, locked_by, recommended) VALUES (?, ?, ?, 0, NULL, 0)');
const setTopicRecommended = db.prepare('UPDATE topics SET recommended = ? WHERE id = ?');
const setTopicLocked = db.prepare('UPDATE topics SET locked = ?, locked_by = ? WHERE id = ?');
const deleteTopicByIdStmt = db.prepare('DELETE FROM topics WHERE id = ?');
const deleteMsgsByTopic = db.prepare('DELETE FROM messages WHERE topic_id = ?');
const countMsgsByTopic = db.prepare('SELECT topic_id, COUNT(*) c FROM messages GROUP BY topic_id');
const getLastMsgs = db.prepare(`
  SELECT m.id, m.topic_id, m.user_name, m.content, m.image, m.created_at
  FROM messages m
  INNER JOIN (
    SELECT topic_id, MAX(id) AS max_id FROM messages GROUP BY topic_id
  ) latest ON m.id = latest.max_id
`);

function parseImageField(raw) {
  if (!raw) return [];
  const s = String(raw);
  if (s.startsWith('[')) {
    try {
      const arr = JSON.parse(s);
      if (Array.isArray(arr)) return arr.filter(Boolean).map(String).slice(0, 5);
    } catch {}
  }
  // legacy single filename
  return [s];
}

function getTopicsPayload() {
  const rows = listTopics.all();
  const counts = {};
  countMsgsByTopic.all().forEach(r => { counts[r.topic_id] = r.c; });
  const lastMap = {};
  getLastMsgs.all().forEach(r => {
    const text = r.content ? decrypt(r.content) : '';
    let preview = '';
    if (text) preview = text.slice(0, 80);
    else if (r.image) {
      const imgs = parseImageField(r.image);
      preview = imgs.length > 1 ? '[' + imgs.length + ' images]' : '[image]';
    }
    lastMap[r.topic_id] = {
      id: r.id,
      name: (r.user_name && String(r.user_name).trim()) || 'Anon',
      text: preview,
      time: r.created_at || ''
    };
  });
  const mapped = rows.map(t => {
    const room = io.sockets.adapter.rooms.get('t:' + t.id);
    const lm = lastMap[t.id] || null;
    return {
      id: t.id,
      name: t.name,
      isGeneral: t.name === 'General',
      isSystem: isSystemTopicName(t.name),
      locked: !!t.locked,
      lockedBy: t.locked ? (t.locked_by === 'moderator' ? 'moderator' : 'user') : null,
      recommended: !!t.recommended,
      msgCount: counts[t.id] || 0,
      online: room ? room.size : 0,
      lastMsg: lm,
      lastMsgId: lm ? lm.id : 0
    };
  });
  // System first; then score = msgCount + modest recommend boost (won't dominate hottest)
  const nonSys = mapped.filter(t => !t.isSystem);
  const topHot = nonSys.reduce((m, t) => Math.max(m, t.msgCount), 0);
  const boost = Math.max(15, Math.floor(topHot * 0.18)); // ~18% of hottest, min 15
  const sysOrder = { general: 0, archive: 1, 'patch notes': 2 };
  mapped.sort((a, b) => {
    if (a.isSystem && !b.isSystem) return -1;
    if (!a.isSystem && b.isSystem) return 1;
    if (a.isSystem && b.isSystem) {
      const ao = sysOrder[a.name.toLowerCase()] ?? 99;
      const bo = sysOrder[b.name.toLowerCase()] ?? 99;
      return ao - bo;
    }
    const sa = a.msgCount + (a.recommended ? boost : 0);
    const sb = b.msgCount + (b.recommended ? boost : 0);
    if (sb !== sa) return sb - sa;
    if (a.recommended !== b.recommended) return a.recommended ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
  return mapped;
}
const insertMsg = db.prepare(`
  INSERT INTO messages (user_id, user_name, user_color, content, image, reply_name, reply_text, reply_msg_id, topic_id, created_at, avatar, user_uid, user_ip, video, audio)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
const HISTORY_PAGE = 80;
const getHistory = db.prepare(`
  SELECT id, user_id, user_name, user_color, content, image, reply_name, reply_text, reply_msg_id, created_at, avatar, user_uid, video, audio
  FROM messages WHERE topic_id = ? ORDER BY id DESC LIMIT ?
`);
const getHistoryBefore = db.prepare(`
  SELECT id, user_id, user_name, user_color, content, image, reply_name, reply_text, reply_msg_id, created_at, avatar, user_uid, video, audio
  FROM messages WHERE topic_id = ? AND id < ? ORDER BY id DESC LIMIT ?
`);
const getHistoryAfter = db.prepare(`
  SELECT id, user_id, user_name, user_color, content, image, reply_name, reply_text, reply_msg_id, created_at, avatar, user_uid, video, audio
  FROM messages WHERE topic_id = ? AND id > ? ORDER BY id ASC LIMIT ?
`);

function mapMsgRow(r) {
  const name = sanitizeName(r.user_name || '') || 'Anon';
  const files = parseImageField(r.image);
  const images = files.map(f =>
    String(f).startsWith('http') || String(f).startsWith('/') ? f : '/uploads/' + f
  );
  const mediaUrl = (v) => v ? (String(v).startsWith('/') || String(v).startsWith('http') ? v : '/uploads/' + v) : null;
  return {
    msgId: r.id,
    id: r.user_id || '',
    name,
    color: r.user_color || '#666,#999',
    avatar: r.avatar
      ? (String(r.avatar).startsWith('http') || String(r.avatar).startsWith('/')
          ? r.avatar
          : '/uploads/' + r.avatar)
      : null,
    text: r.content ? decrypt(r.content) : '',
    image: images[0] || null,
    images,
    video: mediaUrl(r.video),
    audio: mediaUrl(r.audio),
    replyName: r.reply_name || null,
    replyText: r.reply_text || null,
    replyMsgId: r.reply_msg_id || null,
    time: r.created_at || '',
    uid: r.user_uid || null
  };
}

function ensureUser(ip) {
  let user = getUser.get(ip);
  const uid = userUid(ip);
  if (!user) {
    const name = generateName(ip);
    const color = getColor(ip);
    insertUser.run(ip, name, color, new Date().toISOString());
    db.prepare('UPDATE users SET uid = ? WHERE ip = ?').run(uid, ip);
    user = { name, color, avatar: null, uid };
  } else {
    const cleaned = sanitizeName(user.name || '');
    if (!cleaned || cleaned.length < 2) {
      const name = generateName(ip);
      db.prepare('UPDATE users SET name = ? WHERE ip = ?').run(name, ip);
      user.name = name;
    } else if (cleaned !== user.name) {
      db.prepare('UPDATE users SET name = ? WHERE ip = ?').run(cleaned, ip);
      user.name = cleaned;
    }
    if (!user.uid) {
      db.prepare('UPDATE users SET uid = ? WHERE ip = ?').run(uid, ip);
      user.uid = uid;
    }
  }
  user.uid = user.uid || uid;
  return user;
}

function avatarUrl(file) {
  if (!file) return null;
  if (String(file).startsWith('http') || String(file).startsWith('/')) return file;
  return '/uploads/' + file;
}

function buildProfilePayload(socket) {
  return {
    id: socket.profile.id,
    name: socket.profile.name,
    color: socket.profile.color,
    ip: socket.profile.ip,
    avatar: socket.profile.avatar,
    uid: socket.profile.uid
  };
}

function getTopicMembers(topicId) {
  const room = io.sockets.adapter.rooms.get('t:' + topicId);
  if (!room) return [];
  const out = [];
  const seen = new Set();
  for (const sid of room) {
    const s = io.sockets.sockets.get(sid);
    if (!s || !s.profile) continue;
    const key = s.profile.uid || s.profile.ip || sid;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({
      uid: s.profile.uid || null,
      id: s.profile.id,
      name: s.profile.name || 'Anon',
      color: s.profile.color || '#666,#999',
      avatar: s.profile.avatar || null
    });
  }
  // shuffle lightly for "random" avatar order
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}


function calcUserDisk(uid, ip) {
  let bytes = 0;
  const rows = db.prepare('SELECT image FROM messages WHERE user_uid = ? OR user_ip = ?').all(uid, ip);
  rows.forEach(r => {
    if (!r.image) return;
    let files = [];
    try {
      if (String(r.image).startsWith('[')) files = JSON.parse(r.image);
      else files = [r.image];
    } catch { files = [r.image]; }
    files.forEach(f => {
      try {
        const fp = path.join(UPLOADS, String(f).replace(/^\/uploads\//, ''));
        bytes += fs.statSync(fp).size;
      } catch {}
    });
  });
  // avatar
  try {
    const u = getUser.get(ip);
    if (u && u.avatar && !String(u.avatar).startsWith('http')) {
      bytes += fs.statSync(path.join(UPLOADS, u.avatar)).size;
    }
  } catch {}
  return bytes;
}

function formatBytes(n) {
  if (n < 1024) return n + ' B';
  if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1048576).toFixed(2) + ' MB';
}

let bytesIn = 0;
let bytesOut = 0;

// CPU usage sampling
let prevCpu = os.cpus().map(c => c.times);
function getCpuPercent() {
  const cpus = os.cpus();
  let idleDiff = 0, totalDiff = 0;
  cpus.forEach((cpu, i) => {
    const prev = prevCpu[i];
    const idle = cpu.times.idle - prev.idle;
    const total = Object.values(cpu.times).reduce((a, b) => a + b, 0)
      - Object.values(prev).reduce((a, b) => a + b, 0);
    idleDiff += idle;
    totalDiff += total;
  });
  prevCpu = cpus.map(c => c.times);
  if (totalDiff === 0) return 0;
  return Math.min(100, Math.max(0, Math.round(100 * (1 - idleDiff / totalDiff))));
}

function readNum(path) {
  try {
    const s = fs.readFileSync(path, 'utf8').trim();
    if (s === 'max' || s === '') return null;
    const n = parseInt(s, 10);
    // ignore "unlimited" sentinel values used by cgroup
    if (!Number.isFinite(n) || n <= 0 || n >= 1e15) return null;
    return n;
  } catch {
    return null;
  }
}

function getCgroupMemory() {
  // cgroup v2
  const v2Max = readNum('/sys/fs/cgroup/memory.max');
  const v2Cur = readNum('/sys/fs/cgroup/memory.current');
  if (v2Max != null && v2Cur != null) return { used: v2Cur, total: v2Max };

  // cgroup v1
  const v1Lim = readNum('/sys/fs/cgroup/memory/memory.limit_in_bytes');
  const v1Use = readNum('/sys/fs/cgroup/memory/memory.usage_in_bytes');
  if (v1Lim != null && v1Use != null) return { used: v1Use, total: v1Lim };

  // OpenVZ / Virtuozzo beancounters (shared VPS)
  try {
    const bean = fs.readFileSync('/proc/user_beancounters', 'utf8');
    const m = bean.match(/privvmpages\s+(\d+)\s+\d+\s+\d+\s+(\d+)/);
    if (m) {
      const used = parseInt(m[1], 10) * 4096;
      const total = parseInt(m[2], 10) * 4096;
      if (total > 0 && total < 1e15) return { used, total };
    }
  } catch {}

  return null;
}

function getRamInfo() {
  const cg = getCgroupMemory();
  if (cg) {
    const pct = Math.round((cg.used / cg.total) * 100);
    return formatBytes(cg.used) + ' / ' + formatBytes(cg.total) + ' (' + pct + '%)';
  }
  // Fallback: Node process RSS only (avoid showing host RAM on shared VPS)
  const rss = process.memoryUsage().rss;
  return formatBytes(rss);
}

const SERVER_STARTED = Date.now();

function formatUptime(ms) {
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (d > 0) return d + 'd ' + h + 'h ' + m + 'm';
  if (h > 0) return h + 'h ' + m + 'm';
  if (m > 0) return m + 'm ' + sec + 's';
  return sec + 's';
}

function getStats() {
  let dbSize = 0;
  try { dbSize += fs.statSync(dbPath).size; } catch {}
  try { dbSize += fs.statSync(hashDbPath).size; } catch {}
  return {
    online: io.engine.clientsCount || 0,
    db: formatBytes(dbSize),
    netIn: formatBytes(bytesIn),
    netOut: formatBytes(bytesOut),
    ram: getRamInfo(),
    cpu: getCpuPercent() + '%',
    uptime: formatUptime(Date.now() - SERVER_STARTED)
  };
}

const hashDbPath = path.join(__dirname, 'hash.db');
const hashDb = new Database(hashDbPath);

hashDb.exec(`
  CREATE TABLE IF NOT EXISTS file_hashes (
    hash TEXT PRIMARY KEY,
    filename TEXT NOT NULL,
    created_at TEXT NOT NULL
  );
`);

const findFileByHash = hashDb.prepare('SELECT filename FROM file_hashes WHERE hash = ?');
const insertFileHash = hashDb.prepare('INSERT OR REPLACE INTO file_hashes (hash, filename, created_at) VALUES (?, ?, ?)');


function computeFileQuickHash(filePath) {
  try {
    const stat = fs.statSync(filePath);
    const sliceSize = 64 * 1024;
    let buf;
    if (stat.size <= sliceSize * 3) {
      buf = fs.readFileSync(filePath);
    } else {
      const fd = fs.openSync(filePath, 'r');
      try {
        const head = Buffer.alloc(sliceSize);
        fs.readSync(fd, head, 0, sliceSize, 0);
        const midPos = Math.floor(stat.size / 2);
        const mid = Buffer.alloc(sliceSize);
        fs.readSync(fd, mid, 0, sliceSize, midPos);
        const tail = Buffer.alloc(sliceSize);
        fs.readSync(fd, tail, 0, sliceSize, stat.size - sliceSize);
        const sizeBuf = Buffer.alloc(8);
        sizeBuf.writeDoubleBE(stat.size, 0);
        buf = Buffer.concat([head, mid, tail, sizeBuf]);
      } finally {
        fs.closeSync(fd);
      }
    }
    return crypto.createHash('sha256').update(buf).digest('hex');
  } catch {
    return null;
  }
}

function computeBufferQuickHash(buf) {
  try {
    if (!Buffer.isBuffer(buf)) return null;
    const len = buf.length;
    const sliceSize = 64 * 1024;
    let combined;
    if (len <= sliceSize * 3) {
      combined = buf;
    } else {
      const head = buf.subarray(0, sliceSize);
      const midPos = Math.floor(len / 2);
      const mid = buf.subarray(midPos, midPos + sliceSize);
      const tail = buf.subarray(len - sliceSize);
      const sizeBuf = Buffer.alloc(8);
      sizeBuf.writeDoubleBE(len, 0);
      combined = Buffer.concat([head, mid, tail, sizeBuf]);
    }
    return crypto.createHash('sha256').update(combined).digest('hex');
  } catch {
    return null;
  }
}

// Startup scan: index files in uploads/, detect duplicate hashes, update DB references, and delete redundant files
(() => {
  try {
    const files = fs.readdirSync(UPLOADS);
    const now = new Date().toISOString();
    const hashToFileMap = new Map();
    let indexed = 0;
    let duplicatesRemoved = 0;
    let bytesSaved = 0;

    files.forEach(file => {
      const fullPath = path.join(UPLOADS, file);
      try {
        const stat = fs.statSync(fullPath);
        if (stat.isFile()) {
          const hash = computeFileQuickHash(fullPath);
          if (hash) {
            if (hashToFileMap.has(hash)) {
              // Duplicate file detected! Keep primary file, clean up duplicate
              const primaryFile = hashToFileMap.get(hash);
              const dupFile = file;
              
              if (dupFile !== primaryFile) {
                try {
                  db.prepare("UPDATE messages SET image = REPLACE(image, ?, ?) WHERE image LIKE ?").run(dupFile, primaryFile, '%' + dupFile + '%');
                  db.prepare("UPDATE messages SET video = ? WHERE video = ?").run(primaryFile, dupFile);
                  db.prepare("UPDATE messages SET audio = ? WHERE audio = ?").run(primaryFile, dupFile);
                  db.prepare("UPDATE posts SET images = REPLACE(images, ?, ?) WHERE images LIKE ?").run(dupFile, primaryFile, '%' + dupFile + '%');
                  db.prepare("UPDATE posts SET video = ? WHERE video = ?").run(primaryFile, dupFile);
                  db.prepare("UPDATE users SET avatar = ? WHERE avatar = ?").run(dupFile, primaryFile);
                  db.prepare("UPDATE post_comments SET avatar = ? WHERE avatar = ?").run(dupFile, primaryFile);
                } catch (dbErr) {}

                try {
                  fs.unlinkSync(fullPath);
                  duplicatesRemoved++;
                  bytesSaved += stat.size;
                } catch {}
              }
            } else {
              hashToFileMap.set(hash, file);
              insertFileHash.run(hash, file, now);
              indexed++;
            }
          }
        }
      } catch {}
    });

    if (indexed > 0 || duplicatesRemoved > 0) {
      log('QuickHash startup scan: indexed ' + indexed + ' unique file(s), deleted ' + duplicatesRemoved + ' duplicate file(s) saving ' + formatBytes(bytesSaved));
    }
  } catch (e) {
    log('QuickHash startup scan error: ' + e.message);
  }
})();

function saveMediaWithQuickHash(buf, ext, clientHash) {
  const hash = clientHash || computeBufferQuickHash(buf);
  if (hash) {
    const existing = findFileByHash.get(hash);
    if (existing && existing.filename && fs.existsSync(path.join(UPLOADS, existing.filename))) {
      return existing.filename;
    }
  }
  const filename = crypto.randomBytes(16).toString('hex') + '.' + ext;
  fs.writeFileSync(path.join(UPLOADS, filename), buf);
  bytesIn += buf.length;
  if (hash) {
    insertFileHash.run(hash, filename, new Date().toISOString());
  }
  return filename;
}

function saveImage(base64, clientHash) {
  if (!base64 || typeof base64 !== 'string') return null;
  if (base64.startsWith('/uploads/')) {
    const fn = path.basename(base64.replace(/^\/uploads\//, ''));
    if (fn && fs.existsSync(path.join(UPLOADS, fn))) return fn;
  }
  const match = base64.match(/^data:image\/(png|jpeg|jpg|gif|webp);base64,(.+)$/i);
  if (!match) return null;
  const ext = match[1] === 'jpeg' ? 'jpg' : match[1].toLowerCase();
  const buf = Buffer.from(match[2], 'base64');
  if (buf.length > 50 * 1024 * 1024) return null;
  return saveMediaWithQuickHash(buf, ext, clientHash);
}

function saveVideo(base64, clientHash) {
  if (!base64 || typeof base64 !== 'string') return null;
  if (base64.startsWith('/uploads/')) {
    const fn = path.basename(base64.replace(/^\/uploads\//, ''));
    if (fn && fs.existsSync(path.join(UPLOADS, fn))) return fn;
  }
  const match = String(base64 || '').match(/^data:video\/(mp4|webm|quicktime|ogg);base64,(.+)$/i);
  if (!match) return null;
  const extMap = { quicktime: 'mov' };
  const ext = extMap[match[1].toLowerCase()] || match[1].toLowerCase();
  const buf = Buffer.from(match[2], 'base64');
  if (buf.length > 50 * 1024 * 1024) return null;
  return saveMediaWithQuickHash(buf, ext, clientHash);
}

function saveAudio(base64, clientHash) {
  if (!base64 || typeof base64 !== 'string') return null;
  if (base64.startsWith('/uploads/')) {
    const fn = path.basename(base64.replace(/^\/uploads\//, ''));
    if (fn && fs.existsSync(path.join(UPLOADS, fn))) return fn;
  }
  const match = String(base64 || '').match(/^data:audio\/(mpeg|mp3|ogg|wav|webm|mp4);base64,(.+)$/i);
  if (!match) return null;
  const extMap = { mpeg: 'mp3', mp3: 'mp3', mp4: 'm4a' };
  const ext = extMap[match[1].toLowerCase()] || match[1].toLowerCase();
  const buf = Buffer.from(match[2], 'base64');
  if (buf.length > 50 * 1024 * 1024) return null;
  return saveMediaWithQuickHash(buf, ext, clientHash);
}

const POST_IMAGE_MAX = 5 * 1024 * 1024;   // 5 MB for images
const POST_VIDEO_MAX = 50 * 1024 * 1024;  // 50 MB for videos
const POST_AUDIO_MAX = 50 * 1024 * 1024;  // 50 MB for audio
const MAX_POSTS_PER_DAY = 12;
const MAX_VIDEOS_PER_DAY = 5; // stricter — videos eat disk
const DUPLICATE_POST_HOURS = 24;

function savePostImage(base64, clientHash) {
  if (!base64 || typeof base64 !== 'string') return null;
  // Handle pre-uploaded URL (from HTTP upload)
  if (base64.startsWith('/uploads/')) {
    const fn = path.basename(base64.replace(/^\/uploads\//, ''));
    if (fn && fs.existsSync(path.join(UPLOADS, fn))) return fn;
  }
  const match = base64.match(/^data:image\/(png|jpeg|jpg|gif|webp);base64,(.+)$/i);
  if (!match) return null;
  const ext = match[1] === 'jpeg' ? 'jpg' : match[1].toLowerCase();
  const buf = Buffer.from(match[2], 'base64');
  if (buf.length > POST_IMAGE_MAX) return null;
  return saveMediaWithQuickHash(buf, ext, clientHash);
}
function savePostVideo(base64, clientHash) {
  if (!base64 || typeof base64 !== 'string') return null;
  if (base64.startsWith('/uploads/')) {
    const fn = path.basename(base64.replace(/^\/uploads\//, ''));
    if (fn && fs.existsSync(path.join(UPLOADS, fn))) return fn;
  }
  const match = base64.match(/^data:video\/(mp4|webm|quicktime|ogg);base64,(.+)$/i);
  if (!match) return null;
  const extMap = { quicktime: 'mov' };
  const ext = extMap[match[1].toLowerCase()] || match[1].toLowerCase();
  const buf = Buffer.from(match[2], 'base64');
  if (buf.length > POST_VIDEO_MAX) return null;
  return saveMediaWithQuickHash(buf, ext, clientHash);
}
function savePostAudio(base64, clientHash) {
  if (!base64 || typeof base64 !== 'string') return null;
  if (base64.startsWith('/uploads/')) {
    const fn = path.basename(base64.replace(/^\/uploads\//, ''));
    if (fn && fs.existsSync(path.join(UPLOADS, fn))) return fn;
  }
  const match = base64.match(/^data:audio\/(mpeg|mp3|ogg|wav|webm|mp4);base64,(.+)$/i);
  if (!match) return null;
  const extMap = { mpeg: 'mp3', mp3: 'mp3', mp4: 'm4a' };
  const ext = extMap[match[1].toLowerCase()] || match[1].toLowerCase();
  const buf = Buffer.from(match[2], 'base64');
  if (buf.length > POST_AUDIO_MAX) return null;
  return saveMediaWithQuickHash(buf, ext, clientHash);
}

/** Delete media files of a post from disk (images, video, audio). */
function deletePostMedia(post) {
  if (!post) return 0;
  let n = 0;
  const tryUnlink = (name) => {
    if (!name) return;
    const base = path.basename(String(name).replace(/^\/uploads\//, ''));
    if (!base || base.includes('..')) return;
    const full = path.join(UPLOADS, base);
    try {
      if (fs.existsSync(full)) {
        fs.unlinkSync(full);
        n++;
        console.log('Deleted media file:', base);
      }
    } catch (e) {
      console.error('Failed to delete media:', base, e.message);
    }
  };
  if (post.video) tryUnlink(post.video);
  if (post.audio) tryUnlink(post.audio);
  let imgs = [];
  try {
    if (post.images) {
      const raw = typeof post.images === 'string' ? post.images : JSON.stringify(post.images);
      if (raw.startsWith('[')) imgs = JSON.parse(raw);
      else if (raw) imgs = [raw];
    }
  } catch {}
  if (Array.isArray(imgs)) imgs.forEach(tryUnlink);
  return n;
}


// ===== Explore (posts) =====
const insertPost = db.prepare(`
  INSERT INTO posts (user_id, user_name, user_color, avatar, title, body, tags, images, video, audio, ip, created_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
const getPost = db.prepare('SELECT * FROM posts WHERE id = ?');
const listPosts = db.prepare('SELECT * FROM posts ORDER BY id DESC LIMIT ? OFFSET ?');
const deletePostStmt = db.prepare('DELETE FROM posts WHERE id = ? AND ip = ?');
const deletePostByIdStmt = db.prepare('DELETE FROM posts WHERE id = ?');
// legacy post_likes cleanup (table may not exist on fresh installs)
let deletePostLikes;
try { deletePostLikes = db.prepare('DELETE FROM post_likes WHERE post_id = ?'); } catch(e) { deletePostLikes = { run() {} }; }
const deletePostComments = db.prepare('DELETE FROM post_comments WHERE post_id = ?');
const deletePostVotes = db.prepare('DELETE FROM post_votes WHERE post_id = ?');
const getVote = db.prepare('SELECT vote FROM post_votes WHERE post_id = ? AND ip = ?');
const upsertVote = db.prepare(`
  INSERT INTO post_votes (post_id, ip, vote, created_at) VALUES (?, ?, ?, ?)
  ON CONFLICT(post_id, ip) DO UPDATE SET vote = excluded.vote, created_at = excluded.created_at
`);
const removeVote = db.prepare('DELETE FROM post_votes WHERE post_id = ? AND ip = ?');
const recountVotes = db.prepare(`
  UPDATE posts SET
    upvotes = (SELECT COUNT(*) FROM post_votes WHERE post_id = ? AND vote = 1),
    downvotes = (SELECT COUNT(*) FROM post_votes WHERE post_id = ? AND vote = -1)
  WHERE id = ?
`);


const insertComment = db.prepare(`
  INSERT INTO post_comments (post_id, user_id, user_name, user_color, avatar, body, ip, created_at, parent_id, reply_name, reply_text, image)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
const listComments = db.prepare('SELECT * FROM post_comments WHERE post_id = ? ORDER BY id ASC LIMIT 200');
const bumpComments = db.prepare('UPDATE posts SET comments_count = (SELECT COUNT(*) FROM post_comments WHERE post_id = ?) WHERE id = ?');
const bumpShares = db.prepare('UPDATE posts SET shares_count = shares_count + 1 WHERE id = ?');
const bumpViews = db.prepare('UPDATE posts SET views = COALESCE(views,0) + 1 WHERE id = ?');


function parseJsonArr(raw, max) {
  try {
    const a = JSON.parse(raw || '[]');
    if (!Array.isArray(a)) return [];
    return a.map(String).filter(Boolean).slice(0, max || 20);
  } catch { return []; }
}

function mapPost(row, ip) {
  const images = parseJsonArr(row.images, 5).map(f =>
    String(f).startsWith('/') || String(f).startsWith('http') ? f : '/uploads/' + f
  );
  let myVote = 0;
  if (ip) {
    try {
      const v = getVote.get(row.id, ip);
      if (v) myVote = v.vote || 0;
    } catch {}
  }
  const up = row.upvotes != null ? row.upvotes : (row.likes_count || 0);
  const down = row.downvotes || 0;
  return {
    id: row.id,
    userId: row.user_id || '',
    name: row.user_name || 'Anon',
    color: row.user_color || '#666,#999',
    avatar: row.avatar
      ? (String(row.avatar).startsWith('http') || String(row.avatar).startsWith('/')
          ? row.avatar : '/uploads/' + row.avatar)
      : null,
    title: row.title || '',
    body: row.body || '',
    tags: parseJsonArr(row.tags, 8),
    images,
    video: row.video
      ? (String(row.video).startsWith('/') || String(row.video).startsWith('http') ? row.video : '/uploads/' + row.video)
      : null,
    audio: row.audio
      ? (String(row.audio).startsWith('/') || String(row.audio).startsWith('http') ? row.audio : '/uploads/' + row.audio)
      : null,
    upvotes: up,
    downvotes: down,
    score: up - down,
    likes: up,
    comments: row.comments_count || 0,
    shares: row.shares_count || 0,
    views: row.views || 0,
    liked: myVote === 1,
    myVote,
    time: row.created_at || '',
    isOwner: ip ? row.ip === ip : false
  };
}


io.on('connection', (socket) => {
  const clientIp = getClientIP(socket);
  const clientMac = (socket.handshake.query && socket.handshake.query.mac) || null;
  socket.clientMac = clientMac;
  socket.authenticated = false;
  socket.topicId = null;
  log('Connection: socketId=' + socket.id.slice(0, 6) + ' mac=' + (clientMac || 'none') + ' ip=' + clientIp);

  // Multi-MAC auto-login: check if this MAC is authorized on any key
  const keysData = loadKeys();
  let autoKey = null;
  if (clientMac) {
    for (const [k, entry] of Object.entries(keysData.keys)) {
      if (hasMac(entry, clientMac)) { autoKey = k; break; }
    }
  }
  if (autoKey) {
    const keyEntry = keysData.keys[autoKey];
    const userIp = keyEntry.userIp;
    const profile = ensureUser(userIp);
    socket.profile = {
      id: socket.id.slice(0, 6),
      name: profile.name,
      color: profile.color,
      ip: userIp,
      avatar: avatarUrl(profile.avatar),
      uid: profile.uid || userUid(userIp)
    };
    socket.authenticated = true;
    socket.authKey = autoKey;
    log('Auto-login (by MAC): "' + profile.name + '" mac=' + clientMac + ' userIp=' + userIp);
    socket.emit('auto-auth', { key: autoKey });
    socket.emit('profile', buildProfilePayload(socket));
    socket.emit('topics', getTopicsPayload());
    io.emit('stats', getStats());
  } else {
    socket.emit('require-auth');
  }

  socket.on('auth-key', (payload) => {
    if (!payload || typeof payload.key !== 'string') return;
    const key = payload.key.trim();
    if (!key) return;
    const currentMac = (payload && payload.mac) || socket.clientMac || null;
    socket.clientMac = currentMac;
    const keysData = loadKeys();
    const keyEntry = keysData.keys[key];
    if (!keyEntry) {
      log('Auth failed: invalid key from mac=' + currentMac);
      socket.emit('auth-error', { message: 'Invalid key' });
      return;
    }
    // Add current MAC to authorized devices list (UNLIMITED DEVICES PER ACCOUNT!)
    if (currentMac) {
      addMac(keyEntry, currentMac);
      saveKeys(keysData);
    }

    const userIp = keyEntry.userIp;
    const profile = ensureUser(userIp);
    socket.profile = {
      id: socket.id.slice(0, 6),
      name: profile.name,
      color: profile.color,
      ip: userIp,
      avatar: avatarUrl(profile.avatar),
      uid: profile.uid || userUid(userIp)
    };
    socket.authenticated = true;
    socket.authKey = key;
    log('Login (by MAC): "' + profile.name + '" mac=' + currentMac + ' userIp=' + userIp);

    socket.emit('profile', buildProfilePayload(socket));
    socket.emit('topics', getTopicsPayload());
    io.emit('stats', getStats());
  });

  socket.on('create-key', (payload) => {
    if (!payload || typeof payload.key !== 'string') return;
    const key = payload.key.trim();
    if (!key || key.length < 4) {
      socket.emit('auth-error', { message: 'Key must be at least 4 characters' });
      return;
    }
    const currentMac = (payload && payload.mac) || socket.clientMac || null;
    socket.clientMac = currentMac;
    const keysData = loadKeys();
    if (keysData.keys[key]) {
      socket.emit('auth-error', { message: 'This key is already taken' });
      return;
    }
    const recovery = generateRecoveryKey();
    const encryptedLogin = encryptRecovery(recovery, key);
    const userIp = 'key-' + crypto.randomBytes(8).toString('hex');

    const profile = ensureUser(userIp);

    keysData.keys[key] = {
      macs: currentMac ? [currentMac] : [],
      userIp: userIp,
      recovery: encryptedLogin,
      createdAt: new Date().toISOString()
    };
    saveKeys(keysData);

    socket.profile = {
      id: socket.id.slice(0, 6),
      name: profile.name,
      color: profile.color,
      ip: userIp,
      avatar: avatarUrl(profile.avatar),
      uid: profile.uid || userUid(userIp)
    };
    socket.authenticated = true;
    socket.authKey = key;
    log('Registered (by MAC): "' + profile.name + '" mac=' + currentMac + ' userIp=' + userIp);

    socket.emit('key-created', { recoveryKey: recovery });
    socket.emit('profile', buildProfilePayload(socket));
    socket.emit('topics', getTopicsPayload());
    io.emit('stats', getStats());
  });

  socket.on('recover-key', (payload) => {
    if (!payload || typeof payload.recoveryKey !== 'string') return;
    const recovery = payload.recoveryKey.trim();
    if (!recovery) return;
    const keysData = loadKeys();
    for (const [loginKey, entry] of Object.entries(keysData.keys)) {
      if (entry.recovery) {
        const decrypted = decryptRecovery(recovery, entry.recovery);
        if (decrypted === loginKey) {
          log('Key recovered: userIp=' + entry.userIp);
          socket.emit('key-recovered', { key: loginKey });
          return;
        }
      }
    }
    log('Recovery failed: invalid recovery key from mac=' + socket.clientMac);
    socket.emit('auth-error', { message: 'Invalid recovery key' });
  });

  socket.on('logout', () => {
    const name = socket.profile ? socket.profile.name : 'Unknown';
    if (socket.authKey) {
      const keysData = loadKeys();
      if (keysData.keys[socket.authKey] && socket.clientMac) {
        removeMac(keysData.keys[socket.authKey], socket.clientMac);
        saveKeys(keysData);
      }
      socket.authKey = null;
    }
    socket.authenticated = false;
    log('Logout: "' + name + '" mac=' + socket.clientMac);
    socket.emit('logged-out');
  });

  socket.on('ping-check', (t) => socket.emit('pong-check', t));

  socket.on('change-name', (newName) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    let clean = sanitizeName(newName);
    if (!clean || clean.length < 2) {
      clean = generateName(ip);
    }
    const u = getUser.get(ip);
    if (u && u.name === clean) {
      socket.emit('name-ok', clean);
      return;
    }
    const left = cooldownRemaining(u && u.name_changed_at);
    if (left > 0) {
      socket.emit('error', 'Name change on cooldown (' + formatCooldown(left) + ' left)');
      return;
    }
    updateUserName.run(clean, new Date().toISOString(), ip);
    socket.profile.name = clean;
    socket.emit('profile', buildProfilePayload(socket));
    socket.emit('name-ok', clean);
  });

  socket.on('change-avatar', (data) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    let base64 = typeof data === 'string' ? data : (data && data.base64 ? data.base64 : null);
    let hash = typeof data === 'object' && data ? data.hash : null;
    if (!base64 || typeof base64 !== 'string') {
      socket.emit('error', 'Invalid avatar');
      return;
    }
    const match = base64.match(/^data:image\/(png|jpeg|jpg|gif|webp);base64,(.+)$/i);
    if (!match) {
      socket.emit('error', 'Only images allowed');
      return;
    }
    const buf = Buffer.from(match[2], 'base64');
    if (buf.length > 5 * 1024 * 1024) {
      socket.emit('error', 'Avatar max 5MB');
      return;
    }
    const file = saveImage(base64, hash);
    if (!file) {
      socket.emit('error', 'Failed to save avatar');
      return;
    }
    updateUserAvatar.run(file, new Date().toISOString(), ip);
    socket.profile.avatar = avatarUrl(file);
    socket.emit('profile', buildProfilePayload(socket));
    socket.emit('avatar-ok', socket.profile.avatar);
  });

  socket.on('create-topic', (name) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    if (!ACTION_LIMIT(socket.clientMac || clientIp, 'topic')) {
      socket.emit('error', 'Too many topics — slow down a bit');
      return;
    }
    const clean = sanitizeTopic(name);
    if (!clean || clean.length < 2) {
      socket.emit('error', 'Topic name too short');
      return;
    }
    if (isSystemTopicName(clean)) {
      socket.emit('error', 'Reserved topic name');
      return;
    }
    try {
      const info = createTopicStmt.run(clean, new Date().toISOString(), ip);
      io.emit('topics', getTopicsPayload());
      socket.emit('topic-created', { id: info.lastInsertRowid, name: clean });
    } catch {
      socket.emit('error', 'Topic already exists');
    }
  });

  function isTopicOwner(topic, userIp) {
    if (!topic || isSystemTopicName(topic.name)) return false;
    return !!(topic.creator_ip && topic.creator_ip === userIp);
  }

  socket.on('join-topic', (topicName) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    const clean = sanitizeTopic(topicName || 'General');
    const topic = getTopicByName.get(clean) || getTopicByName.get('General');
    if (!topic) return;

    if (socket.topicId) socket.leave('t:' + socket.topicId);
    socket.topicId = topic.id;
    socket.join('t:' + topic.id);

    const rows = getHistory.all(topic.id, HISTORY_PAGE).reverse();
    const room = io.sockets.adapter.rooms.get('t:' + topic.id);
    const topicOnline = room ? room.size : 1;
    const hasMore = rows.length >= HISTORY_PAGE;
    const owner = isTopicOwner(topic, ip);
    const members = getTopicMembers(topic.id);
    socket.emit('joined', {
      topic: {
        id: topic.id,
        name: topic.name,
        locked: !!topic.locked,
        lockedBy: topic.locked ? (topic.locked_by === 'moderator' ? 'moderator' : 'user') : null,
        isOwner: owner,
        isGeneral: topic.name === 'General',
        isSystem: isSystemTopicName(topic.name)
      },
      topicOnline: members.length,
      members,
      hasMore,
      history: rows.map(mapMsgRow)
    });
    io.emit('topics', getTopicsPayload());
    io.to('t:' + topic.id).emit('topic-online', { online: members.length, members: getTopicMembers(topic.id) });
  });

  socket.on('leave-topic', () => {
    if (!socket.authenticated) return;
    if (!socket.topicId) return;
    const tid = socket.topicId;
    socket.leave('t:' + tid);
    socket.topicId = null;
    const members = getTopicMembers(tid);
    io.to('t:' + tid).emit('topic-online', { online: members.length, members });
    io.emit('topics', getTopicsPayload());
    io.emit('stats', getStats());
  });

  socket.on('get-topics', () => {
    socket.emit('topics', getTopicsPayload());
  });

  socket.on('topic-lock', () => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    if (!socket.topicId) return;
    const topic = getTopicById.get(socket.topicId);
    if (!topic || !isTopicOwner(topic, ip)) {
      socket.emit('error', 'Only the topic author can lock');
      return;
    }
    // System topics: only moderator (admin panel) can lock
    if (isSystemTopicName(topic.name)) {
      socket.emit('error', 'Only a moderator can lock system topics');
      return;
    }
    setTopicLocked.run(1, 'user', topic.id);
    io.to('t:' + topic.id).emit('topic-state', { id: topic.id, locked: true, lockedBy: 'user' });
    io.emit('topics', getTopicsPayload());
  });

  socket.on('topic-unlock', () => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    if (!socket.topicId) return;
    const topic = getTopicById.get(socket.topicId);
    if (!topic || !isTopicOwner(topic, ip)) {
      socket.emit('error', 'Only the topic author can unlock');
      return;
    }
    // Author cannot unlock a moderator lock
    if (topic.locked && topic.locked_by === 'moderator') {
      socket.emit('error', 'Locked by a moderator — cannot unlock');
      return;
    }
    setTopicLocked.run(0, null, topic.id);
    io.to('t:' + topic.id).emit('topic-state', { id: topic.id, locked: false, lockedBy: null });
    io.emit('topics', getTopicsPayload());
  });

  socket.on('topic-delete', () => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    if (!socket.topicId) return;
    const topic = getTopicById.get(socket.topicId);
    if (!topic || !isTopicOwner(topic, ip)) {
      socket.emit('error', 'Only the topic author can delete');
      return;
    }
    if (isSystemTopicName(topic.name)) {
      socket.emit('error', 'Cannot delete system topic');
      return;
    }
    const tid = topic.id;
    const tname = topic.name;
    deleteMsgsByTopic.run(tid);
    deleteTopicByIdStmt.run(tid);
    io.to('t:' + tid).emit('topic-deleted', { id: tid, name: tname });
    // force leave
    const room = io.sockets.adapter.rooms.get('t:' + tid);
    if (room) {
      for (const sid of [...room]) {
        const s = io.sockets.sockets.get(sid);
        if (s) {
          s.leave('t:' + tid);
          s.topicId = null;
        }
      }
    }
    io.emit('topics', getTopicsPayload());
  });

  // Load older messages (infinite scroll up)
  socket.on('load-history', (payload) => {
    if (!socket.authenticated) return;
    if (!socket.topicId) return;
    const beforeId = payload && payload.beforeId ? parseInt(payload.beforeId, 10) : 0;
    if (!beforeId) return;
    const rows = getHistoryBefore.all(socket.topicId, beforeId, HISTORY_PAGE).reverse();
    socket.emit('history-page', {
      history: rows.map(mapMsgRow),
      hasMore: rows.length >= HISTORY_PAGE
    });
  });

  // Load newer messages (infinite scroll down in jumped history)
  const FORWARD_PAGE = 40;
  socket.on('load-history-after', (payload) => {
    if (!socket.authenticated || !socket.topicId) return;
    const afterId = payload && payload.afterId ? parseInt(payload.afterId, 10) : 0;
    if (!afterId) return;
    const rows = getHistoryAfter.all(socket.topicId, afterId, FORWARD_PAGE);
    socket.emit('history-page-after', {
      history: rows.map(mapMsgRow),
      hasMore: rows.length >= FORWARD_PAGE
    });
  });

  // Jump to a specific message (reply click outside preloaded range)
  socket.on('jump-to-msg', (payload) => {
    if (!socket.authenticated || !socket.topicId) return;
    const targetId = payload && payload.msgId ? parseInt(payload.msgId, 10) : 0;
    if (!targetId) return;
    try {
      // Fetch target message directly
      const targetRow = db.prepare(`
        SELECT id, user_id, user_name, user_color, content, image, reply_name, reply_text, reply_msg_id, created_at, avatar, user_uid, video, audio
        FROM messages WHERE topic_id = ? AND id = ?
      `).get(socket.topicId, targetId);

      if (!targetRow) {
        socket.emit('error', 'Original message no longer exists');
        return;
      }

      // 20 messages before target (exclusive), 20 after target (exclusive)
      const rowsBefore = getHistoryBefore.all(socket.topicId, targetId, 20).reverse();
      const rowsAfter = db.prepare(`
        SELECT id, user_id, user_name, user_color, content, image, reply_name, reply_text, reply_msg_id, created_at, avatar, user_uid, video, audio
        FROM messages WHERE topic_id = ? AND id > ? ORDER BY id ASC LIMIT 20
      `).all(socket.topicId, targetId);

      // Deduplicate and sort chronologically
      const combinedMap = new Map();
      rowsBefore.concat([targetRow], rowsAfter).forEach(r => combinedMap.set(r.id, r));
      const combined = Array.from(combinedMap.values()).sort((a, b) => a.id - b.id);

      socket.emit('history-jump', {
        history: combined.map(mapMsgRow),
        targetMsgId: targetId
      });
    } catch (e) {
      log('jump-to-msg error: ' + e.message);
      socket.emit('error', 'Could not load original message');
    }
  });

  socket.on('message', (payload) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    if (!socket.topicId) return;
    if (!payload || typeof payload !== 'object') return;
    if (!ACTION_LIMIT(socket.clientMac || clientIp, 'message')) {
      socket.emit('error', 'Sending too fast — wait a second');
      return;
    }
    const topicRow = getTopicById.get(socket.topicId);
    if (topicRow && topicRow.locked) {
      socket.emit('error', topicRow.locked_by === 'moderator' ? 'This topic is locked by a moderator' : 'This topic is locked');
      return;
    }

    const clean = sanitize(payload.text || '');
    // Collect up to 5 images (payload.images[] preferred, legacy payload.image ok)
    const rawImgs = [];
    if (Array.isArray(payload.images)) {
      payload.images.forEach(im => { if (typeof im === 'string') rawImgs.push(im); });
    } else if (typeof payload.image === 'string') {
      rawImgs.push(payload.image);
    }
    const imageHashes = Array.isArray(payload.imageHashes) ? payload.imageHashes : [];
    const videoHash = payload.videoHash || null;
    const audioHash = payload.audioHash || null;

    const savedFiles = [];
    let imgIdx = 0;
    for (const im of rawImgs.slice(0, 5)) {
      const f = saveImage(im, imageHashes[imgIdx++] || null);
      if (f) savedFiles.push(f);
    }

    // Video support (1 per message)
    let savedVideo = null;
    if (typeof payload.video === 'string' && payload.video) {
      savedVideo = saveVideo(payload.video, videoHash);
    }

    // Audio support (1 per message)
    let savedAudio = null;
    if (typeof payload.audio === 'string' && payload.audio) {
      savedAudio = saveAudio(payload.audio, audioHash);
    }

    if (!clean && !savedFiles.length && !savedVideo && !savedAudio) return;

    const replyName = payload.replyName ? sanitizeName(payload.replyName) : null;
    const replyText = payload.replyText ? sanitize(payload.replyText).slice(0, 120) : null;
    const replyMsgId = payload.replyMsgId ? parseInt(payload.replyMsgId, 10) || null : null;
    const time = new Date().toISOString();
    const encrypted = clean ? encrypt(clean) : '';
    const avatarFile = socket.profile.avatar
      ? String(socket.profile.avatar).replace(/^\/uploads\//, '')
      : null;
    const imageField = savedFiles.length
      ? (savedFiles.length === 1 ? savedFiles[0] : JSON.stringify(savedFiles))
      : null;

    bytesOut += (Buffer.byteLength(clean) + savedFiles.length * 50000) * Math.max((io.engine.clientsCount || 1) - 1, 0);
    bytesIn += Buffer.byteLength(clean);

    let displayName = sanitizeName(socket.profile.name || '') || 'Anon';
    if (!displayName || displayName.length < 2) {
      displayName = generateName(ip);
      socket.profile.name = displayName;
      db.prepare('UPDATE users SET name = ? WHERE ip = ?').run(displayName, ip);
    }

    const uid = socket.profile.uid || userUid(ip);
    const videoFile = savedVideo || null;
    const audioFile = savedAudio || null;
    const info = insertMsg.run(
      socket.profile.id, displayName, socket.profile.color,
      encrypted, imageField, replyName, replyText, replyMsgId, socket.topicId, time, avatarFile,
      uid, ip, videoFile, audioFile
    );

    const imageUrls = savedFiles.map(f => '/uploads/' + f);
    io.to('t:' + socket.topicId).emit('message', {
      msgId: info.lastInsertRowid,
      tempId: payload.tempId || null,
      id: socket.profile.id,
      name: displayName,
      color: socket.profile.color,
      avatar: socket.profile.avatar || null,
      text: clean,
      image: imageUrls[0] || null,
      images: imageUrls,
      video: savedVideo ? '/uploads/' + savedVideo : null,
      audio: savedAudio ? '/uploads/' + savedAudio : null,
      replyName, replyText, replyMsgId, time,
      uid
    });
    io.emit('stats', getStats());
    io.emit('topics', getTopicsPayload());
  });

  socket.on('user-profile', (payload) => {
    if (!socket.authenticated) return;
    let uid = payload && payload.uid ? String(payload.uid) : (typeof payload === 'string' ? payload : '');
    let u = uid ? getUserByUid.get(uid) : null;
    if (!u && payload && payload.name) {
      u = db.prepare('SELECT ip, name, color, avatar, uid FROM users WHERE name = ? COLLATE NOCASE LIMIT 1').get(payload.name);
    }
    if (!u) {
      socket.emit('user-profile', { uid: uid || 'anon', name: (payload && payload.name) || 'Anon', color: '#666,#999', avatar: null, messages: 0, media: 0, disk: '0 B' });
      return;
    }
    const targetUid = u.uid || uid;
    const msgCount = db.prepare('SELECT COUNT(*) c FROM messages WHERE user_uid = ? OR user_ip = ?').get(targetUid, u.ip).c;
    const mediaCount = db.prepare("SELECT COUNT(*) c FROM messages WHERE (user_uid = ? OR user_ip = ?) AND image IS NOT NULL AND image != ''").get(targetUid, u.ip).c;
    const disk = formatBytes(calcUserDisk(targetUid, u.ip));
    socket.emit('user-profile', {
      uid: targetUid,
      name: u.name || 'Anon',
      color: u.color || '#666,#999',
      avatar: avatarUrl(u.avatar),
      messages: msgCount || 0,
      media: mediaCount || 0,
      disk
    });
  });

  // ---- Explore ----
  socket.on('explore-feed', (payload) => {
    const ip = socket.profile ? socket.profile.ip : clientIp;
    const page = Math.max(1, parseInt(payload && payload.page, 10) || 1);
    const limit = 10;
    const offset = (page - 1) * limit;
    const sort = String((payload && payload.sort) || 'hot').toLowerCase();
    const q = String((payload && payload.q) || '').trim().toLowerCase().slice(0, 80);

    let rows = [];
    let total = 0;

    if (q) {
      const param = '%' + q + '%';
      total = db.prepare('SELECT COUNT(*) c FROM posts WHERE title LIKE ? OR body LIKE ?').get(param, param).c;
      if (sort === 'latest') {
        rows = db.prepare('SELECT * FROM posts WHERE title LIKE ? OR body LIKE ? ORDER BY id DESC LIMIT ? OFFSET ?').all(param, param, limit, offset);
      } else if (sort === 'oldest') {
        rows = db.prepare('SELECT * FROM posts WHERE title LIKE ? OR body LIKE ? ORDER BY id ASC LIMIT ? OFFSET ?').all(param, param, limit, offset);
      } else {
        rows = db.prepare('SELECT * FROM posts WHERE title LIKE ? OR body LIKE ? ORDER BY (COALESCE(upvotes,0) - COALESCE(downvotes,0) + COALESCE(comments_count,0)*2 + COALESCE(views,0)) DESC, id DESC LIMIT ? OFFSET ?').all(param, param, limit, offset);
      }
    } else {
      total = db.prepare('SELECT COUNT(*) c FROM posts').get().c;
      if (sort === 'latest') {
        rows = db.prepare('SELECT * FROM posts ORDER BY id DESC LIMIT ? OFFSET ?').all(limit, offset);
      } else if (sort === 'oldest') {
        rows = db.prepare('SELECT * FROM posts ORDER BY id ASC LIMIT ? OFFSET ?').all(limit, offset);
      } else {
        rows = db.prepare('SELECT * FROM posts ORDER BY (COALESCE(upvotes,0) - COALESCE(downvotes,0) + COALESCE(comments_count,0)*2 + COALESCE(views,0)) DESC, id DESC LIMIT ? OFFSET ?').all(limit, offset);
      }
    }

    const totalPages = Math.max(1, Math.ceil(total / limit));
    socket.emit('explore-feed', {
      page,
      limit,
      total,
      totalPages,
      hasMore: offset + rows.length < total,
      posts: rows.map(r => mapPost(r, ip))
    });
  });

  socket.on('explore-create', (payload) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    if (!payload || typeof payload !== 'object') return;
    if (!ACTION_LIMIT(socket.clientMac || clientIp, 'post')) {
      socket.emit('error', 'Posting too fast — slow down');
      return;
    }
    const title = sanitize(payload.title || '', 120);
    const body = sanitize(payload.body || '', 8000);
    if (!title || title.length < 2) {
      socket.emit('error', 'Title required (min 2 chars)');
      return;
    }

    // Daily post limit
    const dayStart = new Date();
    dayStart.setHours(0, 0, 0, 0);
    const dayIso = dayStart.toISOString();
    const todayCount = db.prepare('SELECT COUNT(*) c FROM posts WHERE ip = ? AND created_at >= ?').get(ip, dayIso).c;
    if (todayCount >= MAX_POSTS_PER_DAY) {
      socket.emit('error', 'Maximum ' + MAX_POSTS_PER_DAY + ' posts per day reached');
      return;
    }

    // Identical post within 24h (same title + body by same IP)
    const dupWindow = new Date(Date.now() - DUPLICATE_POST_HOURS * 60 * 60 * 1000).toISOString();
    const dup = db.prepare(
      'SELECT id FROM posts WHERE ip = ? AND title = ? AND body = ? AND created_at >= ? LIMIT 1'
    ).get(ip, title, body, dupWindow);
    if (dup) {
      socket.emit('error', 'Identical post already exists within 24 hours');
      return;
    }

    let tags = [];
    if (Array.isArray(payload.tags)) {
      tags = payload.tags.map(t => sanitize(String(t)).slice(0, 24)).filter(t => t.length >= 1).slice(0, 8);
    } else if (typeof payload.tags === 'string') {
      tags = payload.tags.split(/[,#]+/).map(t => sanitize(t).slice(0, 24)).filter(Boolean).slice(0, 8);
    }
    let videoFile = null;
    if (typeof payload.video === 'string' && payload.video) {
      // Stricter video daily limit (disk)
      const vidCount = db.prepare(
        "SELECT COUNT(*) c FROM posts WHERE ip = ? AND created_at >= ? AND video IS NOT NULL AND video != ''"
      ).get(ip, dayIso).c;
      if (vidCount >= MAX_VIDEOS_PER_DAY) {
        socket.emit('error', 'Maximum ' + MAX_VIDEOS_PER_DAY + ' videos per day (disk protection)');
        return;
      }
      videoFile = savePostVideo(payload.video, payload.videoHash || null);
      if (!videoFile) {
        socket.emit('error', 'Video invalid or too large (max 50MB, mp4/webm/mov)');
        return;
      }
    }
    let audioFile = null;
    if (typeof payload.audio === 'string' && payload.audio) {
      audioFile = savePostAudio(payload.audio, payload.audioHash || null);
      if (!audioFile) {
        socket.emit('error', 'Audio invalid or too large (max 50MB, mp3/ogg/wav)');
        return;
      }
    }
    const rawImgs = (videoFile || audioFile) ? [] : (Array.isArray(payload.images) ? payload.images : []);
    const imageHashes = Array.isArray(payload.imageHashes) ? payload.imageHashes : [];
    const saved = [];
    let postImgIdx = 0;
    for (const im of rawImgs.slice(0, 5)) {
      if (typeof im === 'string') {
        const f = savePostImage(im, imageHashes[postImgIdx++] || null);
        if (f) saved.push(f);
      }
    }
    const avatarFile = socket.profile.avatar
      ? String(socket.profile.avatar).replace(/^\/uploads\//, '')
      : null;
    const time = new Date().toISOString();
    try {
      const info = insertPost.run(
        socket.profile.id,
        socket.profile.name || 'Anon',
        socket.profile.color,
        avatarFile,
        title,
        body,
        JSON.stringify(tags),
        JSON.stringify(saved),
        videoFile,
        audioFile,
        ip,
        time
      );
      const row = getPost.get(info.lastInsertRowid);
      const post = mapPost(row, ip);
      io.emit('explore-post', post);
      socket.emit('explore-created', post);
    } catch (e) {
      log('explore-create error: ' + e.message);
      socket.emit('error', 'Failed to create post');
    }
  });

  // legacy explore-like removed — client uses explore-vote directly

  socket.on('explore-vote', (payload) => {
    if (!socket.authenticated) return;
    const ip = (socket.profile && socket.profile.ip) || clientIp;
    const id = parseInt(payload && payload.postId, 10);
    const vote = parseInt(payload && payload.vote, 10);
    if (!id || ![1, -1, 0].includes(vote)) return;
    const row = getPost.get(id);
    if (!row) return;
    const existing = getVote.get(id, ip);
    const cur = existing ? existing.vote : 0;
    let nextVote = vote;
    if (vote === 0 || vote === cur) {
      removeVote.run(id, ip);
      nextVote = 0;
    } else {
      upsertVote.run(id, ip, vote, new Date().toISOString());
      nextVote = vote;
    }
    recountVotes.run(id, id, id);
    const updated = getPost.get(id);
    const up = (updated && updated.upvotes) || 0;
    const down = (updated && updated.downvotes) || 0;
    io.emit('explore-vote-update', { id, upvotes: up, downvotes: down, score: up - down });
    socket.emit('explore-vote-me', { id, upvotes: up, downvotes: down, score: up - down, myVote: nextVote });
  });

  socket.on('explore-view', (postId) => {
    if (!socket.authenticated) return;
    const id = parseInt(postId, 10);
    if (!id) return;
    const row = getPost.get(id);
    if (!row) return;
    bumpViews.run(id);
    const updated = getPost.get(id);
    const views = (updated && updated.views) || 0;
    io.emit('explore-view-update', { id, views });
  });

  socket.on('explore-get', (postId) => {
    const ip = socket.profile ? socket.profile.ip : clientIp;
    const id = parseInt(postId, 10);
    if (!id) return;
    const row = getPost.get(id);
    if (!row) { socket.emit('error', 'Post not found'); return; }
    socket.emit('explore-get', mapPost(row, ip));
  });

  socket.on('explore-share', (postId) => {
    if (!socket.authenticated) return;
    const id = parseInt(postId, 10);
    if (!id) return;
    const row = getPost.get(id);
    if (!row) return;
    bumpShares.run(id);
    const updated = getPost.get(id);
    io.emit('explore-share-update', { id, shares: updated.shares_count || 0 });
  });

  socket.on('explore-comment', (payload) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    if (!payload || typeof payload !== 'object') return;
    if (!ACTION_LIMIT(socket.clientMac || clientIp, 'comment')) {
      socket.emit('error', 'Commenting too fast');
      return;
    }
    const id = parseInt(payload.postId, 10);
    const body = sanitize(payload.body || '').slice(0, 1000);

    const rawImgs = [];
    if (Array.isArray(payload.images)) {
      payload.images.forEach(im => { if (typeof im === 'string') rawImgs.push(im); });
    } else if (typeof payload.image === 'string' && payload.image) {
      rawImgs.push(payload.image);
    }
    const imageHashes = Array.isArray(payload.imageHashes) ? payload.imageHashes : (payload.imageHash ? [payload.imageHash] : []);

    const savedFiles = [];
    let imgIdx = 0;
    for (const im of rawImgs.slice(0, 5)) {
      const f = saveImage(im, imageHashes[imgIdx++] || null);
      if (f) savedFiles.push(f);
    }

    if (!id || (!body && !savedFiles.length)) return;
    const row = getPost.get(id);
    if (!row) return;
    const parentId = payload.parentId ? parseInt(payload.parentId, 10) || null : null;
    const replyName = payload.replyName ? sanitizeName(payload.replyName) : null;
    const replyText = payload.replyText ? sanitize(payload.replyText).slice(0, 120) : null;
    const avatarFile = socket.profile.avatar
      ? String(socket.profile.avatar).replace(/^\/uploads\//, '')
      : null;
    const time = new Date().toISOString();
    const imageField = savedFiles.length
      ? (savedFiles.length === 1 ? savedFiles[0] : JSON.stringify(savedFiles))
      : null;

    const info = insertComment.run(
      id,
      socket.profile.id,
      socket.profile.name || 'Anon',
      socket.profile.color,
      avatarFile,
      body,
      ip,
      time,
      parentId,
      replyName,
      replyText,
      imageField
    );
    bumpComments.run(id, id);
    const updated = getPost.get(id);
    const imageUrls = savedFiles.map(f => '/uploads/' + f);
    const comment = {
      id: info.lastInsertRowid,
      postId: id,
      userId: socket.profile.id,
      name: socket.profile.name || 'Anon',
      color: socket.profile.color,
      avatar: socket.profile.avatar || null,
      body,
      image: imageUrls[0] || null,
      images: imageUrls,
      time,
      parentId,
      replyName,
      replyText
    };
    io.emit('explore-comment', comment);
    io.emit('explore-comment-count', { id, comments: updated.comments_count || 0 });
  });

  socket.on('explore-comments', (postId) => {
    const id = parseInt(postId, 10);
    if (!id) return;
    const rows = listComments.all(id);
    socket.emit('explore-comments', {
      postId: id,
      comments: rows.map(r => {
        const imgs = parseJsonArr(r.image, 5).map(f =>
          String(f).startsWith('/') || String(f).startsWith('http') ? f : '/uploads/' + f
        );
        if (!imgs.length && r.image) {
          imgs.push(String(r.image).startsWith('/') || String(r.image).startsWith('http') ? r.image : '/uploads/' + r.image);
        }
        return {
          id: r.id,
          postId: r.post_id,
          userId: r.user_id || '',
          name: r.user_name || 'Anon',
          color: r.user_color || '#666,#999',
          avatar: r.avatar
            ? (String(r.avatar).startsWith('/') || String(r.avatar).startsWith('http')
                ? r.avatar : '/uploads/' + r.avatar)
            : null,
          body: r.body || '',
          image: imgs[0] || null,
          images: imgs,
          time: r.created_at || '',
          parentId: r.parent_id || null,
          replyName: r.reply_name || null,
          replyText: r.reply_text || null
        };
      })
    });
  });

  socket.on('explore-delete', (postId) => {
    if (!socket.authenticated) return;
    const ip = socket.profile.ip;
    const id = parseInt(postId, 10);
    if (!id) return;
    const post = getPost.get(id);
    if (!post) {
      socket.emit('error', 'Post not found');
      return;
    }
    // Only owner (same IP) can delete
    const info = deletePostStmt.run(id, ip);
    if (info.changes) {
      deletePostMedia(post); // also remove files from disk
      try { deletePostLikes.run(id); } catch {}
      try { deletePostVotes.run(id); } catch {}
      deletePostComments.run(id);
      io.emit('explore-deleted', { id });
    } else {
      socket.emit('error', 'Cannot delete this post');
    }
  });

  socket.on('disconnect', () => {
    console.log('User disconnected:', socket.profile ? socket.profile.name : 'Unknown');
    if (socket.topicId) {
      const tid = socket.topicId;
      // leave happens automatically; size after disconnect is updated after this handler
      setTimeout(() => {
        const members = getTopicMembers(tid);
        io.to('t:' + tid).emit('topic-online', { online: members.length, members });
        io.emit('topics', getTopicsPayload());
      }, 50);
    }
    io.emit('stats', getStats());
    io.emit('topics', getTopicsPayload());
  });
});

setInterval(() => io.emit('stats', getStats()), 2000);

function adminDeletePost(id) {
  const post = getPost.get(id);
  if (!post) return { ok: false, msg: 'Post id ' + id + ' not found' };
  const info = deletePostByIdStmt.run(id);
  if (info.changes) {
    const files = deletePostMedia(post); // remove media from disk
    try { deletePostLikes.run(id); } catch {}
    try { deletePostVotes.run(id); } catch {}
    deletePostComments.run(id);
    io.emit('explore-deleted', { id });
    return { ok: true, msg: 'Deleted post #' + id + ' "' + (post.title || '') + '" (' + files + ' file(s) removed from disk)' };
  }
  return { ok: false, msg: 'Could not delete post #' + id };
}

/** Delete posts by ID range inclusive, e.g. 11-15. Also deletes media from disk. */
function adminDeletePostRange(rangeStr) {
  if (!/^\d+-\d+$/.test(rangeStr || '')) {
    return { ok: false, msg: 'Invalid range. Example: !deleteallpost 11-15' };
  }
  const [start, end] = rangeStr.split('-').map(Number);
  if (start > end) return { ok: false, msg: 'Start must be <= end' };
  const rows = db.prepare('SELECT * FROM posts WHERE id >= ? AND id <= ?').all(start, end);
  let postsDeleted = 0;
  let filesDeleted = 0;
  rows.forEach((post) => {
    const info = deletePostByIdStmt.run(post.id);
    if (info.changes) {
      postsDeleted++;
      filesDeleted += deletePostMedia(post);
      try { deletePostLikes.run(post.id); } catch {}
      try { deletePostVotes.run(post.id); } catch {}
      deletePostComments.run(post.id);
      io.emit('explore-deleted', { id: post.id });
    }
  });
  return {
    ok: true,
    msg: 'Deleted ' + postsDeleted + ' post(s) from #' + start + ' to #' + end + ' (' + filesDeleted + ' file(s) from disk)'
  };
}

function adminDeleteTopic(id) {
  const topic = getTopicById.get(id);
  if (!topic) return { ok: false, msg: 'Topic id ' + id + ' not found' };
  if (isSystemTopicName(topic.name)) return { ok: false, msg: 'Cannot delete system topic' };
  const tid = topic.id;
  const tname = topic.name;
  deleteMsgsByTopic.run(tid);
  deleteTopicByIdStmt.run(tid);
  io.to('t:' + tid).emit('topic-deleted', { id: tid, name: tname });
  const room = io.sockets.adapter.rooms.get('t:' + tid);
  if (room) {
    for (const sid of [...room]) {
      const s = io.sockets.sockets.get(sid);
      if (s) {
        s.leave('t:' + tid);
        s.topicId = null;
      }
    }
  }
  io.emit('topics', getTopicsPayload());
  return { ok: true, msg: 'Deleted topic #' + tid + ' "' + tname + '"' };
}

function adminDeleteAllTopics() {
  const rows = listTopics.all().filter(t => t.name !== 'General');
  let n = 0;
  rows.forEach(t => {
    const r = adminDeleteTopic(t.id);
    if (r.ok) n++;
  });
  return { ok: true, msg: 'Deleted ' + n + ' topics (General kept)' };
}

function startAdminPanel() {
  const readline = require('readline');
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout, prompt: 'admin> ' });
  console.log('Admin panel ready. Commands: !help | !topics | !deletealltopic [id] | !posts | !deletepost <id> | !deleteallpost <range>');
  rl.prompt();
  rl.on('line', (line) => {
    const raw = String(line || '').trim();
    if (!raw) { rl.prompt(); return; }
    const parts = raw.split(/\s+/);
    const cmd = parts[0].toLowerCase();
    try {
      if (cmd === '!help' || cmd === 'help') {
        console.log('  !topics                 List topics (id, name, msgs, locked)');
        console.log('  !deletealltopic         Delete ALL topics except General');
        console.log('  !deletealltopic <id>    Delete one topic by id (not General)');
        console.log('  !lock <id>              Lock topic');
        console.log('  !unlock <id>            Unlock topic');
        console.log('  !recommendatopic <id>   Recommend topic (moderator)');
        console.log('  !unrecommendatopic <id> Remove recommendation');
        console.log('  !posts                  List Explore posts (id, title, likes, comments)');
        console.log('  !deletepost <id>        Delete an Explore post by id (also deletes media from disk)');
        console.log('  !deleteallpost <range>  Delete posts by ID range, e.g. !deleteallpost 11-15 (also deletes media)');
        console.log('  !addkey <key>           Creates a new key entry');
        console.log('  !removekey <key>        Removes key and disconnects users');
        console.log('  !keys                   List all keys');
        console.log('  !recoverkey <recovery>  Recover login key using a recovery key');
      } else if (cmd === '!addkey') {
        const key = parts[1];
        if (!key) console.log('  Usage: !addkey <key>');
        else {
          const keysData = loadKeys();
          if (keysData.keys[key]) {
            console.log('  Error: Key already exists');
          } else {
            const recovery = generateRecoveryKey();
            const encryptedLogin = encryptRecovery(recovery, key);
            const userIp = 'key-' + crypto.randomBytes(8).toString('hex');
            ensureUser(userIp);
            keysData.keys[key] = { mac: null, userIp, recovery: encryptedLogin, createdAt: new Date().toISOString() };
            saveKeys(keysData);
            console.log('  Added key: ' + key + ' (userIp: ' + userIp + ')');
            console.log('  Recovery key: ' + recovery);
            log('Admin added key: ' + key + ' userIp=' + userIp);
          }
        }
      } else if (cmd === '!removekey') {
        const key = parts[1];
        if (!key) console.log('  Usage: !removekey <key>');
        else {
          const keysData = loadKeys();
          if (!keysData.keys[key]) {
            console.log('  Error: Key not found');
          } else {
            delete keysData.keys[key];
            saveKeys(keysData);
            let dc = 0;
            for (const [sid, s] of io.sockets.sockets) {
              if (s.authKey === key) {
                s.emit('force-logout', { reason: 'Key removed by admin' });
                s.disconnect(true);
                dc++;
              }
            }
            console.log('  Removed key: ' + key + ' (disconnected ' + dc + ' sockets)');
          }
        }
      } else if (cmd === '!keys') {
        const keysData = loadKeys();
        const ks = Object.keys(keysData.keys);
        if (!ks.length) console.log('  No keys');
        ks.forEach(k => {
          const v = keysData.keys[k];
          const bound = Array.isArray(v.macs) ? (v.macs.length ? v.macs.join(', ') : 'none') : (v.mac || 'none');
          console.log('  ' + k + '  boundMacs=[' + bound + ']  userIp=' + v.userIp + (v.recovery ? '  [has recovery]' : ''));
        });
      } else if (cmd === '!recoverkey') {
        const recovery = parts[1];
        if (!recovery) console.log('  Usage: !recoverkey <recovery-key>');
        else {
          const keysData = loadKeys();
          let found = false;
          for (const [loginKey, entry] of Object.entries(keysData.keys)) {
            if (entry.recovery) {
              const decrypted = decryptRecovery(recovery, entry.recovery);
              if (decrypted === loginKey) {
                console.log('  Login key: ' + loginKey);
                console.log('  userIp: ' + entry.userIp);
                found = true;
                break;
              }
            }
          }
          if (!found) console.log('  Invalid recovery key');
        }
      } else if (cmd === '!posts') {
        const rows = listPosts.all(200, 0);
        if (!rows.length) console.log('  No posts');
        rows.forEach(p => {
          console.log(
            '  #' + p.id +
            '  ' + (p.title || '(no title)') +
            '  likes=' + (p.likes_count || 0) +
            '  comments=' + (p.comments_count || 0)
          );
        });
      } else if (cmd === '!deletepost') {
        const id = parseInt(parts[1], 10);
        if (!id) console.log('  Usage: !deletepost <id>');
        else console.log('  ' + adminDeletePost(id).msg);
      } else if (cmd === '!deleteallpost') {
        const range = parts[1];
        if (!range) console.log('  Usage: !deleteallpost 11-15');
        else console.log('  ' + adminDeletePostRange(range).msg);
      } else if (cmd === '!topics') {
        getTopicsPayload().forEach(t => {
          console.log(
            '  #' + t.id +
            (t.isGeneral ? ' [SYSTEM]' : '') +
            (t.locked ? (t.lockedBy === 'moderator' ? ' [LOCKED:moderator]' : ' [LOCKED:user]') : '') +
            (t.recommended ? ' [RECOMMENDED]' : '') +
            '  ' + t.name +
            '  msgs=' + t.msgCount +
            '  online=' + t.online
          );
        });
      } else if (cmd === '!deletealltopic') {
        if (parts[1]) {
          const id = parseInt(parts[1], 10);
          if (!id) console.log('  Invalid id');
          else console.log('  ' + adminDeleteTopic(id).msg);
        } else {
          console.log('  ' + adminDeleteAllTopics().msg);
        }
      } else if (cmd === '!recommendatopic' || cmd === '!unrecommendatopic') {
        const id = parseInt(parts[1], 10);
        if (!id) console.log('  Usage: ' + cmd + ' <id>');
        else {
          const t = getTopicById.get(id);
          if (!t) console.log('  Not found');
          else {
            const on = cmd === '!recommendatopic' ? 1 : 0;
            setTopicRecommended.run(on, id);
            io.emit('topics', getTopicsPayload());
            console.log('  Topic #' + id + (on ? ' recommended by moderator' : ' recommendation removed'));
          }
        }
      } else if (cmd === '!lock' || cmd === '!unlock') {
        const id = parseInt(parts[1], 10);
        if (!id) console.log('  Usage: ' + cmd + ' <id>');
        else {
          const t = getTopicById.get(id);
          if (!t) console.log('  Not found');
          else {
            const locked = cmd === '!lock' ? 1 : 0;
            const by = locked ? 'moderator' : null;
            setTopicLocked.run(locked, by, id);
            io.to('t:' + id).emit('topic-state', { id, locked: !!locked, lockedBy: by });
            io.emit('topics', getTopicsPayload());
            console.log('  Topic #' + id + (locked ? ' locked by moderator' : ' unlocked') + (isSystemTopicName(t.name) ? ' [SYSTEM]' : ''));
          }
        }
      } else {
        console.log('  Unknown command. Type !help');
      }
    } catch (e) {
      console.log('  Error:', e.message || e);
    }
    rl.prompt();
  });
}

const PORT = 25222;
server.keepAliveTimeout = 65000;
server.headersTimeout = 66000;
server.requestTimeout = 0; // 0 = no timeout for active video/audio streams
server.listen(PORT, () => {
  log('Anonymous Chat running at http://localhost:' + PORT);
  log('Log file: ' + _logFile);
  startAdminPanel();
});