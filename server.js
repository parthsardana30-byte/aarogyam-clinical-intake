import http from 'node:http';
import { createCipheriv, createDecipheriv, createHash, randomBytes, randomInt, randomUUID, scryptSync, timingSafeEqual } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import QRCode from 'qrcode';

const host = process.env.HOST || '127.0.0.1';
const port = Number(process.env.PORT || 4173);
const publicRoot = resolve('dist');
const dataRoot = resolve(process.env.DATA_DIR || 'data');
const devicesFile = join(dataRoot, 'authorized-devices.json');
const patientsDbFile = join(dataRoot, 'patients.sqlite');
const patientUploadsRoot = join(dataRoot, 'patient-uploads');
const enrollmentTtlMs = 10 * 60 * 1000;
const documentUploadTtlMs = 30 * 60 * 1000;
const maxDocumentBytes = 8 * 1024 * 1024;
const signupOtpTtlMs = 5 * 60 * 1000;
const signupOtpCooldownMs = 30 * 1000;
const enrollments = new Map();
const signupOtps = new Map();
const signupOtpLastSent = new Map();
const hospitalBranches = new Map([
  ['civil-ahmedabad', { id: 'civil-ahmedabad', name: 'Civil Hospital', location: 'Ahmedabad, Gujarat', issuedDoctorIds: new Set(['CHA-DR-2187']) }],
  ['civil-gurugram', { id: 'civil-gurugram', name: 'Civil Hospital', location: 'Gurugram, Haryana', issuedDoctorIds: new Set(['CHG-DR-3304']) }],
  ['civil-ludhiana', { id: 'civil-ludhiana', name: 'Civil Hospital', location: 'Ludhiana, Punjab', issuedDoctorIds: new Set(['CHL-DR-4419']) }],
  ['civil-nashik', { id: 'civil-nashik', name: 'Civil Hospital', location: 'Nashik, Maharashtra', issuedDoctorIds: new Set(['CHN-DR-5576']) }],
  ['civil-rajkot', { id: 'civil-rajkot', name: 'Civil Hospital', location: 'Rajkot, Gujarat', issuedDoctorIds: new Set(['CHR-DR-6631']) }]
]);
let devices = [];
let patients = [];
let patientsDb;

const mimeTypes = {
  '.css': 'text/css; charset=utf-8', '.html': 'text/html; charset=utf-8', '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8', '.png': 'image/png',
  '.svg': 'image/svg+xml', '.webp': 'image/webp'
};

const hash = value => createHash('sha256').update(value).digest('hex');
const publicDevice = device => ({
  id: device.id,
  name: device.name,
  authorizedAt: device.authorizedAt,
  lastSeenAt: device.lastSeenAt || null,
  ipAddress: device.ipAddress || null,
  location: device.location || null
});

function deviceNetworkDetails(request) {
  const decodeHeader = value => {
    try { return decodeURIComponent(String(value || '').replace(/\+/g, ' ')); }
    catch { return String(value || ''); }
  };
  const forwarded = String(request.headers['x-forwarded-for'] || '').split(',')[0].trim();
  const rawIp = forwarded || String(request.headers['cf-connecting-ip'] || request.headers['x-real-ip'] || request.socket.remoteAddress || '').trim();
  const ipAddress = rawIp.replace(/^::ffff:/, '') || 'Unknown';
  const localIp = ipAddress === '::1' || ipAddress === '127.0.0.1' || ipAddress.startsWith('10.') || ipAddress.startsWith('192.168.') || /^172\.(1[6-9]|2\d|3[01])\./.test(ipAddress);
  const city = decodeHeader(request.headers['x-vercel-ip-city'] || request.headers['cf-ipcity']);
  const region = decodeHeader(request.headers['x-vercel-ip-country-region'] || request.headers['cf-region']);
  const country = String(request.headers['x-vercel-ip-country'] || request.headers['cf-ipcountry'] || '').trim();
  const locationParts = [city, region, country].filter(Boolean);
  return {
    ipAddress,
    location: localIp ? 'Local network' : (locationParts.join(', ') || 'Location unavailable')
  };
}

