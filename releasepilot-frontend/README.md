# ReleasePilot Frontend

React + Vite frontend for ReleasePilot. This is the Phase 14.1 foundation: shared app shell, design tokens, appearance preferences, and Demo/Live API adapters.

## Requirements

- Node.js 22 or newer (Vite 8 requirement)
- The ReleasePilot Spring Boot backend for Live mode

## Run locally

```bash
npm install
npm run dev
```

Vite proxies `/api` requests to `http://localhost:8080` by default. Start the backend separately for Live mode.

## Configuration

Copy `.env.example` to `.env.local` if you want to override defaults.

- `VITE_API_BASE_URL`: optional API base URL. Empty uses same-origin `/api` requests.
- `VITE_DEFAULT_MODE`: `demo` (default) or `live`.
- `VITE_API_TIMEOUT_MS`: request timeout in milliseconds (default `15000`).

Vite environment values are public frontend configuration; never put credentials or secrets in them.

## Checks

```bash
npm run lint
npm run build
```

## Current scope

- Demo mode runs without a backend and labels its sample data.
- Live mode uses the backend health endpoint before showing a connected state.
- Read-only draft list, knowledge status/search, and the supported `/api/agent/ask` request are wired through the adapter.
- JWT tokens are held in memory only, not local storage.
- Theme and accent preference are saved locally on this device and are independent of one another.
- Release intelligence, complete authentication flows, admin UI, and release writes are scheduled for later frontend phases. The UI does not claim that a release was approved or published.
