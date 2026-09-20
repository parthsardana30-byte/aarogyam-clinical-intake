const origin = String(process.env.AROGYAM_ORIGIN || 'http://localhost:4173').replace(/\/$/, '');

const patientCredentials = {
  identityMethod: 'aadhaar',
  identityNumber: '811122223333',
  password: '123456',
  phone: '9000000199'
};

const staffCredentials = {
  hospitalId: 'civil-ahmedabad',
  employeeId: 'DEMO-AHM-STAFF-01',
  password: 'DemoStaff@123',
  fullName: 'Neha Sharma',
  phone: '9000000299',
  email: 'demo.staff@aarogyam.test'
};

const doctorCredentials = {
  doctorId: 'CHA-GEN-1001',
  password: 'Aarogyam@2026'
};

async function request(path, options = {}) {
  const response = await fetch(`${origin}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  });
  const payload = await response.json().catch(() => ({}));
  return { response, payload };
}

async function ensurePatient() {
  let login = await request('/api/patient-login', {
    method: 'POST', body: JSON.stringify(patientCredentials)
  });
  if (login.response.ok) return login.payload;

  const otp = await request('/api/signup-otp/request', {
    method: 'POST', body: JSON.stringify({ phone: patientCredentials.phone })
  });
  if (!otp.response.ok || !otp.payload.demoOtp) {
    throw new Error(`Demo patient is missing and demo OTP is unavailable: ${otp.payload.error || otp.response.status}`);
  }
  const verification = await request('/api/signup-otp/verify', {
    method: 'POST',
    body: JSON.stringify({ phone: patientCredentials.phone, id: otp.payload.id, otp: otp.payload.demoOtp })
  });
  if (!verification.response.ok) throw new Error(verification.payload.error || 'Could not verify demo OTP');
  const registration = await request('/api/patient-registrations', {
    method: 'POST',
    body: JSON.stringify({
      ...patientCredentials,
      otpRequestId: otp.payload.id,
      otpVerificationToken: verification.payload.verificationToken,
      fullName: 'Demo Patient',
      dateOfBirth: '1995-01-15',
      gender: 'Male',
      heightCm: 172,
      weightKg: 70,
      bloodGroup: 'O+',
      conditions: ['Hypertension'],
      allergies: 'Penicillin'
    })
  });
  if (!registration.response.ok) throw new Error(registration.payload.error || 'Could not create demo patient');
  return registration.payload;
}

async function ensureStaff() {
  let login = await request('/api/staff-login', {
    method: 'POST',
    body: JSON.stringify({ employeeId: staffCredentials.employeeId, password: staffCredentials.password })
  });
  if (login.response.ok && login.payload.staff?.id === staffCredentials.employeeId) return login.payload.staff;

  const registration = await request('/api/staff-registrations', {
    method: 'POST', body: JSON.stringify(staffCredentials)
  });
  if (!registration.response.ok) {
    throw new Error(`Could not create demo staff: ${registration.payload.error || registration.response.status}`);
  }
  return registration.payload.staff;
}

async function authorizeDemoDevice(staffId) {
  const invitation = await request('/api/device-enrollments', {
    method: 'POST', body: JSON.stringify({ staffId })
  });
  if (!invitation.response.ok) throw new Error(invitation.payload.error || 'Could not create device invitation');

  const accessRequest = await request(`/api/device-enrollments/${invitation.payload.id}/request`, {
    method: 'POST',
    body: JSON.stringify({
      name: 'Ahmedabad Demo Kiosk',
      claimToken: invitation.payload.setupUrl.match(/[?&]token=([^&]+)/)?.[1]
        ? decodeURIComponent(invitation.payload.setupUrl.match(/[?&]token=([^&]+)/)[1])
        : '',
      platform: 'Arogyam demo seeder'
    })
  });
  if (!accessRequest.response.ok) throw new Error(accessRequest.payload.error || 'Could not request device access');

  const approval = await request(`/api/device-enrollments/${invitation.payload.id}/decision`, {
    method: 'POST',
    body: JSON.stringify({ creatorToken: invitation.payload.creatorToken, decision: 'approve' })
  });
  if (!approval.response.ok) throw new Error(approval.payload.error || 'Could not approve demo device');

  const status = await fetch(`${origin}/api/device-enrollments/${invitation.payload.id}/status?requestToken=${encodeURIComponent(accessRequest.payload.requestToken)}`);
  const payload = await status.json();
  const cookie = status.headers.get('set-cookie')?.split(';')[0];
  if (!status.ok || !cookie) throw new Error(payload.error || 'Authorized-device cookie was not issued');
  return cookie;
}

async function seed() {
  const patientLogin = await ensurePatient();
  const patient = patientLogin.patient;
  const patientSession = patientLogin.sessionToken;
  const staff = await ensureStaff();
  const deviceCookie = await authorizeDemoDevice(staff.id);

  const dashboard = await request(`/api/patients/${encodeURIComponent(patient.id)}/dashboard`, {
    headers: { Authorization: `Bearer ${patientSession}` }
  });
  if (dashboard.response.ok && !dashboard.payload.documents?.some(item => item.name === 'Previous consultation summary.pdf')) {
    const demoPdf = Buffer.from('%PDF-1.4\n% Arogyam demo previous consultation summary\n').toString('base64');
    const upload = await request(`/api/patients/${encodeURIComponent(patient.id)}/documents`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${patientSession}` },
      body: JSON.stringify({ name: 'Previous consultation summary.pdf', type: 'application/pdf', data: demoPdf })
    });
    if (!upload.response.ok) throw new Error(upload.payload.error || 'Could not create demo document');
  }

  const conversationId = `demo-clinical-flow-${patient.id}`;
  const intake = await request('/api/patient-intakes', {
    method: 'POST',
    headers: { Cookie: deviceCookie },
    body: JSON.stringify({
      patientId: patient.id,
      conversationId,
      language: 'English',
      intakeSource: 'kiosk',
      summary: 'Patient reports headache for three days, intermittent dizziness, and mild fatigue. No loss of consciousness, chest pain, breathlessness, fever, or recent injury. History of hypertension; missed two doses of regular medicine this week. Penicillin allergy reported. Patient is alert and oriented. Recommend clinician review of blood pressure, medication adherence, red-flag symptoms, and prior records.'
    })
  });
  if (!intake.response.ok) throw new Error(intake.payload.error || 'Could not save demo intake');

  const vitals = await request('/api/staff-patient-vitals', {
    method: 'POST',
    body: JSON.stringify({
      staffId: staff.id,
      patientId: patient.id,
      intakeId: intake.payload.intake.id,
      heartRate: 82,
      oxygenSaturation: 98,
      systolic: 146,
      diastolic: 92
    })
  });
  if (!vitals.response.ok) throw new Error(vitals.payload.error || 'Could not save demo vitals');

  const staffCompletion = await request(`/api/staff-intakes/${encodeURIComponent(intake.payload.intake.id)}/complete`, {
    method: 'POST', body: JSON.stringify({ staffId: staff.id })
  });
  if (!staffCompletion.response.ok) throw new Error(staffCompletion.payload.error || 'Could not complete demo staff preparation');

  const [staffQueue, doctorPatients] = await Promise.all([
    request(`/api/staff-patients?staffId=${encodeURIComponent(staff.id)}`),
    request(`/api/doctor-patients?doctorId=${encodeURIComponent(doctorCredentials.doctorId)}`)
  ]);
  const staffPatient = staffQueue.payload.patients?.find(item => item.id === patient.id);
  const doctorPatient = doctorPatients.payload.patients?.find(item => item.patientId === patient.id);
  if (staffPatient || !doctorPatient) throw new Error('Demo patient did not leave the staff queue and enter the doctor workflow');

  console.log(JSON.stringify({
    ready: true,
    hospital: 'Civil Hospital, Ahmedabad',
    patient: {
      name: patient.fullName,
      patientId: patient.id,
      loginMethod: 'Aadhaar',
      aadhaar: patientCredentials.identityNumber,
      password: patientCredentials.password,
      uhid: intake.payload.intake.uhid,
      encounterNumber: intake.payload.intake.encounterNumber
    },
    staff: { employeeId: staffCredentials.employeeId, password: staffCredentials.password },
    doctor: doctorCredentials,
    verified: {
      staffQueueCleared: true,
      doctorDashboard: true,
      vitals: doctorPatient.vitals,
      documents: doctorPatient.documents.length
    }
  }, null, 2));
}

seed().catch(error => {
  console.error(`Demo clinical flow failed: ${error.message}`);
  process.exitCode = 1;
});