async function loadDevices() {
  await mkdir(dataRoot, { recursive: true });
  try { devices = JSON.parse(await readFile(devicesFile, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
}

async function loadPatients() {
  await mkdir(dataRoot, { recursive: true });
  patientsDb = new DatabaseSync(patientsDbFile);
  patientsDb.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;
    CREATE TABLE IF NOT EXISTS patients (
      id TEXT PRIMARY KEY,
      phone TEXT NOT NULL UNIQUE,
      identity_method TEXT NOT NULL,
      identity_ciphertext TEXT,
      identity_iv TEXT,
      identity_tag TEXT,
      identity_last4 TEXT NOT NULL,
      password_salt TEXT NOT NULL,
      password_hash TEXT NOT NULL,
      full_name TEXT NOT NULL,
      date_of_birth TEXT NOT NULL,
      gender TEXT NOT NULL,
      height_cm REAL NOT NULL,
      weight_kg REAL NOT NULL,
      blood_group TEXT NOT NULL,
      conditions_json TEXT NOT NULL,
      allergies TEXT,
      abha_link_status TEXT NOT NULL DEFAULT 'unlinked',
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS patients_phone_idx ON patients(phone);
    CREATE TABLE IF NOT EXISTS doctors (
      doctor_id TEXT PRIMARY KEY,
      hospital_id TEXT NOT NULL,
      hospital_name TEXT NOT NULL,
      hospital_location TEXT NOT NULL,
      full_name TEXT NOT NULL,
      degree TEXT NOT NULL,
      specialty TEXT NOT NULL,
      medical_registration_number TEXT NOT NULL UNIQUE,
      years_experience INTEGER NOT NULL,
      room_number TEXT NOT NULL,
      phone TEXT NOT NULL,
      email TEXT NOT NULL,
      password_salt TEXT NOT NULL,
      password_hash TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS doctors_hospital_idx ON doctors(hospital_id);
    CREATE TABLE IF NOT EXISTS staff (
      employee_id TEXT PRIMARY KEY,
      hospital_id TEXT NOT NULL,
      hospital_name TEXT NOT NULL,
      hospital_location TEXT NOT NULL,
      full_name TEXT NOT NULL,
      phone TEXT NOT NULL,
      email TEXT NOT NULL UNIQUE,
      password_salt TEXT NOT NULL,
      password_hash TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS staff_hospital_idx ON staff(hospital_id);
    CREATE TABLE IF NOT EXISTS patient_document_sessions (
      id TEXT PRIMARY KEY,
      token_hash TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'open',
      expires_at TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS patient_documents (
      id TEXT PRIMARY KEY,
      upload_session_id TEXT NOT NULL,
      patient_id TEXT,
      patient_reference TEXT,
      patient_name TEXT,
      uploaded_by_staff_id TEXT,
      original_name TEXT NOT NULL,
      mime_type TEXT NOT NULL,
      size_bytes INTEGER NOT NULL,
      stored_name TEXT NOT NULL UNIQUE,
      created_at TEXT NOT NULL,
      FOREIGN KEY(upload_session_id) REFERENCES patient_document_sessions(id) ON DELETE CASCADE,
      FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE
    );
    CREATE INDEX IF NOT EXISTS patient_documents_session_idx ON patient_documents(upload_session_id);
    CREATE INDEX IF NOT EXISTS patient_documents_patient_idx ON patient_documents(patient_id);
  `);
  const patientDocumentColumns = new Set(patientsDb.prepare('PRAGMA table_info(patient_documents)').all().map(column => column.name));
  const patientDocumentMigrations = [
    ['patient_reference', 'TEXT'], ['patient_name', 'TEXT'], ['uploaded_by_staff_id', 'TEXT']
  ];
  for (const [column, type] of patientDocumentMigrations) {
    if (!patientDocumentColumns.has(column)) patientsDb.exec(`ALTER TABLE patient_documents ADD COLUMN ${column} ${type}`);
  }
  const doctorColumns = new Set(patientsDb.prepare('PRAGMA table_info(doctors)').all().map(column => column.name));
  const doctorMigrations = [
    ['full_name', 'TEXT'], ['degree', 'TEXT'], ['specialty', 'TEXT'],
    ['medical_registration_number', 'TEXT'], ['years_experience', 'INTEGER'],
    ['room_number', 'TEXT'], ['phone', 'TEXT'], ['email', 'TEXT']
  ];
  for (const [column, type] of doctorMigrations) {
    if (!doctorColumns.has(column)) patientsDb.exec(`ALTER TABLE doctors ADD COLUMN ${column} ${type}`);
  }
  patientsDb.exec('CREATE UNIQUE INDEX IF NOT EXISTS doctors_medical_registration_idx ON doctors(medical_registration_number)');
  const rows = patientsDb.prepare('SELECT * FROM patients').all();
  patients = rows.map(row => ({
    id: row.id,
    phone: row.phone,
    identity: { method: row.identity_method, last4: row.identity_last4 },
    password: { salt: row.password_salt, hash: row.password_hash },
    profile: {
      fullName: row.full_name, dateOfBirth: row.date_of_birth, gender: row.gender,
      heightCm: row.height_cm, weightKg: row.weight_kg, bloodGroup: row.blood_group
    },
    health: { conditions: JSON.parse(row.conditions_json), allergies: row.allergies },
    abhaLinkStatus: row.abha_link_status,
    createdAt: row.created_at
  }));
}

async function saveDevices() {
  const temporary = `${devicesFile}.tmp`;
  await writeFile(temporary, JSON.stringify(devices, null, 2));
  await rename(temporary, devicesFile);
}

function patientEncryptionKey() {
  const configured = process.env.PATIENT_DATA_KEY;
  if (!configured && process.env.NODE_ENV === 'production') throw new Error('PATIENT_DATA_KEY is required in production');
  if (!configured) return createHash('sha256').update('arogsevak-local-development-key').digest();
  const decoded = Buffer.from(configured, 'base64');
  if (decoded.length !== 32) throw new Error('PATIENT_DATA_KEY must be a base64-encoded 32-byte key');
  return decoded;
}

function encryptIdentityNumber(value) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', patientEncryptionKey(), iv);
  const ciphertext = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]);
  return { ciphertext: ciphertext.toString('base64'), iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64') };
}

function decryptIdentityNumber(row) {
  const decipher = createDecipheriv('aes-256-gcm', patientEncryptionKey(), Buffer.from(row.identity_iv, 'base64'));
  decipher.setAuthTag(Buffer.from(row.identity_tag, 'base64'));
  return Buffer.concat([decipher.update(Buffer.from(row.identity_ciphertext, 'base64')), decipher.final()]).toString('utf8');
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

async function readJson(request, maxBytes = 20_000) {
  let body = '';
  for await (const chunk of request) {
    body += chunk;
    if (body.length > maxBytes) throw new Error('Request too large');
  }
  return JSON.parse(body || '{}');
}

function requestOrigin(request) {
  const protocol = request.headers['x-forwarded-proto'] || (request.socket.encrypted ? 'https' : 'http');
  return `${protocol}://${request.headers.host || `localhost:${port}`}`;
}

function documentSession(id, token) {
  const session = patientsDb.prepare('SELECT * FROM patient_document_sessions WHERE id = ?').get(id);
  if (!session || !token || hash(token) !== session.token_hash) return null;
  if (session.status !== 'open' || new Date(session.expires_at).getTime() < Date.now()) return null;
  return session;
}

function publicPatientDocument(row, id, token) {
  return {
    id: row.id,
    name: row.original_name,
    type: row.mime_type,
    size: row.size_bytes,
    createdAt: row.created_at,
    previewUrl: `/api/document-upload-sessions/${encodeURIComponent(id)}/files/${encodeURIComponent(row.id)}?token=${encodeURIComponent(token)}`
  };
}

async function createDocumentUploadSession(request, response) {
  const id = randomUUID();
  const token = randomBytes(32).toString('base64url');
  const createdAt = new Date().toISOString();
  const expiresAt = new Date(Date.now() + documentUploadTtlMs).toISOString();
  patientsDb.prepare(`INSERT INTO patient_document_sessions (id, token_hash, status, expires_at, created_at) VALUES (?, ?, 'open', ?, ?)`)
    .run(id, hash(token), expiresAt, createdAt);
  const uploadUrl = `${requestOrigin(request)}/mobile-upload.html#session=${encodeURIComponent(id)}&token=${encodeURIComponent(token)}`;
  const qrDataUrl = await QRCode.toDataURL(uploadUrl, { width: 360, margin: 1, color: { dark: '#102b27', light: '#ffffff' } });
  sendJson(response, 201, { id, token, expiresAt, uploadUrl, qrDataUrl });
}

function documentUploadStatus(request, response, id, url) {
  const token = String(url.searchParams.get('token') || '');
  const session = documentSession(id, token);
  if (!session) return sendJson(response, 404, { error: 'This upload session is invalid or expired' });
  const rows = patientsDb.prepare('SELECT * FROM patient_documents WHERE upload_session_id = ? ORDER BY created_at').all(id);
  sendJson(response, 200, { id, expiresAt: session.expires_at, files: rows.map(row => publicPatientDocument(row, id, token)) });
}

const uploadTypes = new Map([
  ['image/jpeg', '.jpg'], ['image/png', '.png'], ['image/webp', '.webp'], ['application/pdf', '.pdf']
]);

async function uploadPatientDocument(request, response, id) {
  const body = await readJson(request, maxDocumentBytes * 2);
  const token = String(body.token || '');
  if (!documentSession(id, token)) return sendJson(response, 404, { error: 'This upload session is invalid or expired' });
  const mimeType = String(body.type || '').toLowerCase();
  const extension = uploadTypes.get(mimeType);
  if (!extension) return sendJson(response, 400, { error: 'Upload a JPG, PNG, WebP or PDF file' });
  const originalName = String(body.name || 'Health document').trim().replace(/[\u0000-\u001f]/g, '').slice(0, 120) || 'Health document';
  const raw = String(body.data || '').replace(/^data:[^;]+;base64,/, '');
  let file;
  try { file = Buffer.from(raw, 'base64'); } catch { return sendJson(response, 400, { error: 'Document data is invalid' }); }
  if (!file.length || file.length > maxDocumentBytes) return sendJson(response, 400, { error: 'Each document must be smaller than 8 MB' });
  const documentId = randomUUID();
  const storedName = `${id}-${documentId}${extension}`;
  await mkdir(patientUploadsRoot, { recursive: true });
  await writeFile(join(patientUploadsRoot, storedName), file, { flag: 'wx' });
  const createdAt = new Date().toISOString();
  patientsDb.prepare(`
    INSERT INTO patient_documents (id, upload_session_id, patient_id, original_name, mime_type, size_bytes, stored_name, created_at)
    VALUES (?, ?, NULL, ?, ?, ?, ?, ?)
  `).run(documentId, id, originalName, mimeType, file.length, storedName, createdAt);
  const row = patientsDb.prepare('SELECT * FROM patient_documents WHERE id = ?').get(documentId);
  sendJson(response, 201, { file: publicPatientDocument(row, id, token) });
}

async function completeStaffDocumentUpload(request, response) {
  const body = await readJson(request);
  const sessionId = String(body.documentSessionId || '');
  const sessionToken = String(body.documentSessionToken || '');
  const patientReference = String(body.patientId || '').trim().slice(0, 40);
  const patientName = String(body.patientName || '').trim().replace(/\s+/g, ' ').slice(0, 80);
  const staffId = normalizeStaffId(body.staffId);
  const session = documentSession(sessionId, sessionToken);
  if (!session) return sendJson(response, 400, { error: 'The document upload session is invalid or expired' });
  if (!patientReference || !patientName) return sendJson(response, 400, { error: 'Select a patient before saving documents' });
  if (!patientsDb.prepare('SELECT employee_id FROM staff WHERE employee_id = ?').get(staffId)) {
    return sendJson(response, 403, { error: 'A registered hospital staff account is required' });
  }
  const documentCount = Number(patientsDb.prepare('SELECT COUNT(*) AS count FROM patient_documents WHERE upload_session_id = ?').get(sessionId).count);
  if (!documentCount) return sendJson(response, 400, { error: 'Upload at least one prescription or document' });
  const patient = patientsDb.prepare('SELECT id FROM patients WHERE id = ?').get(patientReference);
  patientsDb.prepare(`
    UPDATE patient_documents
    SET patient_id = ?, patient_reference = ?, patient_name = ?, uploaded_by_staff_id = ?
    WHERE upload_session_id = ?
  `).run(patient?.id || null, patientReference, patientName, staffId, sessionId);
  patientsDb.prepare("UPDATE patient_document_sessions SET status = 'completed' WHERE id = ?").run(sessionId);
  sendJson(response, 200, { saved: true, patientId: patientReference, documentCount });
}

async function servePatientDocument(request, response, sessionId, documentId, url) {
  const token = String(url.searchParams.get('token') || '');
  if (!documentSession(sessionId, token)) return sendJson(response, 404, { error: 'This upload session is invalid or expired' });
  const row = patientsDb.prepare('SELECT * FROM patient_documents WHERE id = ? AND upload_session_id = ?').get(documentId, sessionId);
  if (!row) return sendJson(response, 404, { error: 'Document not found' });
  try {
    const body = await readFile(join(patientUploadsRoot, row.stored_name));
    response.writeHead(200, {
      'Content-Type': row.mime_type,
      'Content-Length': body.length,
      'Content-Disposition': `inline; filename="${row.original_name.replace(/["\\]/g, '')}"`,
      'Cache-Control': 'private, no-store'
    });
    response.end(body);
  } catch { sendJson(response, 404, { error: 'Document file is unavailable' }); }
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
    Body: `Your Arogyam verification code is ${otp}. It expires in 5 minutes.`
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

function msg91Config() {
  const widgetId = String(process.env.MSG91_WIDGET_ID || '').trim();
  const tokenAuth = String(process.env.MSG91_WIDGET_TOKEN || '').trim();
  const authKey = String(process.env.MSG91_AUTH_KEY || '').trim();
  return { widgetId, tokenAuth, authKey, configured: Boolean(widgetId && tokenAuth && authKey) };
}

function signupOtpConfig(_request, response) {
  const config = msg91Config();
  if (config.configured) {
    return sendJson(response, 200, { provider: 'msg91', widgetId: config.widgetId, tokenAuth: config.tokenAuth });
  }
  sendJson(response, 200, { provider: 'server' });
}

async function patientRegistrationAvailability(request, response) {
  const body = await readJson(request);
  const phone = normalizeIndianPhone(body.phone);
  if (!phone) return sendJson(response, 400, { error: 'Enter a valid 10-digit Indian mobile number' });
  if (patients.some(patient => patient.phone === phone)) {
    return sendJson(response, 409, { available: false, error: 'An account already exists for this phone number. Sign in instead.' });
  }
  sendJson(response, 200, { available: true });
}

async function verifyMsg91AccessToken(accessToken) {
  const { authKey, configured } = msg91Config();
  if (!configured) throw new Error('MSG91 is not configured');
  const result = await fetch('https://control.msg91.com/api/v5/widget/verifyAccessToken', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ authkey: authKey, 'access-token': accessToken })
  });
  let payload = {};
  try { payload = await result.json(); } catch { /* MSG91 returned no JSON body. */ }
  const type = String(payload.type || payload.status || '').toLowerCase();
  const message = String(payload.message || '').toLowerCase();
  const verified = result.ok && (
    type === 'success' || payload.success === true || payload.verified === true ||
    message.includes('verified') || message.includes('valid')
  );
  if (!verified) throw new Error('MSG91 could not verify this OTP session');
  return payload;
}

async function requestSignupOtp(request, response) {
  purgeExpiredSignupOtps();
  const body = await readJson(request);
  const phone = normalizeIndianPhone(body.phone);
  if (!phone) return sendJson(response, 400, { error: 'Enter a valid 10-digit Indian mobile number' });
  if (patients.some(patient => patient.phone === phone)) {
    return sendJson(response, 409, { error: 'An account already exists for this phone number. Sign in instead.' });
  }
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
  const accessToken = String(body.accessToken || '').trim();
  if (accessToken) {
    if (!phone) return sendJson(response, 400, { error: 'Enter a valid 10-digit Indian mobile number' });
    if (accessToken.length > 4096) return sendJson(response, 400, { error: 'Invalid OTP access token' });
    try {
      await verifyMsg91AccessToken(accessToken);
    } catch (error) {
      console.error('MSG91 verification failed:', error.message);
      return sendJson(response, 401, { error: 'OTP verification failed. Please request a new code.' });
    }
    const id = randomUUID();
    const verificationToken = randomBytes(32).toString('base64url');
    signupOtps.set(id, {
      id, phone, otpHash: null, expiresAt: Date.now() + 30 * 60 * 1000,
      attempts: 0, verifiedTokenHash: hash(verificationToken), consumed: false
    });
    return sendJson(response, 200, { verified: true, id, verificationToken });
  }
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

  const documentSessionId = String(body.documentSessionId || '');
  const documentSessionToken = String(body.documentSessionToken || '');
  const pendingDocumentSession = documentSessionId ? documentSession(documentSessionId, documentSessionToken) : null;
  if (documentSessionId && !pendingDocumentSession) {
    return sendJson(response, 400, { error: 'The document upload session expired. Return to the document step and try again.' });
  }

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
  let patientId;
  do { patientId = `AS-${randomInt(100000, 1000000)}`; }
  while (patients.some(item => item.id === patientId));
  const encryptedIdentity = encryptIdentityNumber(identityNumber);
  const patient = {
    id: patientId,
    phone,
    identity: { method: identityMethod, last4: identityNumber.slice(-4) },
    password: { salt: passwordSalt, hash: scryptSync(password, passwordSalt, 64).toString('hex') },
    profile: { fullName, dateOfBirth, gender, heightCm, weightKg, bloodGroup },
    health: { conditions, allergies: allergies || null },
    abhaLinkStatus: identityMethod === 'abha' ? 'pending_verification' : 'unlinked',
    createdAt: new Date().toISOString()
  };
  patientsDb.prepare(`
    INSERT INTO patients (
      id, phone, identity_method, identity_ciphertext, identity_iv, identity_tag, identity_last4,
      password_salt, password_hash, full_name, date_of_birth, gender, height_cm, weight_kg,
      blood_group, conditions_json, allergies, abha_link_status, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    patient.id, patient.phone, patient.identity.method, encryptedIdentity.ciphertext, encryptedIdentity.iv,
    encryptedIdentity.tag, patient.identity.last4, patient.password.salt, patient.password.hash,
    patient.profile.fullName, patient.profile.dateOfBirth, patient.profile.gender, patient.profile.heightCm,
    patient.profile.weightKg, patient.profile.bloodGroup, JSON.stringify(patient.health.conditions),
    patient.health.allergies, patient.abhaLinkStatus, patient.createdAt
  );
  if (pendingDocumentSession) {
    patientsDb.prepare('UPDATE patient_documents SET patient_id = ? WHERE upload_session_id = ?').run(patient.id, documentSessionId);
    patientsDb.prepare("UPDATE patient_document_sessions SET status = 'completed' WHERE id = ?").run(documentSessionId);
  }
  patients.push(patient);
  otpEntry.consumed = true;
  const documentCount = pendingDocumentSession
    ? Number(patientsDb.prepare('SELECT COUNT(*) AS count FROM patient_documents WHERE patient_id = ?').get(patient.id).count)
    : 0;
  sendJson(response, 201, { patient: { id: patient.id, fullName: patient.profile.fullName, documentCount } });
}

async function loginPatient(request, response) {
  const body = await readJson(request);
  const identityMethod = body.identityMethod === 'abha' ? 'abha' : body.identityMethod === 'aadhaar' ? 'aadhaar' : null;
  const identityNumber = String(body.identityNumber || body.identifier || '').replace(/\D/g, '');
  const password = String(body.password || '');
  const identityValid = identityMethod === 'abha' ? /^\d{14}$/.test(identityNumber) : identityMethod === 'aadhaar' && /^\d{12}$/.test(identityNumber);
  if (!identityMethod || !identityValid) return sendJson(response, 400, { error: 'Enter a valid ABHA ID or Aadhaar number' });
  const candidates = patientsDb.prepare(`
    SELECT * FROM patients WHERE identity_method = ? AND identity_last4 = ?
  `).all(identityMethod, identityNumber.slice(-4));
  const row = candidates.find(candidate => {
    try { return decryptIdentityNumber(candidate) === identityNumber; } catch { return false; }
  });
  const patient = row ? patients.find(item => item.id === row.id) : null;
  if (!patient || !password || !patient.password?.salt || !patient.password?.hash) {
    return sendJson(response, 401, { error: 'Identity number or password is incorrect' });
  }
  const candidate = scryptSync(password, patient.password.salt, 64);
  const stored = Buffer.from(patient.password.hash, 'hex');
  if (stored.length !== candidate.length || !timingSafeEqual(stored, candidate)) {
    return sendJson(response, 401, { error: 'Identity number or password is incorrect' });
  }
  sendJson(response, 200, { patient: { id: patient.id, fullName: patient.profile.fullName } });
}

function normalizeDoctorId(value) {
  return String(value || '').trim().toUpperCase().replace(/\s+/g, '');
}

function normalizeStaffId(value) {
  return String(value || '').trim().slice(0, 80);
}

const allowedDoctorSpecialties = new Set([
  'General Medicine', 'Gynaecology', 'Orthopaedics', 'Paediatrics', 'General Surgery',
  'Cardiology', 'Dermatology', 'ENT', 'Ophthalmology', 'Psychiatry', 'AYUSH Medicine', 'Other'
]);

function publicDoctor(row) {
  return {
    id: row.doctor_id,
    fullName: row.full_name,
    degree: row.degree,
    specialty: row.specialty,
    medicalRegistrationNumber: row.medical_registration_number,
    yearsExperience: row.years_experience,
    roomNumber: row.room_number,
    phone: row.phone,
    email: row.email,
    hospitalId: row.hospital_id,
    hospitalName: row.hospital_name,
    hospitalLocation: row.hospital_location
  };
}

function publicHospital(branch) {
  return {
    id: branch.id,
    name: branch.name,
    location: branch.location,
    demoDoctorId: [...branch.issuedDoctorIds][0],
    backgroundImage: `/assets/hospitals/${branch.id}.png`
  };
}

function listHospitalBranches(_request, response) {
  sendJson(response, 200, { hospitals: [...hospitalBranches.values()].map(publicHospital) });
}

async function createDoctorRegistration(request, response) {
  const body = await readJson(request);
  const hospital = hospitalBranches.get(String(body.hospitalId || ''));
  const doctorId = normalizeDoctorId(body.doctorId);
  const password = String(body.password || '');
  const fullName = String(body.fullName || '').trim().replace(/\s+/g, ' ').slice(0, 80);
  const degree = String(body.degree || '').trim().replace(/\s+/g, ' ').slice(0, 80);
  const specialty = String(body.specialty || '');
  const medicalRegistrationNumber = String(body.medicalRegistrationNumber || '').trim().toUpperCase().replace(/\s+/g, '');
  const yearsExperienceRaw = String(body.yearsExperience ?? '').trim();
  const yearsExperience = yearsExperienceRaw ? Number(yearsExperienceRaw) : Number.NaN;
  const roomNumber = String(body.roomNumber || '').trim().replace(/\s+/g, ' ').toUpperCase().slice(0, 20);
  const phone = normalizeIndianPhone(body.phone);
  const email = String(body.email || '').trim().toLowerCase().slice(0, 120);
  if (!hospital) return sendJson(response, 400, { error: 'Select a valid hospital branch' });
  if (!hospital.issuedDoctorIds.has(doctorId)) {
    return sendJson(response, 403, { error: 'This doctor ID was not issued by the selected hospital branch' });
  }
  if (password.length < 8 || password.length > 128) {
    return sendJson(response, 400, { error: 'Password must contain at least 8 characters' });
  }
  if (fullName.length < 2) return sendJson(response, 400, { error: 'Enter the doctor’s full name' });
  if (degree.length < 2) return sendJson(response, 400, { error: 'Enter a valid medical degree or qualification' });
  if (!allowedDoctorSpecialties.has(specialty)) return sendJson(response, 400, { error: 'Select a valid medical specialty' });
  if (!/^[A-Z0-9/-]{4,30}$/.test(medicalRegistrationNumber)) {
    return sendJson(response, 400, { error: 'Enter a valid medical council registration number' });
  }
  if (!Number.isInteger(yearsExperience) || yearsExperience < 0 || yearsExperience > 70) {
    return sendJson(response, 400, { error: 'Years of experience must be between 0 and 70' });
  }
  if (!/^[A-Z0-9 -]{1,20}$/.test(roomNumber)) return sendJson(response, 400, { error: 'Enter a valid room or OPD number' });
  if (!phone) return sendJson(response, 400, { error: 'Enter a valid 10-digit Indian mobile number' });
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return sendJson(response, 400, { error: 'Enter a valid professional email address' });
  if (patientsDb.prepare('SELECT doctor_id FROM doctors WHERE doctor_id = ?').get(doctorId)) {
    return sendJson(response, 409, { error: 'This hospital-issued doctor ID is already registered' });
  }
  if (patientsDb.prepare('SELECT doctor_id FROM doctors WHERE medical_registration_number = ?').get(medicalRegistrationNumber)) {
    return sendJson(response, 409, { error: 'This medical registration number is already associated with an account' });
  }
  const passwordSalt = randomBytes(16).toString('hex');
  const createdAt = new Date().toISOString();
  patientsDb.prepare(`
    INSERT INTO doctors (
      doctor_id, hospital_id, hospital_name, hospital_location, full_name, degree, specialty,
      medical_registration_number, years_experience, room_number, phone, email,
      password_salt, password_hash, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    doctorId, hospital.id, hospital.name, hospital.location, fullName, degree, specialty,
    medicalRegistrationNumber, yearsExperience, roomNumber, phone, email, passwordSalt,
    scryptSync(password, passwordSalt, 64).toString('hex'), createdAt
  );
  const doctor = patientsDb.prepare('SELECT * FROM doctors WHERE doctor_id = ?').get(doctorId);
  sendJson(response, 201, { doctor: publicDoctor(doctor) });
}

async function loginDoctor(request, response) {
  const body = await readJson(request);
  const doctorId = normalizeDoctorId(body.doctorId);
  const password = String(body.password || '');
  const row = patientsDb.prepare('SELECT * FROM doctors WHERE doctor_id = ?').get(doctorId);
  if (!row || !password) return sendJson(response, 401, { error: 'Doctor ID or password is incorrect' });
  const candidate = scryptSync(password, row.password_salt, 64);
  const stored = Buffer.from(row.password_hash, 'hex');
  if (stored.length !== candidate.length || !timingSafeEqual(stored, candidate)) {
    return sendJson(response, 401, { error: 'Doctor ID or password is incorrect' });
  }
  sendJson(response, 200, { doctor: publicDoctor(row) });
}

function publicStaff(row) {
  return {
    id: row.employee_id,
    fullName: row.full_name,
    phone: row.phone,
    email: row.email,
    hospitalId: row.hospital_id,
    hospitalName: row.hospital_name,
    hospitalLocation: row.hospital_location,
    createdAt: row.created_at
  };
}

async function createStaffRegistration(request, response) {
  const body = await readJson(request);
  const hospital = hospitalBranches.get(String(body.hospitalId || ''));
  const employeeId = normalizeStaffId(body.employeeId);
  const password = String(body.password || '');
  const fullName = String(body.fullName || '').trim().replace(/\s+/g, ' ').slice(0, 80);
  const phone = normalizeIndianPhone(body.phone);
  const email = String(body.email || '').trim().toLowerCase().slice(0, 120);
  if (!hospital) return sendJson(response, 400, { error: 'Select a valid hospital branch' });
  if (!employeeId) return sendJson(response, 400, { error: 'Enter an employee ID' });
  if (password.length < 8 || password.length > 128) {
    return sendJson(response, 400, { error: 'Password must contain at least 8 characters' });
  }
  if (fullName.length < 2) return sendJson(response, 400, { error: 'Enter the staff member’s full name' });
  if (!phone) return sendJson(response, 400, { error: 'Enter a valid 10-digit Indian mobile number' });
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return sendJson(response, 400, { error: 'Enter a valid professional email address' });
  if (patientsDb.prepare('SELECT employee_id FROM staff WHERE employee_id = ?').get(employeeId)) {
    return sendJson(response, 409, { error: 'This employee ID is already registered' });
  }
  if (patientsDb.prepare('SELECT employee_id FROM staff WHERE email = ?').get(email)) {
    return sendJson(response, 409, { error: 'This professional email is already associated with an account' });
  }
  const passwordSalt = randomBytes(16).toString('hex');
  const createdAt = new Date().toISOString();
  patientsDb.prepare(`
    INSERT INTO staff (
      employee_id, hospital_id, hospital_name, hospital_location, full_name, phone, email,
      password_salt, password_hash, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    employeeId, hospital.id, hospital.name, hospital.location, fullName, phone, email,
    passwordSalt, scryptSync(password, passwordSalt, 64).toString('hex'), createdAt
  );
  const staff = patientsDb.prepare('SELECT * FROM staff WHERE employee_id = ?').get(employeeId);
  sendJson(response, 201, { staff: publicStaff(staff) });
}

async function loginStaff(request, response) {
  const body = await readJson(request);
  const employeeId = String(body.employeeId || '').trim();
  const password = String(body.password || '');
  if (!employeeId || !password) return sendJson(response, 401, { error: 'Enter any employee ID and your password' });
  const exactRow = patientsDb.prepare('SELECT * FROM staff WHERE employee_id = ?').get(normalizeStaffId(employeeId));
  const candidates = exactRow ? [exactRow] : patientsDb.prepare('SELECT * FROM staff ORDER BY datetime(created_at) DESC').all();
  let row = null;
  for (const candidateRow of candidates) {
    const candidate = scryptSync(password, candidateRow.password_salt, 64);
    const stored = Buffer.from(candidateRow.password_hash, 'hex');
    if (stored.length === candidate.length && timingSafeEqual(stored, candidate)) {
      row = candidateRow;
      break;
    }
  }
  if (!row) return sendJson(response, 401, { error: 'Password is incorrect' });
  sendJson(response, 200, { staff: publicStaff(row) });
}

function listStaffPatientQueue(request, response, url) {
  const staffId = normalizeStaffId(url.searchParams.get('staffId'));
  if (!patientsDb.prepare('SELECT employee_id FROM staff WHERE employee_id = ?').get(staffId)) {
    return sendJson(response, 403, { error: 'A registered hospital staff account is required' });
  }
  const queue = patientsDb.prepare(`
    SELECT id, full_name, created_at
    FROM patients
    ORDER BY datetime(created_at) DESC
  `).all().map(row => ({ id: row.id, fullName: row.full_name }));
  sendJson(response, 200, { patients: queue });
}

function getStaffProfile(request, response, url) {
  const staffId = normalizeStaffId(url.searchParams.get('staffId'));
  const staff = patientsDb.prepare('SELECT * FROM staff WHERE employee_id = ?').get(staffId);
  if (!staff) return sendJson(response, 404, { error: 'Staff profile was not found' });
  sendJson(response, 200, { staff: publicStaff(staff) });
}

async function createEnrollment(request, response) {
  purgeExpiredEnrollments();
  const body = await readJson(request);
  const staffId = normalizeStaffId(body.staffId);
  if (!patientsDb.prepare('SELECT employee_id FROM staff WHERE employee_id = ?').get(staffId)) {
    return sendJson(response, 403, { error: 'A registered hospital staff account is required' });
  }
  let code;
  do { code = String(randomInt(0, 1_000_000)).padStart(6, '0'); }
  while ([...enrollments.values()].some(item => item.codeHash === hash(code)));
  const id = randomUUID();
  const creatorToken = randomBytes(24).toString('base64url');
  const claimToken = randomBytes(24).toString('base64url');
  const expiresAt = Date.now() + enrollmentTtlMs;
  const origin = `http://${request.headers.host || `localhost:${port}`}`;
  const setupUrl = `${origin}/device-enroll.html?enrollment=${encodeURIComponent(id)}&token=${encodeURIComponent(claimToken)}`;
  enrollments.set(id, {
    id, staffId, codeHash: hash(code), creatorHash: hash(creatorToken), claimHash: hash(claimToken),
    expiresAt, status: 'open', request: null
  });
  const qrDataUrl = await QRCode.toDataURL(setupUrl, { width: 360, margin: 1, color: { dark: '#102b27', light: '#ffffff' } });
  sendJson(response, 201, { id, code, creatorToken, qrDataUrl, setupUrl, expiresAt });
}

function enrollmentStatus(request, response, id, url) {
  purgeExpiredEnrollments();
  const enrollment = enrollments.get(id);
  const requestToken = url.searchParams.get('requestToken') || '';
  if (!enrollment || !enrollment.request || hash(requestToken) !== enrollment.request.tokenHash) return sendJson(response, 404, { status: 'expired' });
  if (enrollment.status === 'rejected') return sendJson(response, 200, { status: 'rejected' });
  if (enrollment.status !== 'authorized') return sendJson(response, 200, { status: 'pending', expiresAt: enrollment.expiresAt });
  const headers = { 'Set-Cookie': `arog_device=${encodeURIComponent(enrollment.deviceToken)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=31536000${secureCookie(request)}` };
  sendJson(response, 200, { status: 'authorized', device: publicDevice(enrollment.device) }, headers);
  enrollments.delete(id);
}

async function requestDeviceAuthorization(request, response, id) {
  purgeExpiredEnrollments();
  const body = await readJson(request);
  const name = String(body.name || '').trim().slice(0, 60);
  const claimToken = String(body.claimToken || '');
  const code = String(body.code || '').trim();
  const enrollment = id
    ? enrollments.get(id)
    : [...enrollments.values()].find(item => item.status === 'open' && /^\d{6}$/.test(code) && item.codeHash === hash(code));
  if (!name) return sendJson(response, 400, { error: 'Enter a name for this device' });
  const validClaim = enrollment && ((id && hash(claimToken) === enrollment.claimHash) || (!id && /^\d{6}$/.test(code)));
  if (!enrollment || enrollment.status !== 'open' || !validClaim) {
    return sendJson(response, 404, { error: 'This invitation is invalid or has expired' });
  }
  const requestToken = randomBytes(24).toString('base64url');
  const network = deviceNetworkDetails(request);
  enrollment.status = 'requested';
  enrollment.request = {
    name,
    platform: String(body.platform || request.headers['user-agent'] || 'Unknown device').slice(0, 140),
    ipAddress: network.ipAddress,
    location: network.location,
    requestedAt: new Date().toISOString(),
    tokenHash: hash(requestToken)
  };
  sendJson(response, 201, { status: 'pending', enrollmentId: enrollment.id, requestToken, expiresAt: enrollment.expiresAt });
}

function manageEnrollment(request, response, id, url) {
  purgeExpiredEnrollments();
  const enrollment = enrollments.get(id);
  const creatorToken = url.searchParams.get('creatorToken') || '';
  if (!enrollment || hash(creatorToken) !== enrollment.creatorHash) return sendJson(response, 404, { status: 'expired' });
  sendJson(response, 200, {
    status: enrollment.status,
    expiresAt: enrollment.expiresAt,
    request: enrollment.request ? {
      name: enrollment.request.name,
      platform: enrollment.request.platform,
      ipAddress: enrollment.request.ipAddress,
      location: enrollment.request.location,
      requestedAt: enrollment.request.requestedAt
    } : null
  });
}

async function decideDeviceAuthorization(request, response, id) {
  purgeExpiredEnrollments();
  const body = await readJson(request);
  const enrollment = enrollments.get(id);
  if (!enrollment || hash(String(body.creatorToken || '')) !== enrollment.creatorHash) {
    return sendJson(response, 404, { error: 'This invitation is invalid or has expired' });
  }
  if (enrollment.status !== 'requested' || !enrollment.request) return sendJson(response, 409, { error: 'There is no pending device request' });
  if (body.decision === 'reject') {
    enrollment.status = 'rejected';
    return sendJson(response, 200, { status: 'rejected' });
  }
  if (body.decision !== 'approve') return sendJson(response, 400, { error: 'Choose approve or reject' });
  const deviceToken = randomBytes(32).toString('base64url');
  const device = {
    id: randomUUID(),
    name: enrollment.request.name,
    tokenHash: hash(deviceToken),
    ipAddress: enrollment.request.ipAddress,
    location: enrollment.request.location,
    authorizedAt: new Date().toISOString(),
    lastSeenAt: null
  };
  devices.push(device);
  await saveDevices();
  Object.assign(enrollment, { status: 'authorized', device, deviceToken });
  sendJson(response, 201, { device: publicDevice(device) });
}

async function listDevices(_request, response) {
  sendJson(response, 200, { devices: devices.map(publicDevice).sort((a, b) => b.authorizedAt.localeCompare(a.authorizedAt)) });
}

async function revokeDevice(_request, response, id) {
  const deviceIndex = devices.findIndex(device => device.id === id);
  if (deviceIndex < 0) return sendJson(response, 404, { error: 'Authorized device was not found' });
  const [revokedDevice] = devices.splice(deviceIndex, 1);
  await saveDevices();
  sendJson(response, 200, { revoked: true, device: publicDevice(revokedDevice) });
}

async function deviceSession(request, response) {
  const token = cookies(request).arog_device;
  const device = token && devices.find(item => item.tokenHash === hash(token));
  if (!device) return sendJson(response, 200, { authorized: false });
  device.lastSeenAt = new Date().toISOString();
  void saveDevices();
  sendJson(response, 200, { authorized: true, device: publicDevice(device), allowedRole: 'patient' });
}

function exitDeviceSession(request, response) {
  sendJson(response, 200, { authorized: false }, {
    'Set-Cookie': `arog_device=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0${secureCookie(request)}`
  });
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
    if (request.method === 'POST' && url.pathname === '/api/document-upload-sessions') return await createDocumentUploadSession(request, response);
    const documentStatusMatch = url.pathname.match(/^\/api\/document-upload-sessions\/([0-9a-f-]+)\/status$/i);
    if (request.method === 'GET' && documentStatusMatch) return documentUploadStatus(request, response, documentStatusMatch[1], url);
    const documentFileMatch = url.pathname.match(/^\/api\/document-upload-sessions\/([0-9a-f-]+)\/files\/([0-9a-f-]+)$/i);
    if (request.method === 'GET' && documentFileMatch) return await servePatientDocument(request, response, documentFileMatch[1], documentFileMatch[2], url);
    const documentUploadMatch = url.pathname.match(/^\/api\/document-upload-sessions\/([0-9a-f-]+)\/files$/i);
    if (request.method === 'POST' && documentUploadMatch) return await uploadPatientDocument(request, response, documentUploadMatch[1]);
    if (request.method === 'POST' && url.pathname === '/api/staff-patient-documents') return await completeStaffDocumentUpload(request, response);
    if (request.method === 'GET' && url.pathname === '/api/signup-otp/config') return signupOtpConfig(request, response);
    if (request.method === 'POST' && url.pathname === '/api/patient-registration-availability') return await patientRegistrationAvailability(request, response);
    if (request.method === 'POST' && url.pathname === '/api/signup-otp/request') return await requestSignupOtp(request, response);
    if (request.method === 'POST' && url.pathname === '/api/signup-otp/verify') return await verifySignupOtp(request, response);
    if (request.method === 'POST' && url.pathname === '/api/patient-registrations') return await createPatientRegistration(request, response);
    if (request.method === 'POST' && url.pathname === '/api/patient-login') return await loginPatient(request, response);
    if (request.method === 'GET' && url.pathname === '/api/hospital-branches') return listHospitalBranches(request, response);
    if (request.method === 'POST' && url.pathname === '/api/doctor-registrations') return await createDoctorRegistration(request, response);
    if (request.method === 'POST' && url.pathname === '/api/doctor-login') return await loginDoctor(request, response);
    if (request.method === 'POST' && url.pathname === '/api/staff-registrations') return await createStaffRegistration(request, response);
    if (request.method === 'POST' && url.pathname === '/api/staff-login') return await loginStaff(request, response);
    if (request.method === 'GET' && url.pathname === '/api/staff-patient-queue') return listStaffPatientQueue(request, response, url);
    if (request.method === 'GET' && url.pathname === '/api/staff-profile') return getStaffProfile(request, response, url);
    const statusMatch = url.pathname.match(/^\/api\/device-enrollments\/([0-9a-f-]+)\/status$/i);
    if (request.method === 'GET' && statusMatch) return enrollmentStatus(request, response, statusMatch[1], url);
    const requestMatch = url.pathname.match(/^\/api\/device-enrollments\/([0-9a-f-]+)\/request$/i);
    if (request.method === 'POST' && requestMatch) return await requestDeviceAuthorization(request, response, requestMatch[1]);
    if (request.method === 'POST' && url.pathname === '/api/device-enrollment-requests') return await requestDeviceAuthorization(request, response, '');
    const manageMatch = url.pathname.match(/^\/api\/device-enrollments\/([0-9a-f-]+)\/manage$/i);
    if (request.method === 'GET' && manageMatch) return manageEnrollment(request, response, manageMatch[1], url);
    const decisionMatch = url.pathname.match(/^\/api\/device-enrollments\/([0-9a-f-]+)\/decision$/i);
    if (request.method === 'POST' && decisionMatch) return await decideDeviceAuthorization(request, response, decisionMatch[1]);
    if (request.method === 'GET' && url.pathname === '/api/devices') return await listDevices(request, response);
    const deviceMatch = url.pathname.match(/^\/api\/devices\/([0-9a-f-]+)$/i);
    if (request.method === 'DELETE' && deviceMatch) return await revokeDevice(request, response, deviceMatch[1]);
    if (request.method === 'GET' && url.pathname === '/api/device-session') return await deviceSession(request, response);
    if (request.method === 'POST' && url.pathname === '/api/device-session/logout') return exitDeviceSession(request, response);
    if (request.method === 'GET' || request.method === 'HEAD') return await serveStatic(request, response);
    sendJson(response, 405, { error: 'Method not allowed' });
  } catch (error) {
    console.error(error);
    sendJson(response, 500, { error: 'Server error' });
  }
});

server.listen(port, host, () => console.log(`Arogyam running at http://${host}:${port}`));
