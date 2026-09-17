import http from 'node:http';
import { createHash, randomBytes, randomInt, randomUUID, scryptSync } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import QRCode from 'qrcode';

const host = process.env.HOST || '127.0.0.1';
const port = Number(process.env.PORT || 4173);
const publicRoot = resolve('dist');
const dataRoot = resolve(process.env.DATA_DIR || 'data');
const devicesFile = join(dataRoot, 'authorized-devices.json');
const patientsFile = join(dataRoot, 'patients.json');
const enrollmentTtlMs = 10 * 60 * 1000;
const signupOtpTtlMs = 5 * 60 * 1000;
const signupOtpCooldownMs = 30 * 1000;
const enrollments = new Map();
const signupOtps = new Map();
const signupOtpLastSent = new Map();
let devices = [];
let patients = [];

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

async function loadPatients() {
  await mkdir(dataRoot, { recursive: true });
  try { patients = JSON.parse(await readFile(patientsFile, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
}

async function saveDevices() {
  const temporary = `${devicesFile}.tmp`;
  await writeFile(temporary, JSON.stringify(devices, null, 2));
  await rename(temporary, devicesFile);
}

async function savePatients() {
  const temporary = `${patientsFile}.tmp`;
  await writeFile(temporary, JSON.stringify(patients, null, 2), { mode: 0o600 });
  await rename(temporary, patientsFile);
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

function normalizeIndianPhone(value) {
  const digits = String(value || '').replace(/\D/g, '');
  const national = digits.length === 12 && digits.startsWith('91') ? digits.slice(2) : digits;
  return /^[6-9]\d{9}$/.test(national) ? national : null;
}

function purgeExpiredSignupOtps() {
  const now = Date.now();
  for (const [id, entry] of signupOtps) if (entry.expiresAt < now || entry.consumed) signupOtps.delete(id);
  for (const [phone, sentAt] of signupOtpLastSent) if (now - sentAt > signupOtpCooldownMs) signupOtpLastSent.delete(phone);
}

async function deliverSignupOtp(phone, otp) {
  const accountSid = process.env.TWILIO_ACCOUNT_SID;
  const authToken = process.env.TWILIO_AUTH_TOKEN;
  const fromNumber = process.env.TWILIO_FROM_NUMBER;
  const demoMode = process.env.OTP_DEMO_MODE === '1' || process.env.NODE_ENV !== 'production';
  if (!accountSid || !authToken || !fromNumber) {
    if (demoMode) return { mode: 'demo' };
    throw new Error('SMS provider is not configured');
  }
  const body = new URLSearchParams({
    To: `+91${phone}`,
    From: fromNumber,
    Body: `Your ArogSevak verification code is ${otp}. It expires in 5 minutes.`
  });
  const result = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(accountSid)}/Messages.json`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${Buffer.from(`${accountSid}:${authToken}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
    },
    body
  });
  if (!result.ok) throw new Error(`SMS provider rejected the request (${result.status})`);
  return { mode: 'sms' };
}

async function requestSignupOtp(request, response) {
  purgeExpiredSignupOtps();
  const body = await readJson(request);
  const phone = normalizeIndianPhone(body.phone);
  if (!phone) return sendJson(response, 400, { error: 'Enter a valid 10-digit Indian mobile number' });
  const lastSent = signupOtpLastSent.get(phone) || 0;
  const retryAfter = Math.ceil((signupOtpCooldownMs - (Date.now() - lastSent)) / 1000);
  if (retryAfter > 0) return sendJson(response, 429, { error: `Please wait ${retryAfter}s before requesting another OTP`, retryAfter });
  const otp = String(randomInt(0, 1_000_000)).padStart(6, '0');
  const id = randomUUID();
  const expiresAt = Date.now() + signupOtpTtlMs;
  const delivery = await deliverSignupOtp(phone, otp);
  signupOtps.set(id, { id, phone, otpHash: hash(otp), expiresAt, attempts: 0, verifiedTokenHash: null, consumed: false });
  signupOtpLastSent.set(phone, Date.now());
  sendJson(response, 201, { id, expiresAt, delivery: delivery.mode, ...(delivery.mode === 'demo' ? { demoOtp: otp } : {}) });
}

async function verifySignupOtp(request, response) {
  purgeExpiredSignupOtps();
  const body = await readJson(request);
  const phone = normalizeIndianPhone(body.phone);
  const entry = signupOtps.get(String(body.id || ''));
  const otp = String(body.otp || '').trim();
  if (!entry || !phone || entry.phone !== phone) return sendJson(response, 404, { error: 'OTP request is invalid or expired' });
  if (entry.attempts >= 5) return sendJson(response, 429, { error: 'Too many incorrect attempts. Request a new OTP.' });
  entry.attempts += 1;
  if (!/^\d{6}$/.test(otp) || hash(otp) !== entry.otpHash) return sendJson(response, 400, { error: 'Incorrect OTP' });
  const verificationToken = randomBytes(32).toString('base64url');
  entry.verifiedTokenHash = hash(verificationToken);
  entry.expiresAt = Date.now() + 30 * 60 * 1000;
  sendJson(response, 200, { verified: true, verificationToken });
}

