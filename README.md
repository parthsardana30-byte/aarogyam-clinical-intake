# Aarogyam Clinical Intake

A voice-first, evidence-linked patient intake prototype for AYUSH outpatient care.

## Collaboration workflow

- `main` contains the reviewed, stable version.
- `codex/web` is the Codex development branch.
- `antigravity/web` is the Antigravity development branch.
- Each agent should work in its own Git worktree and merge through `main` after review.
- Local previews do not require a push. Run a local server from the relevant worktree and refresh its browser tab.

## Local preview

Serve the `dist` directory from each worktree on a different port:

- Codex: `http://127.0.0.1:4173`
- Antigravity: `http://127.0.0.1:4174`

The current prototype uses synthetic demonstration records only. It must not be used to store real patient information.
