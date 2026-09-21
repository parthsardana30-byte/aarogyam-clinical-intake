# Aarogyam Patient Android

Kotlin/Jetpack Compose patient app. This repository contains only the Android frontend. It calls the `/api` routes of the Aarogyam website backend, which stores patients in its own database on the server. There is no separate app backend in this repository.

## Run on another PC

1. Clone `https://github.com/aryanatharv/application.git`.
2. Open the cloned repository root in Android Studio and run the `app` configuration on a phone or emulator. A debug APK is available at `app/build/outputs/apk/debug/app-debug.apk` after building.
3. For real OTP testing without a VPS deployment, create `local-secrets.properties` in the Android project root (it is Git-ignored) with `MSG91_MOBILE_WIDGET_ID` and `MSG91_MOBILE_WIDGET_TOKEN` for a mobile-enabled MSG91 widget. Rebuild the APK after adding them. The existing VPS backend must already support `/api/signup-otp/verify` with an `accessToken` and have its server-side `MSG91_AUTH_KEY` configured.

Both debug and release APKs default to the VPS API origin `https://aarogyam.129-121-127-58.sslip.io`. They call `/api` on that origin, not the website's `/#/roles` page. Override with `AAROGYAM_DEBUG_API_BASE_URL` or `AAROGYAM_RELEASE_API_BASE_URL` as a Gradle property or environment variable if the host changes. For local emulator testing only, set `AAROGYAM_DEBUG_API_BASE_URL=http://10.0.2.2:4173` and run the website backend on the PC; its development `.env` may use `NODE_ENV=development` and `OTP_DEMO_MODE=1` for demo OTPs.

An installed phone needs internet access and a valid HTTPS certificate for the VPS origin. Its backend must have the required `/api` routes; changing the APK URL alone does not deploy backend changes to the VPS.

The unchanged website continues using patient passwords. The Android app uses a six-digit E-PIN. Both are accepted by the same backend login, registration and reset routes. A website account with an older nonnumeric password can use Android's OTP login or Forgot E-PIN to set a six-digit E-PIN. This changes the account's shared credential.

Production OTP uses a separate MSG91 mobile widget with Mobile Integration enabled. The website keeps its existing widget. For the current app-only test, Android reads `MSG91_MOBILE_WIDGET_ID` and `MSG91_MOBILE_WIDGET_TOKEN` from the Git-ignored `local-secrets.properties` at build time (or matching Gradle properties/environment variables). If those are absent, it fetches mobile configuration from `/api/signup-otp/config?client=android`, which requires the newer backend deployment. The server verifies the resulting access token using its existing `MSG91_AUTH_KEY`. The Auth Key belongs only on the backend, never in Android source or its local secrets file. The mobile widget token is embedded in the built APK as required by this SDK and can be extracted, so distribute the APK accordingly. No separate OTP template ID is required for the widget flow.

## Current integration

- Phone + E-PIN login, OTP login, OTP-based E-PIN reset and registration
- Mandatory Aadhaar with clearly labeled demo verification in the app
- Patient dashboard, hospital visits, vitals, documents and record-access history
- Patient-uploaded PDF/images are stored on the shared backend; the app can list, view securely in-app, and save copies to the phone. Records uploaded through the website's registration flow or by hospital staff/doctor appear after the app refreshes.
- Patients can delete their own uploads after the backend's `DELETE /api/patients/:patientId/documents/:documentId` route is deployed. Hospital and doctor records remain protected from patient deletion. The website reads the same dashboard, so deletions appear there after it refreshes; no website UI change is required.
- ABHA linking and database-backed medical conditions
- SQLite-backed patient sessions surviving backend restarts
- QR-gated AI intake without a VPS deployment: sign into the website staff portal, generate its existing authorized-device QR, and scan it with the app scanner. Enter a device name in the in-app authorization page and have staff approve the request in the portal. Once the app confirms that the phone is authorized, scan a second QR containing exactly `arogyam://intake?code=AROGYAM-AI-CHECKUP-V1`. The app then talks or types with the same ElevenLabs agent used by the website and saves the clinical summary through the shared `/api/patient-intakes` route. The device remains authorized until staff revokes it in the portal. The fixed AI QR is only an app-side entry gate; it does not itself authorize the device on the server.

The reusable AI QR is a testing shortcut; anyone with a copy can open the chat UI on an already-authorized phone. For later rollout, the backend also has a rotating-QR page at `https://aarogyam.129-121-127-58.sslip.io/hospital-intake-qr`. It creates server-issued, one-use codes that expire after 25 seconds and rotates the display every 10 seconds on an authorized hospital device, but that flow requires a VPS deployment. A photo of even a rotating QR could be relayed, so QR alone does not prove physical presence. The ElevenLabs key, agent ID, and Gemini key remain on the backend; do not put them in the APK. The website and app store the same saved clinical summary, not a raw audio recording. Appointment/companion UI data is still local-only. Do not use this prototype for real patient care without a full clinical, privacy and security review.
