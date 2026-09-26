package com.pulsemind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulsemind")
public class AppProperties {

    private String watchPath = "../sample-service";
    private String logPath = "../sample-service/logs/application.log";
    private long loopIntervalMs = 2500;
    private String geminiApiKey = "";
    private String flashModel = "gemini-3.8-flash";
    private String antigravityAgent = "antigravity-preview-09-2026";
    private String ttsModel = "gemini-3.8-flash-tts";
    private String transcribeModel = "gemini-3.5-transcribe";
    private boolean demoModeFallback = true;

    public String getWatchPath() {
        return watchPath;
    }

    public void setWatchPath(String watchPath) {
        this.watchPath = watchPath;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public long getLoopIntervalMs() {
        return loopIntervalMs;
    }

    public void setLoopIntervalMs(long loopIntervalMs) {
        this.loopIntervalMs = loopIntervalMs;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
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

    public String getTtsModel() {
        return ttsModel;
    }

    public void setTtsModel(String ttsModel) {
        this.ttsModel = ttsModel;
    }

    public String getTranscribeModel() {
        return transcribeModel;
    }

    public void setTranscribeModel(String transcribeModel) {
        this.transcribeModel = transcribeModel;
    }

    public boolean isDemoModeFallback() {
        return demoModeFallback;
    }

    public void setDemoModeFallback(boolean demoModeFallback) {
        this.demoModeFallback = demoModeFallback;
    }

    public boolean hasApiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank()
                && !geminiApiKey.contains("your_google");
    }
}
