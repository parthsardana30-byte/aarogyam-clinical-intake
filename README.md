# Aarogyam Clinical Intake

A voice-first, evidence-linked patient intake prototype for AYUSH outpatient care.

## Collaboration workflow

- `main` contains the reviewed, stable version.
- `codex/web` is the Codex development branch.
- `antigravity/web` is the Antigravity development branch.
- Each agent should work in its own Git worktree and merge through `main` after review.
- Local previews do not require a push. Run a local server from the relevant worktree and refresh its browser tab.

## Local preview and device authorization backend

Install dependencies and start the Node server from the project root:

```powershell
npm install
npm start
```

The Codex preview runs at `http://127.0.0.1:4173`. Open
`http://127.0.0.1:4173/device-enroll.html` on a patient device to generate an
expiring QR code and six-digit authorization code. Hospital staff can approve
the device from **Add authorized device** on the staff dashboard. Once approved,
that browser is restricted to the patient portal and opens the existing check-up
start page after patient login.

Runtime options:

- `HOST` defaults to `127.0.0.1`. Use `0.0.0.0` only when the server is protected
  by a firewall or reverse proxy.
- `PORT` defaults to `4173`.
- `DATA_DIR` defaults to `./data` and stores authorized-device records.
- `ALLOW_TEST_DEVICE_CODE`, `TEST_DEVICE_CODE`, and
  `TEST_DEVICE_SESSION_HOURS` can temporarily enable instant device access for
  controlled testing. The feature is disabled by default, sessions are capped
  at 24 hours, and the code should be supplied only through the server environment.

Camera-based QR scanning requires HTTPS (or localhost). On a raw HTTP VPS/IP,
staff can use the six-digit manual code until TLS is configured.

## Patient check-up AI providers

The check-up screen offers **Qwen395b** (the existing ElevenLabs voice agent)
and the self-hosted Aarogyam AI when both are configured. Set
`ELEVENLABS_API_KEY` and `ELEVENLABS_AGENT_ID` for the existing agent. To enable
Aarogyam AI, set `AAROGYAM_API_URL` to the HTTPS API origin and
`AAROGYAM_API_KEY` to its bearer key in the **site server's environment** (or
Docker Compose `.env`), then restart the site. Never put either key in `dist/`.

Aarogyam text chat and voice use separate sessions from ElevenLabs. Switching
providers starts a new check-up conversation. Aarogyam voice records each user
utterance and sends it after a pause; it is turn-based, not a full-duplex call.
The selected provider's transcript is used for the clinician summary.
The hosted ElevenLabs intake prompt and Focus guardrail are recorded in
[`docs/elevenlabs-intake-agent.md`](docs/elevenlabs-intake-agent.md). This is an
operator reference; changing the file does not automatically update ElevenLabs.

The current prototype uses synthetic demonstration records only. It must not be used to store real patient information.

## Patient intake and test doctor accounts

On an authorized device, a patient completes the AI conversation and sees a saved intake summary with the instruction **Please proceed to reception**. Each saved summary is linked to the authorized device and to the staff account that approved that device. The staff dashboard shows only patients and summaries generated on that staff member's authorized devices, including every saved summary for a patient. The app no longer assigns a doctor, generates an OPD/token number, or manages a consultation queue. Existing completed doctor consultations remain available as read-only historical records; older queued records are retained in storage but are no longer processed by this flow.

The local server seeds one doctor for every selectable specialty at Civil Hospital Ahmedabad, plus a General Medicine demo doctor for every hospital branch. A doctor sees only patients whose intake belongs to the same hospital. All demo doctor accounts use the password `Aarogyam@2026`.

| Hospital branch | Demo doctor ID |
| --- | --- |
| Civil Hospital · Ahmedabad | `CHA-DEMO-1000` |
| Civil Hospital · Gurugram | `CHG-DEMO-1000` |
| Civil Hospital · Ludhiana | `CHL-DEMO-1000` |
| Civil Hospital · Nashik | `CHN-DEMO-1000` |
| Civil Hospital · Rajkot | `CHR-DEMO-1000` |

| Specialty | Doctor ID | Doctor | Room |
| --- | --- | --- | --- |
| General Medicine | `CHA-GEN-1001` | Dr. Aarav Mehta | G-101 |
| Gynaecology | `CHA-GYN-1002` | Dr. Meera Kapoor | GY-201 |
| Orthopaedics | `CHA-ORT-1003` | Dr. Nisha Rao | OR-301 |
| Paediatrics | `CHA-PED-1004` | Dr. Kabir Shah | P-102 |
| General Surgery | `CHA-SUR-1005` | Dr. Rohan Desai | S-204 |
| Cardiology | `CHA-CAR-1006` | Dr. Isha Verma | C-110 |
| Dermatology | `CHA-DER-1007` | Dr. Neel Joshi | D-205 |
| ENT | `CHA-ENT-1008` | Dr. Sana Khan | E-106 |
| Ophthalmology | `CHA-OPH-1009` | Dr. Arjun Patel | O-208 |
| Psychiatry | `CHA-PSY-1010` | Dr. Riya Sen | PS-305 |
| AYUSH Medicine | `CHA-AYU-1011` | Dr. Dev Sharma | A-109 |
| Other | `CHA-OTH-1012` | Dr. Tara Nair | M-210 |
