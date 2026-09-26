package com.pulsemind.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pulsemind.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class GeminiFlashClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiFlashClient.class);

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public GeminiFlashClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.rest = RestClient.create();
    }

    public AnalysisResult analyze(String signalKind, String path, String content, String hint) {
        long start = System.currentTimeMillis();
        if (!props.hasApiKey()) {
            return fallback(signalKind, path, content, hint, System.currentTimeMillis() - start);
        }

        String prompt = """
                You are PulseMind, a continuous background engineering intelligence.
                Analyze this workspace signal and respond with STRICT JSON only:
                {
                  "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                  "title": "short finding title",
                  "detail": "2-4 sentence actionable analysis",
                  "needsVerify": true/false,
                  "suggestedAct": "one concrete next action"
                }

                Signal kind: %s
                Path: %s
                Hint: %s
                Content:
                ---
                %s
                ---
                """.formatted(signalKind, path, hint == null ? "" : hint, truncate(content, 12000));

        String[] models = new String[] {
                props.getFlashModel(),
                "gemini-3.5-flash",
                "gemini-2.5-flash"
        };

        Exception last = null;
        for (String model : models) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    ObjectNode body = mapper.createObjectNode();
                    ArrayNode contents = body.putArray("contents");
                    ObjectNode contentNode = contents.addObject();
                    ArrayNode parts = contentNode.putArray("parts");
                    parts.addObject().put("text", prompt);

                    String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent";

                    String raw = rest.post()
                            .uri(url)
                            .header("x-goog-api-key", props.getGeminiApiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body.toString())
                            .retrieve()
                            .body(String.class);

                    String text = extractText(raw);
                    AnalysisResult parsed = parseJson(text);
                    parsed.latencyMs = System.currentTimeMillis() - start;
                    parsed.model = model;
                    parsed.fallback = false;
                    return parsed;
                } catch (Exception e) {
                    last = e;
                    log.warn("Flash analyze failed model={} attempt={}: {}", model, attempt, e.getMessage());
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("429")) {
                        try {
                            Thread.sleep(800L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    break;
                }
            }
        }

        log.warn("Flash analyze exhausted retries, using local fallback: {}", last != null ? last.getMessage() : "unknown");
        if (props.isDemoModeFallback()) {
            return fallback(signalKind, path, content, hint, System.currentTimeMillis() - start);
        }
        AnalysisResult err = new AnalysisResult();
        err.severity = "MEDIUM";
        err.title = "Flash analysis unavailable";
        err.detail = last != null ? last.getMessage() : "unknown";
        err.needsVerify = false;
        err.suggestedAct = "Retry when API is healthy";
        err.latencyMs = System.currentTimeMillis() - start;
        err.model = props.getFlashModel();
        err.fallback = true;
        return err;
    }

    private AnalysisResult fallback(String kind, String path, String content, String hint, long latency) {
        AnalysisResult r = new AnalysisResult();
        r.fallback = true;
        r.model = props.getFlashModel() + " (demo-fallback)";
        r.latencyMs = latency;
        String c = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (c.contains("null") || c.contains("// bug") || c.contains("quantity") && c.contains("npe")
                || "FILE_CHANGE".equals(kind) && path != null && path.endsWith("OrderService.java")) {
            r.severity = "HIGH";
            r.title = "Null-safety / arithmetic defect in OrderService";
            r.detail = "Detected missing null check on quantity and unsafe division by stock. "
                    + "These will throw NPE or ArithmeticException under edge traffic.";
            r.needsVerify = true;
            r.suggestedAct = "Add Objects.requireNonNullElse(quantity, 0) and guard stock == 0 before division.";
        } else if ("LOG_ANOMALY".equals(kind) || c.contains("error") || c.contains("timeout") || c.contains("latency")) {
            r.severity = "CRITICAL";
            r.title = "Production log anomaly detected";
            r.detail = "Log cortex flagged anomalous lines: " + truncate(content, 280)
                    + ". Likely cascading latency or error burst in the order path.";
            r.needsVerify = true;
            r.suggestedAct = "Escalate to Antigravity deep verify and page on-call if sustained > 60s.";
        } else if ("VOICE_COMMAND".equals(kind)) {
            r.severity = "INFO";
            r.title = "Voice command acknowledged";
            r.detail = "Understood: " + truncate(content, 200);
            r.needsVerify = false;
            r.suggestedAct = hint != null ? hint : "Continue background monitoring";
        } else if ("DEMO_INJECT".equals(kind)) {
            r.severity = "MEDIUM";
            r.title = "Demo signal ingested";
            r.detail = truncate(content, 300);
            r.needsVerify = content != null && content.toLowerCase(Locale.ROOT).contains("verify");
            r.suggestedAct = "Continue Observe→Reason→Verify→Act cycle";
        } else {
            r.severity = "LOW";
            r.title = "Workspace signal observed";
            r.detail = "Background observe noted change at " + path;
            r.needsVerify = false;
            r.suggestedAct = "Keep watching";
        }
        return r;
    }

    private AnalysisResult parseJson(String text) throws Exception {
        String json = text.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        JsonNode node = mapper.readTree(json);
        AnalysisResult r = new AnalysisResult();
        r.severity = node.path("severity").asText("MEDIUM");
        r.title = node.path("title").asText("Finding");
        r.detail = node.path("detail").asText(text);
        r.needsVerify = node.path("needsVerify").asBoolean(false);
        r.suggestedAct = node.path("suggestedAct").asText("Monitor");
        return r;
    }

    private String extractText(String raw) throws Exception {
        JsonNode root = mapper.readTree(raw);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (parts.isArray() && !parts.isEmpty()) {
            return parts.get(0).path("text").asText("");
        }
        throw new IllegalStateException("No text in Gemini response");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public static class AnalysisResult {
        public String severity;
        public String title;
        public String detail;
        public boolean needsVerify;
        public String suggestedAct;
        public long latencyMs;
        public String model;
        public boolean fallback;

        public Map<String, Object> meta() {
            Map<String, Object> m = new HashMap<>();
            m.put("needsVerify", needsVerify);
            m.put("suggestedAct", suggestedAct);
            m.put("fallback", fallback);
            return m;
        }
    }
}
