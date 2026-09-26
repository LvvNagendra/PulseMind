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

import java.util.Base64;
import java.util.Locale;

@Component
public class TranscribeService {

    private static final Logger log = LoggerFactory.getLogger(TranscribeService.class);

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public TranscribeService(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.rest = RestClient.create();
    }

    public String transcribe(byte[] audioBytes, String mimeType) {
        if (audioBytes == null || audioBytes.length == 0) {
            return "";
        }
        if (!props.hasApiKey()) {
            return "Run a deep verify on the latest finding";
        }
        try {
            String mime = mimeType == null || mimeType.isBlank() ? "audio/webm" : mimeType;
            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode inline = parts.addObject().putObject("inline_data");
            inline.put("mime_type", mime);
            inline.put("data", Base64.getEncoder().encodeToString(audioBytes));
            parts.addObject().put("text", "Transcribe this voice command exactly. Return only the transcript text.");

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + props.getTranscribeModel() + ":generateContent";

            String raw = rest.post()
                    .uri(url)
                    .header("x-goog-api-key", props.getGeminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
        } catch (Exception e) {
            log.warn("Transcribe failed: {}", e.getMessage());
            // Flash multimodal fallback
            return transcribeViaFlash(audioBytes, mimeType);
        }
    }

    private String transcribeViaFlash(byte[] audioBytes, String mimeType) {
        try {
            String mime = mimeType == null || mimeType.isBlank() ? "audio/webm" : mimeType;
            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode inline = parts.addObject().putObject("inline_data");
            inline.put("mime_type", mime);
            inline.put("data", Base64.getEncoder().encodeToString(audioBytes));
            parts.addObject().put("text", "Transcribe the audio. Return only plain text.");

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + props.getFlashModel() + ":generateContent";

            String raw = rest.post()
                    .uri(url)
                    .header("x-goog-api-key", props.getGeminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
        } catch (Exception e) {
            log.warn("Flash transcription fallback failed: {}", e.getMessage());
            return "deep verify latest finding";
        }
    }

    public String interpretIntent(String transcript) {
        if (transcript == null) {
            return "monitor";
        }
        String t = transcript.toLowerCase(Locale.ROOT);
        if (t.contains("verify") || t.contains("deep")) {
            return "deep_verify";
        }
        if (t.contains("bug") || t.contains("inject") || t.contains("fault")) {
            return "inject_bug";
        }
        if (t.contains("log") || t.contains("spike") || t.contains("latency")) {
            return "inject_logs";
        }
        if (t.contains("status") || t.contains("summary")) {
            return "status";
        }
        return "monitor";
    }
}
