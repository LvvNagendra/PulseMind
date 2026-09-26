package com.pulsemind.model;

public class WorkspaceSignal {

    public enum Kind {
        FILE_CHANGE, LOG_ANOMALY, DEMO_INJECT, VOICE_COMMAND, HEARTBEAT
    }

    private Kind kind;
    private String path;
    private String content;
    private String hint;
    private long createdAtMs = System.currentTimeMillis();

    public static WorkspaceSignal of(Kind kind, String path, String content, String hint) {
        WorkspaceSignal s = new WorkspaceSignal();
        s.kind = kind;
        s.path = path;
        s.content = content;
        s.hint = hint;
        return s;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }
}
