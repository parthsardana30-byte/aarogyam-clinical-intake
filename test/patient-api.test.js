import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { connect } from 'node:net';
import { after, before, test } from 'node:test';

const port = 43173;
const origin = `http://127.0.0.1:${port}`;
let dataDir;
let server;

async function json(path, options = {}) {
  const response = await fetch(origin + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });
  const payload = await response.json();
  return { response, payload };
}

async function waitForServer() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`${origin}/api/signup-otp/config`);
      if (response.ok) return;
    } catch { /* Server is still starting. */ }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Test server did not start');
}

async function startServer() {
  server = spawn(process.execPath, ['server.js'], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: String(port), HOST: '127.0.0.1', DATA_DIR: dataDir, OTP_DEMO_MODE: '1' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await waitForServer();
}

async function stopServer() {
  if (server && server.exitCode === null) {
    server.kill();
    await new Promise(resolve => server.once('exit', resolve));
  }
}

before(async () => {
  dataDir = await mkdtemp(join(tmpdir(), 'aarogyam-api-test-'));
  await startServer();
});

after(async () => {
  await stopServer();
  await rm(dataDir, { recursive: true, force: true });
});

test('voice relay rejects an upgrade without a one-time ticket', async () => {
  const response = await new Promise((resolve, reject) => {
    const socket = connect(port, '127.0.0.1');
    let received = '';
    socket.on('connect', () => socket.write('GET /api/elevenlabs/voice?ticket=invalid HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n'));
    socket.on('data', chunk => { received += chunk.toString(); });
    socket.on('end', () => resolve(received));
    socket.on('error', reject);
  });
  assert.match(response, /^HTTP\/1\.1 401 Unauthorized/);
});

test('patient registration, login and dashboard stay patient-scoped', async () => {
  const phone = '9876543210';
  const password = '123456';

  const otpRequest = await json('/api/signup-otp/request', {
    method: 'POST', body: JSON.stringify({ phone }),
  });
  assert.equal(otpRequest.response.status, 201);
  assert.match(otpRequest.payload.demoOtp, /^\d{6}$/);

  const verification = await json('/api/signup-otp/verify', {
    method: 'POST',
    body: JSON.stringify({ phone, id: otpRequest.payload.id, otp: otpRequest.payload.demoOtp }),
  });
  assert.equal(verification.response.status, 200);

  const registrationDocuments = await json('/api/document-upload-sessions', { method: 'POST', body: '{}' });
  assert.equal(registrationDocuments.response.status, 201);
  const registrationUpload = await json(`/api/document-upload-sessions/${registrationDocuments.payload.id}/files`, {
    method: 'POST', body: JSON.stringify({
      token: registrationDocuments.payload.token, name: 'previous-report.png', type: 'image/png',
      data: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    }),
  });
  assert.equal(registrationUpload.response.status, 201);

  const registration = await json('/api/patient-registrations', {
    method: 'POST',
    body: JSON.stringify({
      phone,
      otpRequestId: otpRequest.payload.id,
      otpVerificationToken: verification.payload.verificationToken,
      identityMethod: 'aadhaar',
      identityNumber: '700123456789',
      fullName: 'Integration Test Patient',
      dateOfBirth: '1995-06-15',
      gender: 'Female',
      heightCm: 165,
      weightKg: 62,
      bloodGroup: 'O+',
      conditions: ['none'],
      allergies: 'None',
      password,
      documentSessionId: registrationDocuments.payload.id,
      documentSessionToken: registrationDocuments.payload.token,
    }),
  });
  assert.equal(registration.response.status, 201);
  assert.match(registration.payload.patient.id, /^AS-\d{6}$/);
  assert.ok(registration.payload.sessionToken);
  assert.equal(registration.payload.patient.documentCount, 1);

  const phoneLogin = await json('/api/patient-login', {
    method: 'POST', body: JSON.stringify({ phone, password }),
  });
  assert.equal(phoneLogin.response.status, 200);
  assert.ok(phoneLogin.payload.sessionToken);

  const loginOtpRequest = await json('/api/signup-otp/request', {
    method: 'POST', body: JSON.stringify({ phone, purpose: 'login' }),
  });
  const loginOtpVerification = await json('/api/signup-otp/verify', {
    method: 'POST', body: JSON.stringify({ phone, purpose: 'login', id: loginOtpRequest.payload.id, otp: loginOtpRequest.payload.demoOtp }),
  });
  const otpLogin = await json('/api/patient-login-otp', {
    method: 'POST', body: JSON.stringify({ phone, otpRequestId: loginOtpRequest.payload.id, otpVerificationToken: loginOtpVerification.payload.verificationToken }),
  });
  assert.equal(otpLogin.response.status, 200);
  assert.ok(otpLogin.payload.sessionToken);

  const resetOtpRequest = await json('/api/signup-otp/request', {
    method: 'POST', body: JSON.stringify({ phone, purpose: 'reset' }),
  });
  const resetOtpVerification = await json('/api/signup-otp/verify', {
    method: 'POST', body: JSON.stringify({ phone, purpose: 'reset', id: resetOtpRequest.payload.id, otp: resetOtpRequest.payload.demoOtp }),
  });
  const invalidReset = await json('/api/patient-password-reset', {
    method: 'POST', body: JSON.stringify({ phone, otpRequestId: resetOtpRequest.payload.id,
      otpVerificationToken: resetOtpVerification.payload.verificationToken, epin: 'secret123' }),
  });
  assert.equal(invalidReset.response.status, 400);
  assert.match(invalidReset.payload.error, /exactly 6 digits/);
  const reset = await json('/api/patient-password-reset', {
    method: 'POST', body: JSON.stringify({ phone, otpRequestId: resetOtpRequest.payload.id,
      otpVerificationToken: resetOtpVerification.payload.verificationToken, epin: '654321' }),
  });
  assert.equal(reset.response.status, 200);
  assert.ok(reset.payload.sessionToken);

  const login = await json('/api/patient-login', {
    method: 'POST', body: JSON.stringify({ phone, epin: '654321' }),
  });
  assert.equal(login.response.status, 200);
  assert.ok(login.payload.sessionToken);

  const patientId = login.payload.patient.id;
  const denied = await json(`/api/patients/${patientId}/dashboard`, {
    headers: { Authorization: 'Bearer invalid' },
  });
  assert.equal(denied.response.status, 401);

  const auth = { Authorization: `Bearer ${login.payload.sessionToken}` };
  const linkAbha = await json(`/api/patients/${patientId}/link-abha`, {
    method: 'POST', headers: auth, body: JSON.stringify({ abhaNumber: '80012345678901' }),
  });
  assert.equal(linkAbha.response.status, 200);
  assert.equal(linkAbha.payload.last4, '8901');
  const accessHistory = await json(`/api/patients/${patientId}/access-history`, { headers: auth });
  assert.equal(accessHistory.response.status, 200);
  assert.deepEqual(accessHistory.payload.accessHistory, []);
  const initialDashboard = await json(`/api/patients/${patientId}/dashboard`, { headers: auth });
  assert.equal(initialDashboard.response.status, 200);
  assert.equal(initialDashboard.payload.patient.profile.fullName, 'Integration Test Patient');
  assert.equal(initialDashboard.payload.patient.profile.gender, 'Female');
  assert.equal(initialDashboard.payload.patient.profile.heightCm, 165);
  assert.equal(initialDashboard.payload.patient.profile.weightKg, 62);
  assert.deepEqual(initialDashboard.payload.summary, { consultationCount: 0, documentCount: 1 });
  assert.equal(initialDashboard.payload.documents[0].name, 'previous-report.png');
  assert.equal(initialDashboard.payload.documents[0].source, 'registration');
  const registrationFile = await fetch(origin + initialDashboard.payload.documents[0].previewUrl, { headers: auth });
  assert.equal(registrationFile.status, 200);
  assert.equal(registrationFile.headers.get('content-type'), 'image/png');
  const wrongPatientFile = await fetch(origin + initialDashboard.payload.documents[0].previewUrl.replace(patientId, 'AS-000001'), { headers: auth });
  assert.notEqual(wrongPatientFile.status, 200);

  const updateConditions = await json(`/api/patients/${patientId}/medical-conditions`, {
    method: 'PUT', headers: auth, body: JSON.stringify({ conditions: ['diabetes', 'high_cholesterol'] }),
  });
  assert.equal(updateConditions.response.status, 200);
  const dashboardAfterConditions = await json(`/api/patients/${patientId}/dashboard`, { headers: auth });
  assert.deepEqual(dashboardAfterConditions.payload.patient.health.conditions, ['diabetes', 'high_cholesterol']);

  const upload = await json(`/api/patients/${patientId}/documents`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({
      name: 'test-result.png',
      type: 'image/png',
      data: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    }),
  });
  assert.equal(upload.response.status, 201);
  assert.equal(upload.payload.document.name, 'test-result.png');

  const dashboard = await json(`/api/patients/${patientId}/dashboard`, { headers: auth });
  assert.equal(dashboard.payload.summary.documentCount, 2);
  assert.ok(dashboard.payload.documents.some(document => document.name === 'test-result.png' && document.source === 'patient'));

  const abhaLogin = await json('/api/patient-login', {
    method: 'POST', body: JSON.stringify({ identityMethod: 'abha', identityNumber: '80012345678901', epin: '654321' }),
  });
  assert.equal(abhaLogin.response.status, 200);

  await stopServer();
  await startServer();
  const restoredSessionDashboard = await json(`/api/patients/${patientId}/dashboard`, { headers: auth });
  assert.equal(restoredSessionDashboard.response.status, 200);
  assert.equal(restoredSessionDashboard.payload.patient.abhaLast4, '8901');
});

test('OTP login tells an unregistered patient to register first', async () => {
  const phone = '9876543299';
  const request = await json('/api/signup-otp/request', {
    method: 'POST', body: JSON.stringify({ phone, purpose: 'login' }),
  });
  assert.equal(request.response.status, 404);
  assert.equal(request.payload.error, 'No patient account is linked to this mobile number');

  const login = await json('/api/patient-login-otp', {
    method: 'POST', body: JSON.stringify({ phone, otpRequestId: 'missing', otpVerificationToken: 'missing' }),
  });
  assert.equal(login.response.status, 404);
  assert.equal(login.payload.error, 'No account found for this mobile number. Please register first.');
});
