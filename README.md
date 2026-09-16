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

Camera-based QR scanning requires HTTPS (or localhost). On a raw HTTP VPS/IP,
staff can use the six-digit manual code until TLS is configured.

The current prototype uses synthetic demonstration records only. It must not be used to store real patient information.
