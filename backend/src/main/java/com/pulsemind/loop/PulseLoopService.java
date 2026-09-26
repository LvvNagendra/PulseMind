package com.pulsemind.loop;

import com.pulsemind.bus.EventBus;
import com.pulsemind.config.AppProperties;
import com.pulsemind.gemini.AntigravityClient;
import com.pulsemind.gemini.GeminiFlashClient;
import com.pulsemind.gemini.TtsService;
import com.pulsemind.model.LoopStatus;
import com.pulsemind.model.PulseEvent;
import com.pulsemind.model.WorkspaceSignal;
import com.pulsemind.ws.PulseWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PulseLoopService {

    private static final Logger log = LoggerFactory.getLogger(PulseLoopService.class);

    private final EventBus eventBus;
    private final GeminiFlashClient flash;
    private final AntigravityClient antigravity;
    private final TtsService tts;
    private final PulseWebSocketHandler ws;
    private final AppProperties props;
    private final LoopStatus status = new LoopStatus();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicReference<GeminiFlashClient.AnalysisResult> lastHighFinding = new AtomicReference<>();
    private final AtomicReference<String> lastContext = new AtomicReference<>("");

    public PulseLoopService(
            EventBus eventBus,
            GeminiFlashClient flash,
            AntigravityClient antigravity,
            TtsService tts,
            PulseWebSocketHandler ws,
            AppProperties props
    ) {
        this.eventBus = eventBus;
        this.flash = flash;
        this.antigravity = antigravity;
        this.tts = tts;
        this.ws = ws;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        status.setApiKeyConfigured(props.hasApiKey());
        status.setFlashModel(props.getFlashModel());
        status.setAntigravityAgent(props.getAntigravityAgent());
        ws.broadcast(PulseEvent.of(
                PulseEvent.Phase.SYSTEM,
                PulseEvent.Severity.INFO,
                "PulseMind ORVA loop online",
                "Continuous Observe → Reason → Verify → Act engine started. API key "
                        + (props.hasApiKey() ? "configured" : "missing (demo-fallback active)") + ".",
                "local",
                0,
                "loop"
        ));
    }

    @Scheduled(fixedDelayString = "${pulsemind.loop-interval-ms:2500}")
    public void tick() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        try {
            status.setLastTick(Instant.now());
            status.setPendingEvents(eventBus.size());
            status.incrementCycles();

            List<WorkspaceSignal> batch = eventBus.drain(5);
            if (batch.isEmpty()) {
                status.setPhase("IDLE");
                if (status.getCycles() % 12 == 0) {
                    ws.broadcast(PulseEvent.of(
                            PulseEvent.Phase.OBSERVE,
                            PulseEvent.Severity.INFO,
                            "Heartbeat — workspace quiet",
                            "No new file/log signals. Background mind remains armed.",
                            "local",
                            0,
                            "heartbeat"
                    ));
                }
                return;
            }

            for (WorkspaceSignal signal : batch) {
                processSignal(signal);
            }
            status.setPhase("IDLE");
        } catch (Exception e) {
            log.error("ORVA tick failed", e);
            status.setPhase("ERROR");
        } finally {
            busy.set(false);
        }
    }

    public void processSignal(WorkspaceSignal signal) {
        status.setPhase("OBSERVE");
        ws.broadcast(PulseEvent.of(
                PulseEvent.Phase.OBSERVE,
                PulseEvent.Severity.INFO,
                "Observed " + signal.getKind(),
                truncate(signal.getPath() + " — " + preview(signal.getContent()), 360),
                "local",
                0,
                signal.getKind().name()
        ));

        status.setPhase("REASON");
        GeminiFlashClient.AnalysisResult analysis = flash.analyze(
                signal.getKind().name(),
                signal.getPath(),
                signal.getContent(),
                signal.getHint()
        );

        PulseEvent.Severity sev = parseSeverity(analysis.severity);
        PulseEvent reasonEvent = PulseEvent.of(
                PulseEvent.Phase.REASON,
                sev,
                analysis.title,
                analysis.detail,
                analysis.model,
                analysis.latencyMs,
                "gemini-flash"
        );
        reasonEvent.setMeta(analysis.meta());
        ws.broadcast(reasonEvent);
        status.incrementFindings();

        if (sev == PulseEvent.Severity.HIGH || sev == PulseEvent.Severity.CRITICAL) {
            lastHighFinding.set(analysis);
            lastContext.set(signal.getContent());
            status.incrementCriticals();
        }

        boolean forceVerify = signal.getHint() != null && signal.getHint().toLowerCase(Locale.ROOT).contains("verify");
        if (analysis.needsVerify || forceVerify || sev == PulseEvent.Severity.CRITICAL) {
            runVerify(analysis, signal.getContent());
        }

        status.setPhase("ACT");
        PulseEvent act = PulseEvent.of(
                PulseEvent.Phase.ACT,
                sev,
                "Action: " + analysis.suggestedAct,
                "Queued remediation guidance from continuous loop.",
                analysis.model,
                0,
                "act"
        );
        act.setMeta(Map.of("suggestedAct", analysis.suggestedAct));
        ws.broadcast(act);

        if (sev == PulseEvent.Severity.HIGH || sev == PulseEvent.Severity.CRITICAL) {
            String spoken = analysis.title + ". " + analysis.suggestedAct;
            tts.speakBase64(spoken).ifPresentOrElse(audio -> {
                PulseEvent alert = PulseEvent.of(
                        PulseEvent.Phase.ACT,
                        sev,
                        "TTS alert dispatched",
                        spoken,
                        props.getTtsModel(),
                        0,
                        "tts"
                );
                alert.setAudioBase64(audio);
                ws.broadcast(alert);
            }, () -> {
                PulseEvent alert = PulseEvent.of(
                        PulseEvent.Phase.ACT,
                        sev,
                        "Critical alert (TTS unavailable)",
                        spoken,
                        props.getTtsModel() + " (skipped)",
                        0,
                        "tts"
                );
                ws.broadcast(alert);
            });
        }
    }

    public PulseEvent runVerify(GeminiFlashClient.AnalysisResult analysis, String context) {
        status.setPhase("VERIFY");
        AntigravityClient.VerifyResult verify = antigravity.deepVerify(
                analysis.title,
                analysis.detail,
                context == null ? "" : context
        );
        PulseEvent event = PulseEvent.of(
                PulseEvent.Phase.VERIFY,
                PulseEvent.Severity.HIGH,
                "Deep verify complete",
                verify.detail,
                verify.model,
                verify.latencyMs,
                "antigravity"
        );
        event.setMeta(Map.of(
                "fallback", verify.fallback,
                "interactionId", verify.interactionId == null ? "" : verify.interactionId
        ));
        ws.broadcast(event);
        return event;
    }

    public PulseEvent forceDeepVerify() {
        GeminiFlashClient.AnalysisResult last = lastHighFinding.get();
        if (last == null) {
            last = new GeminiFlashClient.AnalysisResult();
            last.title = "Manual deep verify";
            last.detail = "Operator requested deep verification with no prior HIGH finding.";
            last.severity = "MEDIUM";
            last.suggestedAct = "Scan OrderService for null and divide-by-zero risks";
            last.model = props.getFlashModel();
        }
        return runVerify(last, lastContext.get());
    }

    public LoopStatus getStatus() {
        status.setPendingEvents(eventBus.size());
        status.setApiKeyConfigured(props.hasApiKey());
        return status;
    }

    private PulseEvent.Severity parseSeverity(String s) {
        try {
            return PulseEvent.Severity.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return PulseEvent.Severity.MEDIUM;
        }
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String oneLine = content.replace('\n', ' ').trim();
        return truncate(oneLine, 220);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
