# PulseMind

**Chatbots wait. PulseMind thinks in the background at flash speed.**

PulseMind is a continuous background AI workspace for hackathon Problem Statement 1 — *Frontier Intelligence at Flash Speed*. A Java Spring Boot engine runs an **Observe → Reason → Verify → Act (ORVA)** loop against a live workspace, streaming every thought to a real-time React dashboard.

## Why this wins

| Judge lens | PulseMind answer |
|---|---|
| Unique idea | Not another chat UI — the product *is* the autonomous background loop |
| Gemini stack | Flash + Antigravity + TTS + Transcribe used for real work |
| Java fit | Concurrent watchers, scheduled ORVA loop, WebSocket fan-out |
| Demoable | One-click injectors recreate the wow path in under 2 minutes |
| No paywall | Open UI, no login |

## Architecture

```text
sample-service (files + logs)
        │
        ▼
 WorkspaceWatcher ──► EventBus ──► PulseLoopService (ORVA)
                                         │
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                    ▼
            gemini-3.8-flash   antigravity-preview    gemini-3.8-flash-tts
            (REASON)           -09-2026 (VERIFY)      (spoken ACT alerts)
                                         │
                                         ▼
                              WebSocket /ws/pulse
                                         │
                                         ▼
                              PulseMind React dashboard
                              (+ Transcribe voice bridge)
```

## Google AI stack

| Capability | Model / Agent |
|---|---|
| Continuous reason / review | `gemini-3.8-flash` |
| Deep verify / act | `antigravity-preview-09-2026` (Interactions API) |
| Spoken alerts | `gemini-3.8-flash-tts` |
| Voice commands | `gemini-3.5-transcribe` |

If an API key is missing or a model endpoint errors, **demo-fallback** keeps the loop alive so judges still see ORVA in action (UI badges show fallback).

## Quick start (local)

### 1. API key

```bash
copy .env.example .env
# set GEMINI_API_KEY=... from Google AI Studio
```

PowerShell:

```powershell
$env:GEMINI_API_KEY="your_key_here"
```

### 2. Backend (Java 17+)

```powershell
cd d:\geminiMinds
$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.11"
$env:PULSEMIND_WATCH_PATH="$PWD\sample-service"
$env:PULSEMIND_LOG_PATH="$PWD\sample-service\logs\application.log"
.\.tools\apache-maven-3.9.6\bin\mvn.cmd -f backend\pom.xml spring-boot:run
```

Or double-click `scripts\run-backend.bat` (after Maven tools are present under `.tools`).

Backend: `http://localhost:8080`

### 3. Frontend

```powershell
cd frontend
npm install
npm run dev
```

UI: `http://localhost:5173`

### Docker (one command)

```powershell
$env:GEMINI_API_KEY="your_key_here"
docker compose up --build
```

Open `http://localhost:3000` — no login.

## Judge demo script (90 seconds)

1. Open the UI — watch the **LIVE** orb and heartbeat observes.
2. Click **Run 90s demo sequence** (or Inject code fault → Spike logs → Deep verify).
3. Timeline fills with **OBSERVE → REASON → VERIFY → ACT**.
4. High/critical findings trigger TTS alerts (when the API key is present).
5. Optional: click mic / type `run a deep verify` for Transcribe → action.

## API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Liveness |
| GET | `/api/loop/status` | ORVA metrics |
| GET | `/api/events/recent` | Recent pulse events |
| POST | `/api/demo/inject/{type}` | `bug` · `logs` · `verify` · `sequence` |
| POST | `/api/voice/command` | Multipart audio → Transcribe → intent |
| POST | `/api/voice/text` | Text command shortcut |
| WS | `/ws/pulse` | Live event stream |

## Project layout

```text
backend/          Spring Boot ORVA engine
frontend/         React + Vite live workspace
sample-service/   Intentionally faulty Java target under watch
docker-compose.yml
```

## Cloud Run note (public URL for Attachments)

Build and deploy the backend image to Cloud Run, set `GEMINI_API_KEY`, mount/bake `sample-service`, then deploy the frontend nginx image with API/WS proxy — or host frontend on Cloud Storage + CDN pointing at the Cloud Run URL. Attach the public URL in the hackathon writeup **Attachments** section (no login/paywall).

## Pitch (copy for writeup)

> Most AI coding tools wait for a prompt. PulseMind never waits. A Java reactive engine continuously observes files and logs, reasons with Gemini 3.8 Flash, deep-verifies with the Antigravity managed agent, and acts — including spoken alerts — while the engineer keeps working. Judges experience a live ORVA timeline, not a chatbot.
