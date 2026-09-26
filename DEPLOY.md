# Deploy PulseMind (live URL for hackathon)

## Security first

- **Never share Google passwords in chat.** Change the password for `devstar4620@gcplab.me` immediately.
- Create a **new** Gemini API key at https://aistudio.google.com/api-keys after you sign in yourself.
- Put the key only in server env vars (`GEMINI_API_KEY`). Never commit `.env` or put the key in frontend `VITE_*` vars.

## Option A — Docker on any VPS (fastest public URL)

```bash
# on a cloud VM with Docker
git clone <your-github-repo-url>
cd geminiMinds
export GEMINI_API_KEY=your_new_key
docker compose up --build -d
```

Public UI: `http://YOUR_VM_IP:3000`  
Backend API: `http://YOUR_VM_IP:8080`

## Option B — Google Cloud Run (good for GCP lab accounts)

1. Sign in yourself in browser to Google Cloud / AI Studio (do not paste passwords into tools).
2. Create/select a GCP project.
3. Enable Cloud Run + Artifact Registry.
4. Build & deploy backend:

```bash
gcloud auth login   # interactive in YOUR terminal
gcloud config set project YOUR_PROJECT_ID
gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/pulsemind-backend ./backend
gcloud run deploy pulsemind-backend \
  --image gcr.io/YOUR_PROJECT_ID/pulsemind-backend \
  --allow-unauthenticated \
  --set-env-vars GEMINI_API_KEY=YOUR_NEW_KEY,PULSEMIND_WATCH_PATH=/workspace/sample-service,PULSEMIND_LOG_PATH=/workspace/sample-service/logs/application.log \
  --port 8080
```

5. Deploy frontend (set `VITE_API_BASE` / `VITE_WS_URL` to the Cloud Run backend URL at build time), or keep nginx compose image proxying `/api` and `/ws`.

Attach the **https://….run.app** URL in hackathon **Attachments** (no login wall).

## Option C — Local demo recording (if cloud deploy blocked)

If lab quotas block deploy:

1. Run locally: backend `:8080` + frontend `:5173`
2. Record a 90s Loom / terminal video of **Run 90s demo sequence**
3. Submit video + GitHub repo link in Attachments

## GitHub push (required for submission)

```powershell
cd d:\geminiMinds
gh auth login          # YOUR interactive login (browser)
git init
git add .
git commit -m "Add PulseMind continuous Gemini ORVA workspace"
gh repo create PulseMind --public --source=. --remote=origin --push
```

Confirm `.env` is **not** in the commit (`git status` should ignore it).

## After you have a live URL

1. Open the URL (no login).
2. Confirm **Stream live**.
3. Click **Run 90s demo sequence**.
4. Paste URL into Devpost/hackathon writeup Attachments.
5. Keep `HACKATHON_WRITEUP.md` + `PRODUCT.md` + `docs/UI_TEST_GUIDE.html` with the submission.
