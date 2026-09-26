# Deploy PulseMind (live URL for hackathon)

## Current live demo URL

**https://wild-garlics-live.loca.lt**

- First visit: localtunnel may show a warning page — enter IP **`49.200.108.74`** then Continue
- Keep this PC online with backend `:8080` + frontend `:5173` + tunnel process running
- No login / no paywall for judges

**GitHub (public code):** https://github.com/LvvNagendra/PulseMind

## How judges demo (60 seconds)

1. Open the live URL
2. Pass the tunnel IP check if shown
3. Wait for **LIVE / Stream live**
4. Click **Run 90s demo sequence**
5. Watch OBSERVE → REASON → VERIFY → ACT

## Security first

- Never share Google passwords in chat.
- Keep `GEMINI_API_KEY` only in local `.env` / server env (not in GitHub).

## Restart live tunnel later

```powershell
# terminal 1 — backend with .env loaded
cd d:\geminiMinds\backend
# java -jar target\pulsemind-engine-1.0.0.jar

# terminal 2 — frontend
cd d:\geminiMinds\frontend
npx vite --host 0.0.0.0 --port 5173

# terminal 3 — public URL
npx --yes localtunnel --port 5173
```

## Option — Docker / Cloud Run

See earlier sections in git history / README for `docker compose` and Cloud Run when you want a permanent `*.run.app` URL.
