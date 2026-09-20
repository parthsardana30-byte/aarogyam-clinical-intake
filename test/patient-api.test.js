import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
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
    }),
  });
  assert.equal(registration.response.status, 201);
  assert.match(registration.payload.patient.id, /^AS-\d{6}$/);
  assert.ok(registration.payload.sessionToken);

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
  assert.deepEqual(initialDashboard.payload.summary, { consultationCount: 0, documentCount: 0 });

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
  assert.equal(dashboard.payload.summary.documentCount, 1);
  assert.equal(dashboard.payload.documents[0].name, 'test-result.png');

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
