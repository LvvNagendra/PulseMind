package com.pulsemind.watcher;

import com.pulsemind.bus.EventBus;
import com.pulsemind.config.AppProperties;
import com.pulsemind.model.PulseEvent;
import com.pulsemind.model.WorkspaceSignal;
import com.pulsemind.ws.PulseWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WorkspaceWatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceWatcher.class);

    private final AppProperties props;
    private final EventBus eventBus;
    private final PulseWebSocketHandler ws;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pulsemind-watcher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);
    private WatchService watchService;
    private long logOffset;

    public WorkspaceWatcher(AppProperties props, EventBus eventBus, PulseWebSocketHandler ws) {
        this.props = props;
        this.eventBus = eventBus;
        this.ws = ws;
    }

    @PostConstruct
    public void start() {
        try {
            Path watchRoot = Paths.get(props.getWatchPath()).toAbsolutePath().normalize();
            Files.createDirectories(watchRoot);
            Path logFile = Paths.get(props.getLogPath()).toAbsolutePath().normalize();
            Files.createDirectories(logFile.getParent());
            if (!Files.exists(logFile)) {
                Files.writeString(logFile, "");
            }
            logOffset = Files.size(logFile);

            watchService = FileSystems.getDefault().newWatchService();
            registerRecursive(watchRoot);

            executor.submit(this::loop);
            ws.broadcast(PulseEvent.of(
                    PulseEvent.Phase.SYSTEM,
                    PulseEvent.Severity.INFO,
                    "Workspace watcher armed",
                    "Watching " + watchRoot + " and " + logFile,
                    "local",
                    0,
                    "watcher"
            ));
            log.info("Watching workspace at {}", watchRoot);
        } catch (Exception e) {
            log.error("Failed to start workspace watcher", e);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                pollLogTail();
                WatchKey key = watchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Path name = (Path) event.context();
                    Path full = dir.resolve(name);
                    if (Files.isDirectory(full)) {
                        try {
                            registerRecursive(full);
                        } catch (IOException ignored) {
                        }
                        continue;
                    }
                    String content = readSafe(full);
                    eventBus.publish(WorkspaceSignal.of(
                            WorkspaceSignal.Kind.FILE_CHANGE,
                            full.toString(),
                            content,
                            kind.name()
                    ));
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.debug("Watcher cycle error: {}", e.getMessage());
            }
        }
    }

    private void pollLogTail() {
        try {
            Path logFile = Paths.get(props.getLogPath()).toAbsolutePath().normalize();
            if (!Files.exists(logFile)) {
                return;
            }
            long size = Files.size(logFile);
            if (size < logOffset) {
                logOffset = 0;
            }
            if (size == logOffset) {
                return;
            }
            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(logOffset);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = raf.readLine()) != null) {
                    String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    sb.append(decoded).append('\n');
                    if (isAnomaly(decoded)) {
                        eventBus.publish(WorkspaceSignal.of(
                                WorkspaceSignal.Kind.LOG_ANOMALY,
                                logFile.toString(),
                                decoded,
                                "log-anomaly"
                        ));
                    }
                }
                logOffset = raf.getFilePointer();
                if (sb.length() > 0 && !sb.toString().toLowerCase(Locale.ROOT).contains("error")
                        && !sb.toString().toLowerCase(Locale.ROOT).contains("exception")) {
                    // still publish mild observe for non-anomaly bursts when demo injects INFO spikes
                    String chunk = sb.toString().trim();
                    if (chunk.contains("LATENCY_SPIKE") || chunk.contains("TIMEOUT")) {
                        eventBus.publish(WorkspaceSignal.of(
                                WorkspaceSignal.Kind.LOG_ANOMALY,
                                logFile.toString(),
                                chunk,
                                "latency-spike"
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Log tail failed: {}", e.getMessage());
        }
    }

    private boolean isAnomaly(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("error")
                || lower.contains("exception")
                || lower.contains("fatal")
                || lower.contains("timeout")
                || lower.contains("latency_spike")
                || lower.contains("outofmemory");
    }

    private void registerRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                    } catch (IOException e) {
                        log.debug("Could not register {}", dir);
                    }
                });
    }

    private String readSafe(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 200_000) {
                return "(skipped large/binary file)";
            }
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".java") || name.endsWith(".log") || name.endsWith(".md")
                    || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt")
                    || name.endsWith(".xml") || name.endsWith(".json"))) {
                return "(non-text file change)";
            }
            return Files.readString(path);
        } catch (Exception e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }
}