async function confirmSignupOtp(request, response) {
  purgeExpiredSignupOtps();
  const body = await readJson(request);
  const phone = normalizeIndianPhone(body.phone);
  const entry = signupOtps.get(String(body.id || ''));
  const token = String(body.verificationToken || '');
  if (!entry || !phone || entry.phone !== phone || !entry.verifiedTokenHash || hash(token) !== entry.verifiedTokenHash) {
    return sendJson(response, 401, { error: 'Phone verification is required' });
  }
  entry.consumed = true;
  sendJson(response, 200, { verified: true });
}

const allowedBloodGroups = new Set(['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-', 'Unknown']);
const allowedGenders = new Set(['Female', 'Male', 'Non-binary', 'Prefer not to say']);
const allowedConditions = new Set([
  'heart_disease', 'hypertension', 'diabetes', 'asthma', 'thyroid',
  'kidney_disease', 'liver_disease', 'arthritis', 'none'
]);

async function createPatientRegistration(request, response) {
  purgeExpiredSignupOtps();
  const body = await readJson(request);
  const phone = normalizeIndianPhone(body.phone);
  const otpEntry = signupOtps.get(String(body.otpRequestId || ''));
  const verificationToken = String(body.otpVerificationToken || '');
  if (!otpEntry || !phone || otpEntry.phone !== phone || !otpEntry.verifiedTokenHash || hash(verificationToken) !== otpEntry.verifiedTokenHash) {
    return sendJson(response, 401, { error: 'Phone verification is required or has expired' });
  }
  if (patients.some(patient => patient.phone === phone)) return sendJson(response, 409, { error: 'An account already exists for this phone number' });

  const identityMethod = body.identityMethod === 'abha' ? 'abha' : body.identityMethod === 'aadhaar' ? 'aadhaar' : null;
  const identityNumber = String(body.identityNumber || '').replace(/\D/g, '');
  const identityValid = identityMethod === 'abha' ? /^\d{14}$/.test(identityNumber) : identityMethod === 'aadhaar' && /^\d{12}$/.test(identityNumber);
  const fullName = String(body.fullName || '').trim().replace(/\s+/g, ' ').slice(0, 80);
  const dateOfBirth = String(body.dateOfBirth || '');
  const date = new Date(`${dateOfBirth}T00:00:00Z`);
  const earliestBirthDate = new Date();
  earliestBirthDate.setUTCFullYear(earliestBirthDate.getUTCFullYear() - 120);
  const gender = String(body.gender || '');
  const heightCm = Number(body.heightCm);
  const weightKg = Number(body.weightKg);
  const bloodGroup = String(body.bloodGroup || 'Unknown');
  const password = String(body.password || '');
  const allergies = String(body.allergies || '').trim().slice(0, 300);
  let conditions = Array.isArray(body.conditions) ? [...new Set(body.conditions.map(String))] : [];
  conditions = conditions.filter(condition => allowedConditions.has(condition));
  if (conditions.includes('none')) conditions = ['none'];

  if (!identityMethod || !identityValid) return sendJson(response, 400, { error: 'Identity number is invalid' });
  if (fullName.length < 2) return sendJson(response, 400, { error: 'Enter your full name' });
  if (Number.isNaN(date.getTime()) || date > new Date() || date < earliestBirthDate) return sendJson(response, 400, { error: 'Enter a valid date of birth' });
  if (!allowedGenders.has(gender)) return sendJson(response, 400, { error: 'Select a valid gender option' });
  if (!Number.isFinite(heightCm) || heightCm < 50 || heightCm > 250) return sendJson(response, 400, { error: 'Height must be between 50 and 250 cm' });
  if (!Number.isFinite(weightKg) || weightKg < 2 || weightKg > 350) return sendJson(response, 400, { error: 'Weight must be between 2 and 350 kg' });
  if (!allowedBloodGroups.has(bloodGroup)) return sendJson(response, 400, { error: 'Select a valid blood group' });
  if (password.length < 6 || password.length > 128) return sendJson(response, 400, { error: 'Password must contain at least 6 characters' });

  const passwordSalt = randomBytes(16).toString('hex');
  const patient = {
    id: `AS-${randomInt(100000, 1000000)}`,
    phone,
    identity: { method: identityMethod, last4: identityNumber.slice(-4) },
    password: { salt: passwordSalt, hash: scryptSync(password, passwordSalt, 64).toString('hex') },
    profile: { fullName, dateOfBirth, gender, heightCm, weightKg, bloodGroup },
    health: { conditions, allergies: allergies || null },
    createdAt: new Date().toISOString()
  };
  patients.push(patient);
  await savePatients();
  otpEntry.consumed = true;
  sendJson(response, 201, { patient: { id: patient.id, fullName: patient.profile.fullName } });
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

await Promise.all([loadDevices(), loadPatients()]);
const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
    if (request.method === 'POST' && url.pathname === '/api/device-enrollments') return await createEnrollment(request, response);
    if (request.method === 'POST' && url.pathname === '/api/signup-otp/request') return await requestSignupOtp(request, response);
    if (request.method === 'POST' && url.pathname === '/api/signup-otp/verify') return await verifySignupOtp(request, response);
    if (request.method === 'POST' && url.pathname === '/api/signup-otp/confirm') return await confirmSignupOtp(request, response);
    if (request.method === 'POST' && url.pathname === '/api/patient-registrations') return await createPatientRegistration(request, response);
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
