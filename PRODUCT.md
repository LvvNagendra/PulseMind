# PulseMind — Product explanation

## What is this application?

**PulseMind** is a **continuous background AI workspace**.  
Unlike ChatGPT-style tools that wait for a prompt, PulseMind keeps thinking while you work.

It runs an automatic loop:

**OBSERVE → REASON → VERIFY → ACT (ORVA)**

powered by Google Gemini models.

---

## Who uses this application?

| User | How they use it |
|---|---|
| **Software engineers / SRE** | Keep coding; PulseMind watches files/logs and flags bugs or outages in real time |
| **Tech leads / reviewers** | Review the live timeline of findings as an audit trail |
| **Hackathon judges** | Click one demo button and see Flash + Antigravity working live |
| **QA / testers** | Repeat inject scenarios and verify ORVA behavior from the UI |

---

## How to check it (UI)

1. Open **http://localhost:5173**
2. Confirm orb shows **LIVE**
3. Click **Run 90s demo sequence**
4. In **Sentinel timeline**, confirm cards for **OBSERVE → REASON → VERIFY → ACT**
5. Optional: type `run a deep verify` in Voice bridge → Send

Detailed tester steps: [`docs/UI_TEST_GUIDE.html`](docs/UI_TEST_GUIDE.html)

---

## What was missing (and now added)

| Gap | Fix |
|---|---|
| Unclear who the product is for | “Who uses PulseMind” section in UI + `/api/about` |
| How to verify quickly | “How to check (60 seconds)” panel in UI |
| Weak navigation / polish | Top bar, connection pill, toast feedback, phase filters |
| Product metadata API | `GET /api/about` and richer `GET /api/health` |
| Tester HTML guide | `docs/UI_TEST_GUIDE.html` |

---

## Architecture (simple)

- **Backend (Java Spring Boot):** watchers + ORVA loop + Gemini clients + WebSocket
- **Frontend (React):** live operations workspace
- **sample-service:** intentional-fault Java target for demos
