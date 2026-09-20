# Aarogyam Patient Android

Kotlin/Jetpack Compose patient app in the same repository as the Aarogyam website and Node backend. It calls the existing `/api` routes and uses the same `data/patients.sqlite` database on the server; there is no separate app backend.

## Run on another PC

1. Clone the `application` branch and run `npm install` in the repository root.
2. Create a local `.env` from the root `.env.example`; copy real secrets separately. Never commit `.env`.
3. Start the backend with `npm start`. To display local demo OTPs for testing, set `NODE_ENV=development` and `OTP_DEMO_MODE=1` in `.env`.
4. Open `aarogyam-patient-android` in Android Studio and run the `app` configuration on an emulator.

The debug API base defaults to `http://10.0.2.2:4173`, which points from the Android emulator to the host PC. Override it with `AAROGYAM_DEBUG_API_BASE_URL` as a Gradle property or environment variable. The release base defaults to `https://aarogyam.129-121-127-58.sslip.io` and can be overridden with `AAROGYAM_RELEASE_API_BASE_URL`. Use the backend origin only; do not append `/#/roles`.

The unchanged website continues using patient passwords. The Android app uses a six-digit E-PIN. Both are accepted by the same backend login, registration and reset routes. A website account with an older nonnumeric password can use Android's OTP login or Forgot E-PIN to set a six-digit E-PIN. This changes the account's shared credential.

Production OTP uses the existing MSG91 Widget ID and Widget Token for Android's Kotlin SDK, and the server verifies its access token using `MSG91_AUTH_KEY`. Enable Mobile Integration on the MSG91 widget. The Auth Key belongs only in the server environment, never in Android source. No separate OTP template ID is required for the default widget flow.

## Current integration

- Phone + E-PIN login, OTP login, OTP-based E-PIN reset and registration
- Mandatory Aadhaar with clearly labeled demo verification in the app
- Patient dashboard, hospital visits, vitals, documents and record-access history
- ABHA linking and database-backed medical conditions
- SQLite-backed patient sessions surviving backend restarts

The scanner is intentionally not connected to the hospital QR backend yet. Some appointment/companion UI data is still local-only. Do not use this prototype for real patient care without a full clinical, privacy and security review.
