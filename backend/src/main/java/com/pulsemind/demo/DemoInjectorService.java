package com.pulsemind.demo;

import com.pulsemind.bus.EventBus;
import com.pulsemind.config.AppProperties;
import com.pulsemind.loop.PulseLoopService;
import com.pulsemind.model.PulseEvent;
import com.pulsemind.model.WorkspaceSignal;
import com.pulsemind.ws.PulseWebSocketHandler;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class DemoInjectorService {

    private final AppProperties props;
    private final EventBus eventBus;
    private final PulseLoopService loop;
    private final PulseWebSocketHandler ws;

    public DemoInjectorService(
            AppProperties props,
            EventBus eventBus,
            PulseLoopService loop,
            PulseWebSocketHandler ws
    ) {
        this.props = props;
        this.eventBus = eventBus;
        this.loop = loop;
        this.ws = ws;
    }

    public Map<String, Object> inject(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "bug", "inject_bug" -> injectBug();
            case "logs", "spike", "inject_logs" -> injectLogSpike();
            case "verify", "deep_verify" -> injectDeepVerify();
            case "sequence", "demo" -> injectSequence();
            case "scan", "analyze", "scan_project" -> scanProject();
            default -> Map.of("ok", false, "error", "Unknown inject type: " + type);
        };
    }

    /** Analyze important existing files in the watched project (no upload needed). */
    private Map<String, Object> scanProject() {
        try {
            Path root = Paths.get(props.getWatchPath()).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                return Map.of("ok", false, "error", "Watch path not found: " + root);
            }

            List<Path> priority = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            String full = p.toString().toLowerCase(Locale.ROOT);
                            if (full.contains("target") || full.contains("node_modules") || full.contains(".git")) {
                                return false;
                            }
                            return n.endsWith(".java");
                        })
                        .sorted(Comparator.comparing(p -> priorityScore(p.toString())))
                        .limit(12)
                        .forEach(priority::add);
            }

            int queued = 0;
            for (Path file : priority) {
                if (Files.size(file) > 120_000) {
                    continue;
                }
                String content = Files.readString(file);
                eventBus.publish(WorkspaceSignal.of(
                        WorkspaceSignal.Kind.FILE_CHANGE,
                        file.toString(),
                        content,
                        "project-scan"
                ));
                queued++;
            }

            // Also pull recent error log tail if present
            Path log = Paths.get(props.getLogPath()).toAbsolutePath().normalize();
            if (Files.exists(log)) {
                String tail = tailFile(log, 8_000);
                if (!tail.isBlank()) {
                    eventBus.publish(WorkspaceSignal.of(
                            WorkspaceSignal.Kind.LOG_ANOMALY,
                            log.toString(),
                            tail,
                            "project-scan-log"
                    ));
                    queued++;
                }
            }

            ws.broadcast(PulseEvent.of(
                    PulseEvent.Phase.SYSTEM,
                    PulseEvent.Severity.INFO,
                    "Project scan queued",
                    "Queued " + queued + " signals from " + root + " for ORVA analysis.",
                    "local",
                    0,
                    "scan"
            ));
            return Map.of("ok", true, "type", "scan", "queued", queued, "watchPath", root.toString());
        } catch (Exception e) {
            return Map.of("ok", false, "type", "scan", "error", e.getMessage());
        }
    }

    private int priorityScore(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.contains("security") || p.contains("auth")) return 0;
        if (p.contains("controller")) return 1;
        if (p.contains("service")) return 2;
        if (p.contains("config")) return 3;
        return 5;
    }

    private Map<String, Object> injectBug() {
        try {
            Path file = resolveBugTarget();
            String content = Files.readString(file);
            String marker = "// PULSEMIND_DEMO_MARKER";
            if (!content.contains(marker)) {
                // Soft touch: append marker comment so watcher fires without breaking compile if possible
                content = content + "\n" + marker + " // PulseMind demo touch for analysis\n";
                Files.writeString(file, content);
            } else {
                Files.writeString(file, content);
            }

            String snapshot = Files.readString(file);
            eventBus.publish(WorkspaceSignal.of(
                    WorkspaceSignal.Kind.DEMO_INJECT,
                    file.toString(),
                    snapshot,
                    "inject_bug"
            ));
            eventBus.publish(WorkspaceSignal.of(
                    WorkspaceSignal.Kind.FILE_CHANGE,
                    file.toString(),
                    snapshot,
                    "ENTRY_MODIFY"
            ));

            ws.broadcast(PulseEvent.of(
                    PulseEvent.Phase.SYSTEM,
                    PulseEvent.Severity.MEDIUM,
                    "Demo injector: code fault / file touch",
                    "Touched " + file.getFileName() + " under watched project for analysis.",
                    "local",
                    0,
                    "demo"
            ));
            return Map.of("ok", true, "type", "bug", "path", file.toString());
        } catch (Exception e) {
            String synthetic = """
                    public String placeOrder(String sku, Integer quantity) {
                        int q = quantity; // BUG: NPE
                        int ratio = q / inventory.get(sku); // BUG: /0
                        return "OK";
                    }
                    """;
            eventBus.publish(WorkspaceSignal.of(
                    WorkspaceSignal.Kind.FILE_CHANGE,
                    "synthetic/OrderService.java",
                    synthetic,
                    "ENTRY_MODIFY"
            ));
            return Map.of("ok", true, "type", "bug", "note", "synthetic:" + e.getMessage());
        }
    }

    private Path resolveBugTarget() throws Exception {
        Path root = Paths.get(props.getWatchPath()).toAbsolutePath().normalize();
        Path sample = root.resolve("src").resolve("OrderService.java");
        if (Files.exists(sample)) {
            return sample;
        }
        // Prefer Auth/Security controllers in real projects like D:\\Nani\\backend
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.contains("auth") || n.contains("security") || n.contains("payroll");
                    })
                    .findFirst()
                    .orElseGet(() -> root.resolve("src").resolve("main").resolve("java")
                            .resolve("com").resolve("hrms").resolve("HrmsApplication.java"));
        }
    }

    private Map<String, Object> injectLogSpike() {
        try {
            Path logFile = Paths.get(props.getLogPath()).toAbsolutePath().normalize();
            Files.createDirectories(logFile.getParent());
            String stamp = Instant.now().toString();
            String lines = """
                    %s ERROR [hrms] TimeoutException: checkout exceeded 3000ms budget
                    %s ERROR [hrms] LATENCY_SPIKE p99=4200ms route=/api/payroll
                    %s FATAL [hrms] Cascading failure: DB connection pool exhausted
                    """.formatted(stamp, stamp, stamp);
            Files.writeString(logFile, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            eventBus.publish(WorkspaceSignal.of(
                    WorkspaceSignal.Kind.LOG_ANOMALY,
                    logFile.toString(),
                    lines,
                    "demo-log-spike"
            ));
            ws.broadcast(PulseEvent.of(
                    PulseEvent.Phase.SYSTEM,
                    PulseEvent.Severity.HIGH,
                    "Demo injector: log spike",
                    "Appended anomaly lines to " + logFile.getFileName(),
                    "local",
                    0,
                    "demo"
            ));
            return Map.of("ok", true, "type", "logs", "path", logFile.toString());
        } catch (Exception e) {
            eventBus.publish(WorkspaceSignal.of(
                    WorkspaceSignal.Kind.LOG_ANOMALY,
                    props.getLogPath(),
                    "ERROR LATENCY_SPIKE TimeoutException pool exhausted",
                    "demo-log-spike"
            ));
            return Map.of("ok", true, "type", "logs", "note", String.valueOf(e.getMessage()));
        }
    }

    private Map<String, Object> injectDeepVerify() {
        PulseEvent event = loop.forceDeepVerify();
        return Map.of("ok", true, "type", "verify", "eventId", event.getId());
    }

    private Map<String, Object> injectSequence() {
        scanProject();
        injectLogSpike();
        injectDeepVerify();
        return Map.of("ok", true, "type", "sequence", "steps", 3);
    }

    private String tailFile(Path file, int maxChars) throws Exception {
        byte[] all = Files.readAllBytes(file);
        if (all.length <= maxChars) {
            return new String(all, StandardCharsets.UTF_8);
        }
        return new String(all, all.length - maxChars, maxChars, StandardCharsets.UTF_8);
    }
}
