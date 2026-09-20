import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { after, before, test } from 'node:test';

const port = 43174;
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
      if ((await fetch(`${origin}/api/signup-otp/config`)).ok) return;
    } catch { /* Server is still starting. */ }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Test server did not start');
}

async function registerPatient() {
  const phone = '9876543211';
  const otp = await json('/api/signup-otp/request', { method: 'POST', body: JSON.stringify({ phone }) });
  const verification = await json('/api/signup-otp/verify', {
    method: 'POST', body: JSON.stringify({ phone, id: otp.payload.id, otp: otp.payload.demoOtp }),
  });
  const registration = await json('/api/patient-registrations', {
    method: 'POST', body: JSON.stringify({
      phone, otpRequestId: otp.payload.id, otpVerificationToken: verification.payload.verificationToken,
      identityMethod: 'aadhaar', identityNumber: '800123456780', epin: '123456',
      fullName: 'Hospital Identity Patient', dateOfBirth: '1990-04-12', gender: 'Male',
      heightCm: 172, weightKg: 70, bloodGroup: 'B+', conditions: ['none'], allergies: 'None',
    }),
  });
  assert.equal(registration.response.status, 201);
  return { ...registration.payload.patient, sessionToken: registration.payload.sessionToken };
}

async function authorizeDevice(staffId, name, staffCookie) {
  const invitation = await json('/api/device-enrollments', {
    method: 'POST', headers: { Cookie: staffCookie }, body: JSON.stringify({ staffId })
  });
  assert.equal(invitation.response.status, 201);
  const request = await json('/api/device-enrollment-requests', {
    method: 'POST', body: JSON.stringify({ name, code: invitation.payload.code, platform: 'Integration test' }),
  });
  assert.equal(request.response.status, 201);
  const decision = await json(`/api/device-enrollments/${invitation.payload.id}/decision`, {
    method: 'POST', body: JSON.stringify({ creatorToken: invitation.payload.creatorToken, decision: 'approve' }),
  });
  assert.equal(decision.response.status, 201);
  const status = await fetch(`${origin}/api/device-enrollments/${invitation.payload.id}/status?requestToken=${encodeURIComponent(request.payload.requestToken)}`);
  assert.equal(status.status, 200);
  const cookie = status.headers.get('set-cookie')?.split(';')[0];
  assert.match(cookie || '', /^arog_device=/);
  return cookie;
}

