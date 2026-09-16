import http from 'node:http';
import { createHash, randomBytes, randomInt, randomUUID } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import QRCode from 'qrcode';

const host = process.env.HOST || '127.0.0.1';
const port = Number(process.env.PORT || 4173);
const publicRoot = resolve('dist');
const dataRoot = resolve(process.env.DATA_DIR || 'data');
const devicesFile = join(dataRoot, 'authorized-devices.json');
const enrollmentTtlMs = 10 * 60 * 1000;
const enrollments = new Map();
let devices = [];

const mimeTypes = {
  '.css': 'text/css; charset=utf-8', '.html': 'text/html; charset=utf-8', '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8', '.png': 'image/png',
  '.svg': 'image/svg+xml', '.webp': 'image/webp'
};

const hash = value => createHash('sha256').update(value).digest('hex');
const publicDevice = device => ({ id: device.id, name: device.name, authorizedAt: device.authorizedAt, lastSeenAt: device.lastSeenAt || null });

async function loadDevices() {
  await mkdir(dataRoot, { recursive: true });
  try { devices = JSON.parse(await readFile(devicesFile, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
}

async function saveDevices() {
  const temporary = `${devicesFile}.tmp`;
  await writeFile(temporary, JSON.stringify(devices, null, 2));
  await rename(temporary, devicesFile);
}

function cookies(request) {
  return Object.fromEntries((request.headers.cookie || '').split(';').map(item => item.trim().split('=').map(decodeURIComponent)).filter(parts => parts.length === 2));
}

function secureCookie(request) {
  return request.socket.encrypted || request.headers['x-forwarded-proto'] === 'https' ? '; Secure' : '';
}

function sendJson(response, status, payload, headers = {}) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers });
  response.end(JSON.stringify(payload));
}

async function readJson(request) {
  let body = '';
  for await (const chunk of request) {
    body += chunk;
    if (body.length > 20_000) throw new Error('Request too large');
  }
  return JSON.parse(body || '{}');
}

function purgeExpiredEnrollments() {
  const now = Date.now();
  for (const [id, enrollment] of enrollments) if (enrollment.expiresAt < now) enrollments.delete(id);
}

async function createEnrollment(request, response) {
  purgeExpiredEnrollments();
  let code;
  do { code = String(randomInt(0, 1_000_000)).padStart(6, '0'); }
  while ([...enrollments.values()].some(item => item.codeHash === hash(code)));
  const id = randomUUID();
  const pendingToken = randomBytes(24).toString('base64url');
  const expiresAt = Date.now() + enrollmentTtlMs;
  enrollments.set(id, { id, codeHash: hash(code), pendingHash: hash(pendingToken), expiresAt, status: 'pending' });
  const qrDataUrl = await QRCode.toDataURL(`AROGSEVAK-AUTH:${code}`, { width: 360, margin: 1, color: { dark: '#102b27', light: '#ffffff' } });
  sendJson(response, 201, { id, code, qrDataUrl, expiresAt }, {
    'Set-Cookie': `arog_enrollment=${encodeURIComponent(pendingToken)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=600${secureCookie(request)}`
  });
}

function enrollmentStatus(request, response, id) {
  purgeExpiredEnrollments();
  const enrollment = enrollments.get(id);
  const pendingToken = cookies(request).arog_enrollment;
  if (!enrollment || !pendingToken || hash(pendingToken) !== enrollment.pendingHash) return sendJson(response, 404, { status: 'expired' });
  if (enrollment.status !== 'authorized') return sendJson(response, 200, { status: 'pending', expiresAt: enrollment.expiresAt });
  const headers = { 'Set-Cookie': `arog_device=${encodeURIComponent(enrollment.deviceToken)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=31536000${secureCookie(request)}` };
  sendJson(response, 200, { status: 'authorized', device: publicDevice(enrollment.device) }, headers);
  enrollments.delete(id);
}

async function authorizeDevice(request, response) {
  purgeExpiredEnrollments();
  const body = await readJson(request);
  const name = String(body.name || '').trim().slice(0, 60);
  const code = String(body.code || '').trim();
  if (!name || !/^\d{6}$/.test(code)) return sendJson(response, 400, { error: 'Device name and a valid 6-digit code are required' });
  const enrollment = [...enrollments.values()].find(item => item.status === 'pending' && item.codeHash === hash(code));
  if (!enrollment) return sendJson(response, 404, { error: 'Code is invalid or expired' });
  const deviceToken = randomBytes(32).toString('base64url');
  const device = { id: randomUUID(), name, tokenHash: hash(deviceToken), authorizedAt: new Date().toISOString(), lastSeenAt: null };
  devices.push(device);
  await saveDevices();
  Object.assign(enrollment, { status: 'authorized', device, deviceToken });
  sendJson(response, 201, { device: publicDevice(device) });
}

async function listDevices(_request, response) {
  sendJson(response, 200, { devices: devices.map(publicDevice).sort((a, b) => b.authorizedAt.localeCompare(a.authorizedAt)) });
}

async function deviceSession(request, response) {
  const token = cookies(request).arog_device;
  const device = token && devices.find(item => item.tokenHash === hash(token));
  if (!device) return sendJson(response, 200, { authorized: false });
  device.lastSeenAt = new Date().toISOString();
  void saveDevices();
  sendJson(response, 200, { authorized: true, device: publicDevice(device), allowedRole: 'patient' });
}

async function serveStatic(request, response) {
  const requestUrl = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
  let pathname = decodeURIComponent(requestUrl.pathname).replace(/^\/+/, '');
  if (!pathname) pathname = 'index.html';
  const filePath = normalize(join(publicRoot, pathname));
  if (!filePath.startsWith(publicRoot)) return sendJson(response, 403, { error: 'Forbidden' });
  try {
    const info = await stat(filePath);
    const finalPath = info.isDirectory() ? join(filePath, 'index.html') : filePath;
    const body = await readFile(finalPath);
    response.writeHead(200, { 'Content-Type': mimeTypes[extname(finalPath)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    if (request.method === 'HEAD') response.end(); else response.end(body);
  } catch {
    sendJson(response, 404, { error: 'Not found' });
  }
}

await loadDevices();
const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
    if (request.method === 'POST' && url.pathname === '/api/device-enrollments') return await createEnrollment(request, response);
    const statusMatch = url.pathname.match(/^\/api\/device-enrollments\/([0-9a-f-]+)\/status$/i);
    if (request.method === 'GET' && statusMatch) return enrollmentStatus(request, response, statusMatch[1]);
    if (request.method === 'POST' && url.pathname === '/api/device-authorizations') return await authorizeDevice(request, response);
    if (request.method === 'GET' && url.pathname === '/api/devices') return await listDevices(request, response);
    if (request.method === 'GET' && url.pathname === '/api/device-session') return await deviceSession(request, response);
    if (request.method === 'GET' || request.method === 'HEAD') return await serveStatic(request, response);
    sendJson(response, 405, { error: 'Method not allowed' });
  } catch (error) {
    console.error(error);
    sendJson(response, 500, { error: 'Server error' });
  }
});

server.listen(port, host, () => console.log(`ArogSevak running at http://${host}:${port}`));
