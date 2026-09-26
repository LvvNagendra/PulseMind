package com.pulsemind.api;

import com.pulsemind.config.AppProperties;
import com.pulsemind.loop.PulseLoopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AboutController {

    private final AppProperties props;
    private final PulseLoopService loop;

    public AboutController(AppProperties props, PulseLoopService loop) {
        this.props = props;
        this.loop = loop;
    }

    @GetMapping({"/about", "/product"})
    public Map<String, Object> about() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "PulseMind");
        out.put("tagline", "Chatbots wait. PulseMind thinks in the background at flash speed.");
        out.put("problemStatement", "Frontier Intelligence at Flash Speed");
        out.put("version", "1.0.0");
        out.put("summary",
                "A continuous background AI workspace for engineering teams. "
                        + "While developers code, PulseMind observes files and logs, reasons with Gemini 3.8 Flash, "
                        + "deep-verifies with Antigravity, and acts with remediation guidance and spoken alerts.");

        out.put("whoUsesIt", List.of(
                Map.of(
                        "role", "Software engineers / SRE",
                        "why", "Catch bugs and log anomalies while coding without stopping to ask a chatbot"
                ),
                Map.of(
                        "role", "Tech leads / reviewers",
                        "why", "See a live audit trail of risks found in a watched service"
                ),
                Map.of(
                        "role", "Hackathon judges / demos",
                        "why", "One-click injectors prove the ORVA loop with live Gemini models"
                ),
                Map.of(
                        "role", "QA / platform teams",
                        "why", "Verify continuous intelligence behavior against inject scenarios"
                )
        ));

        out.put("howItWorks", List.of(
                "OBSERVE — watch sample-service files and logs",
                "REASON — Gemini 3.8 Flash classifies severity and suggests action",
                "VERIFY — Antigravity Agent deep-verifies high-risk findings",
                "ACT — queue remediation + optional TTS spoken alerts"
        ));

        out.put("stack", Map.of(
                "backend", "Java 17 + Spring Boot 3 (WebSocket, scheduling)",
                "frontend", "React + Vite",
                "models", List.of(
                        props.getFlashModel(),
                        props.getAntigravityAgent(),
                        props.getTtsModel(),
                        props.getTranscribeModel()
                )
        ));

        out.put("howToCheck", List.of(
                "Open UI at http://localhost:5173",
                "Confirm orb shows LIVE",
                "Click Run 90s demo sequence",
                "Confirm timeline shows OBSERVE → REASON → VERIFY → ACT",
                "Optional: voice text 'run a deep verify'"
        ));

        out.put("apiKeyConfigured", props.hasApiKey());
        out.put("loop", loop.getStatus());
        return out;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "pulsemind-engine",
                "version", "1.0.0",
                "problemStatement", "Frontier Intelligence at Flash Speed",
                "apiKeyConfigured", props.hasApiKey()
        );
    }
}
