# Hackathon writeup — PulseMind

## Problem statement

**1. Frontier Intelligence at Flash Speed** (recommended for Java) — `gemini-3.8-flash`

## Short overview

PulseMind is a continuous background AI workspace for **engineers, SREs, tech leads, QA, and hackathon judges**. While a developer works, a Spring Boot engine watches files and logs, then runs an autonomous **Observe → Reason → Verify → Act** loop using Gemini — streaming every decision to a live UI over WebSockets.

## Who uses it

- Engineers/SRE — catch bugs and log anomalies without stopping to chat
- Tech leads — live audit trail of risks
- Judges — one-click demo of Flash + Antigravity
- QA — repeatable inject scenarios from the UI

## Demo

**Live demo (global):** https://wild-garlics-live.loca.lt

First browser visit may ask for a tunnel password — enter this IP once: `49.200.108.74`

Then:
1. Confirm orb / status shows LIVE
2. Click **Run 90s demo sequence** or **Scan watched project**
3. Watch OBSERVE → REASON → VERIFY → ACT in the timeline

**GitHub:** https://github.com/LvvNagendra/PulseMind

1. Start with Docker Compose or local scripts (see README)
2. Open the UI (no login)
3. Confirm orb = LIVE
4. Click **Run 90s demo sequence**
5. Confirm timeline ORVA cards appear

See also: `PRODUCT.md` and `docs/UI_TEST_GUIDE.html`

## Value proposition

Chat-based coding assistants only think when asked. PulseMind thinks *continuously*, at Flash speed, so issues are found and verified before the engineer context-switches to ask.

## What we built

- Java ORVA engine (watchers + concurrent loop + WebSocket fan-out)
- Gemini 3.8 Flash for fast reason/review
- Antigravity Agent (`antigravity-preview-09-2026`) via Interactions API for deep verify
- Gemini Flash TTS for spoken critical alerts
- Gemini 3.5 Transcribe for hands-free voice commands
- One-click demo injectors for judge-friendly wow path

## Demo

1. Start with Docker Compose or local scripts (see README)
2. Open the UI (no login)
3. Click **Run 90s demo sequence**
4. Watch OBSERVE → REASON → VERIFY → ACT stream live

## Attachments

- Source repo / zip of this project
- Public demo URL (Cloud Run / Docker host) — *add after you deploy*
- Optional: terminal recording of `docker compose up` + injector run

## Architecture

See README mermaid/ASCII diagram. Backend on Java 17 Spring Boot 3; frontend Vite React; watched target is `sample-service`.
