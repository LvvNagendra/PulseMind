package com.pulsemind.api;

import com.pulsemind.bus.EventBus;
import com.pulsemind.demo.DemoInjectorService;
import com.pulsemind.gemini.TranscribeService;
import com.pulsemind.loop.PulseLoopService;
import com.pulsemind.model.PulseEvent;
import com.pulsemind.model.WorkspaceSignal;
import com.pulsemind.ws.PulseWebSocketHandler;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final TranscribeService transcribe;
    private final DemoInjectorService demo;
    private final EventBus eventBus;
    private final PulseWebSocketHandler ws;
    private final PulseLoopService loop;

    public VoiceController(
            TranscribeService transcribe,
            DemoInjectorService demo,
            EventBus eventBus,
            PulseWebSocketHandler ws,
            PulseLoopService loop
    ) {
        this.transcribe = transcribe;
        this.demo = demo;
        this.eventBus = eventBus;
        this.ws = ws;
        this.loop = loop;
    }

    @PostMapping(value = "/command", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> command(
            @RequestPart("audio") MultipartFile audio,
            @RequestPart(value = "mimeType", required = false) String mimeType
    ) throws Exception {
        String transcript = transcribe.transcribe(audio.getBytes(),
                mimeType != null ? mimeType : audio.getContentType());
        String intent = transcribe.interpretIntent(transcript);

        ws.broadcast(PulseEvent.of(
                PulseEvent.Phase.VOICE,
                PulseEvent.Severity.INFO,
                "Voice command received",
                "Transcript: \"" + transcript + "\" → intent=" + intent,
                "gemini-3.5-transcribe",
                0,
                "voice"
        ));

        eventBus.publish(WorkspaceSignal.of(
                WorkspaceSignal.Kind.VOICE_COMMAND,
                "microphone",
                transcript,
                intent
        ));

        Map<String, Object> actionResult = switch (intent) {
            case "deep_verify" -> demo.inject("verify");
            case "inject_bug" -> demo.inject("bug");
            case "inject_logs" -> demo.inject("logs");
            case "status" -> Map.of("ok", true, "status", loop.getStatus());
            default -> Map.of("ok", true, "note", "monitoring");
        };

        return Map.of(
                "ok", true,
                "transcript", transcript,
                "intent", intent,
                "action", actionResult
        );
    }

    @PostMapping("/text")
    public Map<String, Object> textCommand(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        String intent = transcribe.interpretIntent(text);
        ws.broadcast(PulseEvent.of(
                PulseEvent.Phase.VOICE,
                PulseEvent.Severity.INFO,
                "Text command received",
                "\"" + text + "\" → intent=" + intent,
                "local",
                0,
                "voice-text"
        ));
        Map<String, Object> actionResult = switch (intent) {
            case "deep_verify" -> demo.inject("verify");
            case "inject_bug" -> demo.inject("bug");
            case "inject_logs" -> demo.inject("logs");
            default -> {
                eventBus.publish(WorkspaceSignal.of(
                        WorkspaceSignal.Kind.VOICE_COMMAND,
                        "text-command",
                        text,
                        intent
                ));
                yield Map.of("ok", true);
            }
        };
        return Map.of("ok", true, "transcript", text, "intent", intent, "action", actionResult);
    }
}
