export type Phase = 'OBSERVE' | 'REASON' | 'VERIFY' | 'ACT' | 'SYSTEM' | 'VOICE'
export type Severity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface PulseEvent {
  id: string
  timestamp: string
  phase: Phase
  severity: Severity
  title: string
  detail: string
  model: string
  latencyMs: number
  source: string
  meta?: Record<string, unknown>
  audioBase64?: string
}

export interface LoopStatus {
  running: boolean
  phase: string
  lastTick?: string
  startedAt?: string
  cycles: number
  findings: number
  criticals: number
  apiKeyConfigured: boolean
  flashModel: string
  antigravityAgent: string
  pendingEvents: number
}
