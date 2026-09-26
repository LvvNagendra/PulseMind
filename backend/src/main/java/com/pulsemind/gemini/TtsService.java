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
import java.util.Optional;

@Component
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public TtsService(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.rest = RestClient.create();
    }

    public Optional<String> speakBase64(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (!props.hasApiKey()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode content = contents.addObject();
            content.putArray("parts").addObject().put("text", truncate(text, 500));

            ObjectNode genConfig = body.putObject("generationConfig");
            ArrayNode modalities = genConfig.putArray("responseModalities");
            modalities.add("AUDIO");
            ObjectNode speech = genConfig.putObject("speechConfig");
            ObjectNode voice = speech.putObject("voiceConfig").putObject("prebuiltVoiceConfig");
            voice.put("voiceName", "Kore");

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + props.getTtsModel() + ":generateContent";

            String raw = rest.post()
                    .uri(url)
                    .header("x-goog-api-key", props.getGeminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    JsonNode inline = part.path("inlineData");
                    if (inline.isMissingNode()) {
                        inline = part.path("inline_data");
                    }
                    String data = inline.path("data").asText(null);
                    if (data != null && !data.isBlank()) {
                        return Optional.of(data);
                    }
                }
            }
            log.debug("TTS response lacked audio bytes");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("TTS failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Tiny silent-ish placeholder so UI can still signal an alert without API audio. */
    public Optional<String> silentPlaceholder() {
        // Minimal valid-ish WAV header + silence (not played aggressively)
        byte[] wav = new byte[44];
        return Optional.of(Base64.getEncoder().encodeToString(wav));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
