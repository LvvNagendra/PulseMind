package com.pulsemind.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class LoopStatus {

    private boolean running = true;
    private String phase = "IDLE";
    private Instant lastTick;
    private Instant startedAt = Instant.now();
    private final AtomicLong cycles = new AtomicLong();
    private final AtomicLong findings = new AtomicLong();
    private final AtomicLong criticals = new AtomicLong();
    private boolean apiKeyConfigured;
    private String flashModel;
    private String antigravityAgent;
    private long pendingEvents;

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Instant getLastTick() {
        return lastTick;
    }

    public void setLastTick(Instant lastTick) {
        this.lastTick = lastTick;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public long getCycles() {
        return cycles.get();
    }

    public void incrementCycles() {
        cycles.incrementAndGet();
    }

    public long getFindings() {
        return findings.get();
    }

    public void incrementFindings() {
        findings.incrementAndGet();
    }

    public long getCriticals() {
        return criticals.get();
    }

    public void incrementCriticals() {
        criticals.incrementAndGet();
    }

    public boolean isApiKeyConfigured() {
        return apiKeyConfigured;
    }

    public void setApiKeyConfigured(boolean apiKeyConfigured) {
        this.apiKeyConfigured = apiKeyConfigured;
    }

    public String getFlashModel() {
        return flashModel;
    }

    public void setFlashModel(String flashModel) {
        this.flashModel = flashModel;
    }

    public String getAntigravityAgent() {
        return antigravityAgent;
    }

    public void setAntigravityAgent(String antigravityAgent) {
        this.antigravityAgent = antigravityAgent;
    }

    public long getPendingEvents() {
        return pendingEvents;
    }

    public void setPendingEvents(long pendingEvents) {
        this.pendingEvents = pendingEvents;
    }
}