before(async () => {
  dataDir = await mkdtemp(join(tmpdir(), 'aarogyam-uhid-test-'));
  server = spawn(process.execPath, ['server.js'], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: String(port), HOST: '127.0.0.1', DATA_DIR: dataDir, OTP_DEMO_MODE: '1' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await waitForServer();
});

after(async () => {
  if (server && server.exitCode === null) {
    server.kill();
    await new Promise(resolve => server.once('exit', resolve));
  }
  await rm(dataDir, { recursive: true, force: true });
});

test('UHIDs are stable per hospital and clinical data reaches that hospital', async () => {
  const staffA = await json('/api/staff-registrations', {
    method: 'POST', body: JSON.stringify({
      hospitalId: 'civil-ahmedabad', employeeId: 'STAFF-A', password: 'Password@1',
      fullName: 'Ahmedabad Staff', phone: '9000000101', email: 'staff-a@example.test',
    }),
  });
  const staffB = await json('/api/staff-registrations', {
    method: 'POST', body: JSON.stringify({
      hospitalId: 'civil-gurugram', employeeId: 'STAFF-B', password: 'Password@1',
      fullName: 'Gurugram Staff', phone: '9000000102', email: 'staff-b@example.test',
    }),
  });
  assert.equal(staffA.response.status, 201);
  assert.equal(staffB.response.status, 201);
  const staffCookieA = staffA.response.headers.get('set-cookie').split(';')[0];
  const staffCookieB = staffB.response.headers.get('set-cookie').split(';')[0];
  const wrongStaffId = await json('/api/staff-login', {
    method: 'POST', body: JSON.stringify({ employeeId: 'NOT-STAFF-A', password: 'Password@1' }),
  });
  assert.equal(wrongStaffId.response.status, 401);
  const [cookieA, cookieB, patient] = await Promise.all([
    authorizeDevice('STAFF-A', 'Ahmedabad kiosk', staffCookieA),
    authorizeDevice('STAFF-B', 'Gurugram kiosk', staffCookieB),
    registerPatient(),
  ]);

  const patientLogin = await json('/api/patient-login', {
    method: 'POST', headers: { Cookie: cookieA },
    body: JSON.stringify({ identityMethod: 'aadhaar', identityNumber: '800123456780', epin: '123456' }),
  });
  assert.equal(patientLogin.response.status, 200);
  assert.equal(patientLogin.payload.patient.id, patient.id);
  const kioskDashboard = await json(`/api/patients/${patient.id}/dashboard`, {
    headers: { Cookie: cookieA, Authorization: `Bearer ${patient.sessionToken}` },
  });
  assert.equal(kioskDashboard.response.status, 403);
  const kioskStaff = await json('/api/staff-patients?staffId=STAFF-A', {
    headers: { Cookie: `${cookieA}; ${staffCookieA}` },
  });
  assert.equal(kioskStaff.response.status, 403);

  const firstA = await json('/api/patient-intakes', {
    method: 'POST', headers: { Cookie: cookieA, Authorization: `Bearer ${patient.sessionToken}` },
    body: JSON.stringify({ patientId: patient.id, conversationId: 'conversation-a-1', language: 'English', summary: 'Headache for two days. No fever reported.' }),
  });
  assert.equal(firstA.response.status, 201);
  assert.match(firstA.payload.intake.uhid, /^CHA-\d{6}$/);
  assert.equal(firstA.payload.intake.uhidCreated, true);
  const intakeWithoutPatientLogin = await json('/api/patient-intakes', {
    method: 'POST', headers: { Cookie: cookieA },
    body: JSON.stringify({ patientId: patient.id, conversationId: 'unauthorized', summary: 'Should not be saved' }),
  });
  assert.equal(intakeWithoutPatientLogin.response.status, 401);

  const retryA = await json('/api/patient-intakes', {
    method: 'POST', headers: { Cookie: cookieA, Authorization: `Bearer ${patient.sessionToken}` },
    body: JSON.stringify({ patientId: patient.id, conversationId: 'conversation-a-1', language: 'English', summary: 'Headache for two days. No fever reported.' }),
  });
  assert.equal(retryA.payload.intake.uhid, firstA.payload.intake.uhid);
  assert.equal(retryA.payload.intake.encounterNumber, firstA.payload.intake.encounterNumber);

  const secondA = await json('/api/patient-intakes', {
    method: 'POST', headers: { Cookie: cookieA, Authorization: `Bearer ${patient.sessionToken}` },
    body: JSON.stringify({ patientId: patient.id, conversationId: 'conversation-a-2', language: 'Hindi', summary: 'Follow-up intake completed.' }),
  });
  assert.equal(secondA.payload.intake.uhid, firstA.payload.intake.uhid);
  assert.notEqual(secondA.payload.intake.encounterNumber, firstA.payload.intake.encounterNumber);

  const firstB = await json('/api/patient-intakes', {
    method: 'POST', headers: { Cookie: cookieB, Authorization: `Bearer ${patient.sessionToken}` },
    body: JSON.stringify({ patientId: patient.id, conversationId: 'conversation-b-1', language: 'English', summary: 'First Gurugram intake completed.' }),
  });
  assert.match(firstB.payload.intake.uhid, /^CHG-\d{6}$/);
  assert.notEqual(firstB.payload.intake.uhid, firstA.payload.intake.uhid);

  const vitals = await json('/api/staff-patient-vitals', {
    method: 'POST', headers: { Cookie: staffCookieA }, body: JSON.stringify({
      staffId: 'STAFF-A', patientId: patient.id, intakeId: secondA.payload.intake.id,
      heartRate: 78, oxygenSaturation: 98, systolic: 122, diastolic: 80,
    }),
  });
  assert.equal(vitals.response.status, 201);

  const documentSession = await json('/api/document-upload-sessions', { method: 'POST', body: '{}' });
  const documentUpload = await json(`/api/document-upload-sessions/${documentSession.payload.id}/files`, {
    method: 'POST', body: JSON.stringify({
      token: documentSession.payload.token, name: 'previous-prescription.png', type: 'image/png',
      data: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    }),
  });
  assert.equal(documentUpload.response.status, 201);
  const completedDocument = await json('/api/staff-patient-documents', {
    method: 'POST', headers: { Cookie: staffCookieA }, body: JSON.stringify({
      staffId: 'STAFF-A', patientId: patient.id, patientName: patient.fullName,
      intakeId: secondA.payload.intake.id,
      documentCategory: 'lab',
      documentSessionId: documentSession.payload.id, documentSessionToken: documentSession.payload.token,
    }),
  });
  assert.equal(completedDocument.response.status, 200);

  const queueA = await json('/api/staff-patients?staffId=STAFF-A', { headers: { Cookie: staffCookieA } });
  const queueB = await json('/api/staff-patients?staffId=STAFF-B', { headers: { Cookie: staffCookieB } });
  assert.equal(queueA.payload.patients[0].uhid, firstA.payload.intake.uhid);
  assert.equal(queueA.payload.patients[0].latestVitals.heartRate, 78);
  assert.equal(queueB.payload.patients[0].uhid, firstB.payload.intake.uhid);
  const wrongStaffQueue = await json('/api/staff-patients?staffId=STAFF-B', { headers: { Cookie: staffCookieA } });
  assert.equal(wrongStaffQueue.response.status, 401);
  const staffC = await json('/api/staff-registrations', {
    method: 'POST', body: JSON.stringify({
      hospitalId: 'civil-ahmedabad', employeeId: 'STAFF-C', password: 'Password@1',
      fullName: 'Other Ahmedabad Staff', phone: '9000000103', email: 'staff-c@example.test',
    }),
  });
  const staffCookieC = staffC.response.headers.get('set-cookie').split(';')[0];
  const queueC = await json('/api/staff-patients?staffId=STAFF-C', { headers: { Cookie: staffCookieC } });
  assert.deepEqual(queueC.payload.patients, []);
  const crossStaffVitals = await json('/api/staff-patient-vitals', {
    method: 'POST', headers: { Cookie: staffCookieC }, body: JSON.stringify({
      staffId: 'STAFF-C', patientId: patient.id, intakeId: secondA.payload.intake.id,
      heartRate: 78, oxygenSaturation: 98, systolic: 122, diastolic: 80,
    }),
  });
  assert.equal(crossStaffVitals.response.status, 404);

  const vitalsB = await json('/api/staff-patient-vitals', {
    method: 'POST', headers: { Cookie: staffCookieB }, body: JSON.stringify({
      staffId: 'STAFF-B', patientId: patient.id, intakeId: firstB.payload.intake.id,
      heartRate: 76, oxygenSaturation: 99, systolic: 118, diastolic: 76,
    }),
  });
  assert.equal(vitalsB.response.status, 201);
  const staffCompletionB = await json(`/api/staff-intakes/${firstB.payload.intake.id}/complete`, {
    method: 'POST', headers: { Cookie: staffCookieB }, body: JSON.stringify({ staffId: 'STAFF-B' }),
  });
  assert.equal(staffCompletionB.response.status, 200);

  const doctorLogin = await json('/api/doctor-login', {
    method: 'POST', body: JSON.stringify({ doctorId: 'CHA-GEN-1001', password: 'Aarogyam@2026' }),
  });
  assert.equal(doctorLogin.response.status, 200);
  const doctorCookie = doctorLogin.response.headers.get('set-cookie').split(';')[0];
  const doctorBeforeStaffCompletion = await json('/api/doctor-patients?doctorId=CHA-GEN-1001', { headers: { Cookie: doctorCookie } });
  assert.equal(doctorBeforeStaffCompletion.payload.patients.some(record => record.uhid === firstA.payload.intake.uhid), false);
  const staffCompletion = await json(`/api/staff-intakes/${secondA.payload.intake.id}/complete`, {
    method: 'POST', headers: { Cookie: staffCookieA }, body: JSON.stringify({ staffId: 'STAFF-A' }),
  });
  assert.equal(staffCompletion.response.status, 200);
  const queueAfterCompletion = await json('/api/staff-patients?staffId=STAFF-A', { headers: { Cookie: staffCookieA } });
  assert.equal(queueAfterCompletion.payload.patients.some(record => record.uhid === firstA.payload.intake.uhid), false);

  const doctor = await json('/api/doctor-patients?doctorId=CHA-GEN-1001', { headers: { Cookie: doctorCookie } });
  assert.equal(doctor.response.status, 200);
  assert.equal(doctor.payload.patients[0].uhid, firstA.payload.intake.uhid);
  assert.equal(doctor.payload.patients[0].vitals.oxygenSaturation, 98);
  assert.equal(doctor.payload.patients[0].documents[0].name, 'previous-prescription.png');
  assert.equal(doctor.payload.patients.some(record => record.uhid === firstB.payload.intake.uhid), false);
  const anonymousDoctorQueue = await json('/api/doctor-patients?doctorId=CHA-GEN-1001');
  assert.equal(anonymousDoctorQueue.response.status, 401);

  const gurugramLogin = await json('/api/doctor-login', {
    method: 'POST', body: JSON.stringify({ doctorId: 'CHG-DEMO-1000', password: 'Aarogyam@2026' }),
  });
  const gurugramCookie = gurugramLogin.response.headers.get('set-cookie').split(';')[0];
  const gurugramDoctor = await json('/api/doctor-patients?doctorId=CHG-DEMO-1000', { headers: { Cookie: gurugramCookie } });
  assert.equal(gurugramDoctor.response.status, 200);
  assert.equal(gurugramDoctor.payload.doctor.hospitalId, 'civil-gurugram');
  assert.equal(gurugramDoctor.payload.patients.some(record => record.uhid === firstB.payload.intake.uhid), true);
  assert.equal(gurugramDoctor.payload.patients.some(record => record.uhid === firstA.payload.intake.uhid), false);
  const wrongDoctorQueue = await json('/api/doctor-patients?doctorId=CHG-DEMO-1000', { headers: { Cookie: doctorCookie } });
  assert.equal(wrongDoctorQueue.response.status, 401);

  const clinicalAnswer = await json('/api/doctor-clinical-assistant', {
    method: 'POST', headers: { Cookie: doctorCookie }, body: JSON.stringify({
      doctorId: 'CHA-GEN-1001', patientId: patient.id,
      question: 'What should I clarify first from the current intake and previous history?',
    }),
  });
  assert.equal(clinicalAnswer.response.status, 200);
  assert.match(clinicalAnswer.payload.answer, /Current intake/);
  assert.equal(clinicalAnswer.payload.groundedIn.hasVitals, true);
  assert.equal(clinicalAnswer.payload.groundedIn.documentCount, 1);

  const nonMedicalQuestion = await json('/api/doctor-clinical-assistant', {
    method: 'POST', headers: { Cookie: doctorCookie }, body: JSON.stringify({
      doctorId: 'CHA-GEN-1001', patientId: patient.id, question: 'Write a poem about the weather',
    }),
  });
  assert.equal(nonMedicalQuestion.response.status, 422);
  assert.match(nonMedicalQuestion.payload.error, /only medical questions/i);

  const prescription = await json('/api/doctor-evaluations/complete', {
    method: 'POST', headers: { Cookie: doctorCookie }, body: JSON.stringify({
      doctorId: 'CHA-GEN-1001', patientId: patient.id, intakeId: secondA.payload.intake.id,
      reviewedSummary: 'Doctor-reviewed headache consultation summary.',
      clinical: { chiefComplaint: 'Headache for two days', diagnosis: 'Tension-type headache', allergies: 'Penicillin' },
      medications: [{
        name: 'Paracetamol', strength: '500 mg', form: 'Tablet', dose: '1 tablet', route: 'Oral',
        frequency: 'Twice daily', timing: 'After food', duration: '3 days', quantity: '6', refills: '0',
      }],
      instructions: {
        investigations: 'Check blood pressure', lifestyle: 'Hydration and rest',
        precautions: 'Return for severe headache or weakness', followUpDate: '2026-09-25',
      },
    }),
  });
  assert.equal(prescription.response.status, 201);
  assert.match(prescription.payload.prescriptionId, /^RX-\d{4}-\d{6}$/);

  const dashboard = await json(`/api/patients/${patient.id}/dashboard`, {
    headers: { Authorization: `Bearer ${patientLogin.payload.sessionToken}` },
  });
  assert.equal(dashboard.response.status, 200);
  assert.equal(dashboard.payload.summary.consultationCount, 1);
  assert.equal(dashboard.payload.visits.length, 3);
  const ahmedabadVisit = dashboard.payload.visits.find(item => item.id === secondA.payload.intake.id);
  assert.equal(ahmedabadVisit.summary, 'Doctor-reviewed headache consultation summary.');
  assert.equal(ahmedabadVisit.evaluationStatus, 'completed');
  assert.equal(ahmedabadVisit.vitals.heartRate, 78);
  assert.equal(ahmedabadVisit.doctor.name, 'Dr. Aarav Mehta');
  assert.equal(ahmedabadVisit.documents.some(item => item.category === 'lab'), true);
  assert.equal(dashboard.payload.accessHistory.some(item => item.doctor?.id === 'CHA-GEN-1001'), true);
  const prescriptionDocument = dashboard.payload.documents.find(item => item.name.includes(prescription.payload.prescriptionId));
  assert.equal(prescriptionDocument.type, 'application/pdf');
  assert.equal(prescriptionDocument.category, 'prescription');
  const pdf = await fetch(`${origin}${prescriptionDocument.previewUrl}`, {
    headers: { Authorization: `Bearer ${patientLogin.payload.sessionToken}` },
  });
  assert.equal(pdf.status, 200);
  assert.equal((await pdf.text()).startsWith('%PDF-1.4'), true);
  const staffLogout = await json('/api/logout', { method: 'POST', headers: { Cookie: staffCookieA } });
  assert.equal(staffLogout.response.status, 200);
  const afterLogout = await json('/api/staff-patients?staffId=STAFF-A', { headers: { Cookie: staffCookieA } });
  assert.equal(afterLogout.response.status, 401);
});
