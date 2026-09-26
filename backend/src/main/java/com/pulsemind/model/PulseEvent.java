package com.pulsemind.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class PulseEvent {

    public enum Phase {
        OBSERVE, REASON, VERIFY, ACT, SYSTEM, VOICE
    }

    public enum Severity {
        INFO, LOW, MEDIUM, HIGH, CRITICAL
    }

    private String id;
    private Instant timestamp;
    private Phase phase;
    private Severity severity;
    private String title;
    private String detail;
    private String model;
    private long latencyMs;
    private String source;
    private Map<String, Object> meta;
    private String audioBase64;

    public static PulseEvent of(Phase phase, Severity severity, String title, String detail, String model, long latencyMs, String source) {
        PulseEvent e = new PulseEvent();
        e.id = UUID.randomUUID().toString();
        e.timestamp = Instant.now();
        e.phase = phase;
        e.severity = severity;
        e.title = title;
        e.detail = detail;
        e.model = model;
        e.latencyMs = latencyMs;
        e.source = source;
        return e;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public String getAudioBase64() {
        return audioBase64;
    }

    public void setAudioBase64(String audioBase64) {
        this.audioBase64 = audioBase64;
    }
}
