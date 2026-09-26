import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { LoopStatus, Phase, PulseEvent } from './types'

const API = import.meta.env.VITE_API_BASE ?? ''

function wsUrl() {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL as string
  // Same-origin so Vite/nginx proxy /ws works for local + public tunnels
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws/pulse`
}

function playAudioBase64(b64: string) {
  try {
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
    const blob = new Blob([bytes], { type: 'audio/wav' })
    const url = URL.createObjectURL(blob)
    const audio = new Audio(url)
    audio.play().catch(() => {})
  } catch {
    // ignore invalid audio
  }
}

type AboutPayload = {
  name: string
  tagline: string
  summary: string
  whoUsesIt: { role: string; why: string }[]
  howItWorks: string[]
  howToCheck: string[]
}

export default function App() {
  const [events, setEvents] = useState<PulseEvent[]>([])
  const [status, setStatus] = useState<LoopStatus | null>(null)
  const [about, setAbout] = useState<AboutPayload | null>(null)
  const [connected, setConnected] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [voiceNote, setVoiceNote] = useState('')
  const [recording, setRecording] = useState(false)
  const [phaseFilter, setPhaseFilter] = useState<'ALL' | Phase>('ALL')
  const [toast, setToast] = useState<string | null>(null)
  const mediaRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const seenAudio = useRef(new Set<string>())

  const showToast = (msg: string) => {
    setToast(msg)
    window.setTimeout(() => setToast(null), 3200)
  }

  const pushEvent = useCallback((ev: PulseEvent) => {
    setEvents((prev) => {
      if (prev.some((p) => p.id === ev.id)) return prev
      return [ev, ...prev].slice(0, 120)
    })
    if (ev.audioBase64 && !seenAudio.current.has(ev.id)) {
      seenAudio.current.add(ev.id)
      playAudioBase64(ev.audioBase64)
    }
  }, [])

  useEffect(() => {
    let closed = false
    let socket: WebSocket | null = null
    let retry: number | undefined

    const connect = () => {
      socket = new WebSocket(wsUrl())
      socket.onopen = () => {
        if (!closed) setConnected(true)
      }
      socket.onclose = () => {
        if (!closed) {
          setConnected(false)
          retry = window.setTimeout(connect, 1500)
        }
      }
      socket.onmessage = (msg) => {
        try {
          pushEvent(JSON.parse(msg.data) as PulseEvent)
        } catch {
          // ignore
        }
      }
    }
    connect()

    const poll = async () => {
      try {
        const res = await fetch(`${API}/api/loop/status`)
        if (res.ok) setStatus(await res.json())
      } catch {
        // backend may be starting
      }
    }
    poll()
    const id = window.setInterval(poll, 2000)

    fetch(`${API}/api/about`)
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => data && setAbout(data))
      .catch(() => {})

    return () => {
      closed = true
      window.clearInterval(id)
      if (retry) window.clearTimeout(retry)
      socket?.close()
    }
  }, [pushEvent])

  const inject = async (type: string, label: string) => {
    setBusy(type)
    try {
      const res = await fetch(`${API}/api/demo/inject/${type}`, { method: 'POST' })
      if (!res.ok) throw new Error('inject failed')
      showToast(`${label} sent — watch the timeline`)
    } catch {
      showToast('Action failed — is the backend running?')
    } finally {
      setBusy(null)
    }
  }

  const sendTextCommand = async () => {
    if (!voiceNote.trim()) return
    setBusy('voice')
    try {
      await fetch(`${API}/api/voice/text`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: voiceNote }),
      })
      showToast('Voice command queued')
      setVoiceNote('')
    } catch {
      showToast('Voice command failed')
    } finally {
      setBusy(null)
    }
  }

  const toggleRecord = async () => {
    if (recording && mediaRef.current) {
      mediaRef.current.stop()
      setRecording(false)
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(stream)
      chunksRef.current = []
      recorder.ondataavailable = (e) => {
        if (e.data.size) chunksRef.current.push(e.data)
      }
      recorder.onstop = async () => {
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || 'audio/webm' })
        stream.getTracks().forEach((t) => t.stop())
        const form = new FormData()
        form.append('audio', blob, 'command.webm')
        form.append('mimeType', blob.type || 'audio/webm')
        setBusy('mic')
        try {
          await fetch(`${API}/api/voice/command`, { method: 'POST', body: form })
          showToast('Mic command sent')
        } finally {
          setBusy(null)
        }
      }
      mediaRef.current = recorder
      recorder.start()
      setRecording(true)
    } catch {
      setVoiceNote('Allow microphone or type a command instead')
    }
  }

  const phaseGlow = status?.phase ?? 'IDLE'
  const criticalCount = useMemo(
    () => events.filter((e) => e.severity === 'CRITICAL' || e.severity === 'HIGH').length,
    [events],
  )
  const filtered = useMemo(
    () => (phaseFilter === 'ALL' ? events : events.filter((e) => e.phase === phaseFilter)),
    [events, phaseFilter],
  )

  return (
    <div className="shell">
      <div className="atmosphere" aria-hidden />

      <nav className="topbar">
        <div className="topbar-brand">
          <span className="mark" aria-hidden />
          <strong>PulseMind</strong>
          <span className="sep">/</span>
          <span className="sub">Continuous ORVA Engine</span>
        </div>
        <div className="topbar-right">
          <a href="#audience">Who uses this</a>
          <a href="#workspace">Workspace</a>
          <a href="#check">How to check</a>
          <span className={`conn ${connected ? 'on' : 'off'}`}>
            <i />
            {connected ? 'Stream live' : 'Reconnecting'}
          </span>
        </div>
      </nav>

      {toast && <div className="toast">{toast}</div>}

      <header className="hero">
        <div className="brand-block">
          <p className="eyebrow">Frontier Intelligence at Flash Speed</p>
          <h1 className="brand">PulseMind</h1>
          <p className="tagline">
            {about?.tagline ??
              'While you build, the background mind observes, reasons, verifies, and acts — powered by Gemini 3.8 Flash.'}
          </p>
          <div className="cta-row">
            <button
              className="btn primary"
              disabled={!!busy}
              onClick={() => inject('sequence', 'Demo sequence')}
            >
              {busy === 'sequence' ? 'Running…' : 'Run 90s demo sequence'}
            </button>
            <button className="btn ghost" disabled={!!busy} onClick={() => inject('scan', 'Project scan')}>
              Scan watched project
            </button>
            <button className="btn ghost" disabled={!!busy} onClick={() => inject('bug', 'Code fault')}>
              Inject code fault
            </button>
            <button className="btn ghost" disabled={!!busy} onClick={() => inject('logs', 'Log spike')}>
              Spike logs
            </button>
            <button className="btn ghost" disabled={!!busy} onClick={() => inject('verify', 'Deep verify')}>
              Deep verify
            </button>
          </div>
          <p className="hero-hint">
            Best first click for judges/testers: <strong>Run 90s demo sequence</strong>
          </p>
        </div>

        <div className={`pulse-orb ${connected ? 'live' : 'down'}`}>
          <div className="orb-core" />
          <div className="orb-ring" />
          <div className="orb-meta">
            <span>{connected ? 'LIVE' : 'RECONNECTING'}</span>
            <strong>{phaseGlow}</strong>
          </div>
        </div>
      </header>

      <section className="stats" aria-label="Loop metrics">
        <Metric label="Cycles" value={status?.cycles ?? 0} />
        <Metric label="Findings" value={status?.findings ?? 0} />
        <Metric label="High / Critical" value={criticalCount} />
        <Metric label="Pending" value={status?.pendingEvents ?? 0} />
        <Metric
          label="Gemini API"
          value={status?.apiKeyConfigured ? 'Live key' : 'Demo fallback'}
          text
        />
      </section>

      <section className="audience" id="audience">
        <div className="section-head">
          <h2>Who uses PulseMind</h2>
          <p>Built for people who ship and operate software — not another chat box.</p>
        </div>
        <div className="audience-grid">
          {(about?.whoUsesIt ?? defaultAudience).map((u) => (
            <article key={u.role} className="audience-card">
              <h3>{u.role}</h3>
              <p>{u.why}</p>
            </article>
          ))}
        </div>
        <div className="orva-strip" aria-label="How ORVA works">
          {(about?.howItWorks ?? defaultOrva).map((step, i) => (
            <div key={step} className="orva-step">
              <span>{String(i + 1).padStart(2, '0')}</span>
              <p>{step}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="workspace" id="workspace">
        <div className="timeline-panel">
          <div className="panel-head">
            <div>
              <h2>Sentinel timeline</h2>
              <p>Live ORVA stream from the Java background engine</p>
            </div>
            <div className="filters">
              {(['ALL', 'OBSERVE', 'REASON', 'VERIFY', 'ACT', 'VOICE', 'SYSTEM'] as const).map((p) => (
                <button
                  key={p}
                  className={`chip ${phaseFilter === p ? 'active' : ''}`}
                  onClick={() => setPhaseFilter(p)}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
          <div className="timeline">
            {filtered.length === 0 && (
              <div className="empty">
                Waiting for background intelligence… Click <em>Run 90s demo sequence</em>.
              </div>
            )}
            {filtered.map((ev) => (
              <article key={ev.id} className={`event sev-${ev.severity.toLowerCase()}`}>
                <div className="event-top">
                  <span className="phase">{ev.phase}</span>
                  <span className={`sev-pill sev-${ev.severity.toLowerCase()}`}>{ev.severity}</span>
                  <span className="time">{formatTime(ev.timestamp)}</span>
                </div>
                <h3>{ev.title}</h3>
                <p>{ev.detail}</p>
                <div className="event-foot">
                  <code>{ev.model}</code>
                  {ev.latencyMs > 0 && <span>{ev.latencyMs} ms</span>}
                  <span>{ev.source}</span>
                </div>
              </article>
            ))}
          </div>
        </div>

        <aside className="side-panel">
          <div className="panel-head">
            <h2>Voice bridge</h2>
            <p>Hands-free control for on-call engineers</p>
          </div>
          <div className="voice-box">
            <button
              className={`btn mic ${recording ? 'hot' : ''}`}
              onClick={toggleRecord}
              disabled={!!busy && !recording}
            >
              {recording ? 'Stop & send' : 'Record mic command'}
            </button>
            <div className="text-cmd">
              <input
                value={voiceNote}
                onChange={(e) => setVoiceNote(e.target.value)}
                placeholder='Try: "run a deep verify"'
                onKeyDown={(e) => e.key === 'Enter' && sendTextCommand()}
              />
              <button className="btn ghost" onClick={sendTextCommand} disabled={!!busy}>
                Send
              </button>
            </div>
            <ul className="hints">
              <li>“inject a bug”</li>
              <li>“spike the logs”</li>
              <li>“run a deep verify”</li>
            </ul>
          </div>

          <div className="stack-card" id="check">
            <h3>How to check (60 seconds)</h3>
            <ol className="check-list">
              {(about?.howToCheck ?? defaultCheck).map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </div>

          <div className="stack-card">
            <h3>Google AI stack</h3>
            <ul>
              <li>
                <code>gemini-3.8-flash</code> continuous reason
              </li>
              <li>
                <code>antigravity-preview-09-2026</code> deep verify
              </li>
              <li>
                <code>gemini-3.8-flash-tts</code> spoken alerts
              </li>
              <li>
                <code>gemini-3.5-transcribe</code> voice commands
              </li>
            </ul>
            <p className="muted">
              Active: {status?.flashModel ?? '…'} · {status?.antigravityAgent ?? '…'}
            </p>
          </div>
        </aside>
      </section>

      <footer className="site-foot">
        <div>
          <strong>PulseMind</strong> · Java Spring Boot engine · React workspace · Gemini Flash speed
        </div>
        <div className="muted">No login · Demo injectors for judges · Problem Statement 1</div>
      </footer>
    </div>
  )
}

const defaultAudience = [
  { role: 'Software engineers / SRE', why: 'Catch bugs and log anomalies while coding — without stopping to prompt a chatbot.' },
  { role: 'Tech leads / reviewers', why: 'See a live audit trail of risks found in a watched service.' },
  { role: 'Hackathon judges', why: 'One-click injectors prove the continuous ORVA loop with live Gemini models.' },
  { role: 'QA / platform teams', why: 'Verify continuous intelligence behavior against repeatable inject scenarios.' },
]

const defaultOrva = [
  'OBSERVE — watch sample-service files and logs',
  'REASON — Gemini 3.8 Flash classifies severity and suggests action',
  'VERIFY — Antigravity Agent deep-verifies high-risk findings',
  'ACT — queue remediation + optional TTS spoken alerts',
]

const defaultCheck = [
  'Open UI and confirm orb shows LIVE',
  'Click Run 90s demo sequence',
  'Confirm timeline shows OBSERVE → REASON → VERIFY → ACT',
  'Optional: type “run a deep verify” in Voice bridge',
]

function Metric({
  label,
  value,
  text,
}: {
  label: string
  value: number | string
  text?: boolean
}) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong className={text ? 'text' : undefined}>{value}</strong>
    </div>
  )
}

function formatTime(iso: string) {
  try {
    return new Date(iso).toLocaleTimeString()
  } catch {
    return ''
  }
}
