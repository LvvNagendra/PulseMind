package com.pulsemind.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pulsemind.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AntigravityClient {

    private static final Logger log = LoggerFactory.getLogger(AntigravityClient.class);

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public AntigravityClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.rest = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("x-goog-api-key", props.getGeminiApiKey());
                    return execution.execute(request, body);
                })
                .build();
    }

    public VerifyResult deepVerify(String findingTitle, String findingDetail, String context) {
        long start = System.currentTimeMillis();
        if (!props.hasApiKey()) {
            return fallback(findingTitle, findingDetail, System.currentTimeMillis() - start);
        }
        try {
            String input = """
                    You are the PulseMind deep-verify agent.
                    Given this engineering finding, propose a concrete verification plan,
                    identify root cause likelihood, and recommend a safe remediation.
                    Keep the answer under 250 words.

                    Finding: %s
                    Detail: %s
                    Context:
                    %s
                    """.formatted(findingTitle, findingDetail, truncate(context, 8000));

            ObjectNode body = mapper.createObjectNode();
            body.put("agent", props.getAntigravityAgent());
            body.put("input", input);
            body.put("environment", "remote");

            String raw = rest.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/interactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            String output = firstNonBlank(
                    root.path("output_text").asText(null),
                    extractOutputs(root),
                    root.toString()
            );

            VerifyResult result = new VerifyResult();
            result.model = props.getAntigravityAgent();
            result.detail = output;
            result.latencyMs = System.currentTimeMillis() - start;
            result.fallback = false;
            result.interactionId = root.path("id").asText(root.path("name").asText(""));
            return result;
        } catch (Exception e) {
            log.warn("Antigravity verify failed, using fallback: {}", e.getMessage());
            if (props.isDemoModeFallback()) {
                return fallback(findingTitle, findingDetail, System.currentTimeMillis() - start);
            }
            VerifyResult err = new VerifyResult();
            err.model = props.getAntigravityAgent();
            err.detail = "Deep verify unavailable: " + e.getMessage();
            err.latencyMs = System.currentTimeMillis() - start;
            err.fallback = true;
            return err;
        }
    }

    private VerifyResult fallback(String title, String detail, long latency) {
        VerifyResult r = new VerifyResult();
        r.model = props.getAntigravityAgent() + " (demo-fallback)";
        r.latencyMs = latency;
        r.fallback = true;
        r.detail = """
                Deep verify plan for "%s":
                1) Reproduce with quantity=null and stock=0 unit tests.
                2) Confirm stack traces match NPE / ArithmeticException.
                3) Patch with null-safe quantity + early return when stock <= 0.
                4) Re-run smoke orders for SKU-100 and SKU-200.
                Evidence: %s
                """.formatted(title, truncate(detail, 400)).trim();
        return r;
    }

    private String extractOutputs(JsonNode root) {
        JsonNode outputs = root.path("outputs");
        if (outputs.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode o : outputs) {
                String t = o.path("text").asText(o.path("content").asText(""));
                if (!t.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(t);
                }
            }
            return sb.toString();
        }
        JsonNode steps = root.path("steps");
        if (steps.isArray() && !steps.isEmpty()) {
            return steps.get(steps.size() - 1).path("text").asText("");
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public static class VerifyResult {
        public String detail;
        public String model;
        public long latencyMs;
        public boolean fallback;
        public String interactionId;
    }
}
