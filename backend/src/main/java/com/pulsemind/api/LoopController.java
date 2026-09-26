package com.pulsemind.api;

import com.pulsemind.demo.DemoInjectorService;
import com.pulsemind.loop.PulseLoopService;
import com.pulsemind.model.LoopStatus;
import com.pulsemind.model.PulseEvent;
import com.pulsemind.ws.PulseWebSocketHandler;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoopController {

    private final PulseLoopService loop;
    private final PulseWebSocketHandler ws;
    private final DemoInjectorService demo;

    public LoopController(PulseLoopService loop, PulseWebSocketHandler ws, DemoInjectorService demo) {
        this.loop = loop;
        this.ws = ws;
        this.demo = demo;
    }

    @GetMapping("/loop/status")
    public LoopStatus status() {
        return loop.getStatus();
    }

    @GetMapping("/events/recent")
    public List<PulseEvent> recent(@RequestParam(defaultValue = "50") int limit) {
        return ws.recent(Math.min(limit, 200));
    }

    @PostMapping("/demo/inject")
    public Map<String, Object> inject(@RequestBody Map<String, String> body) {
        String type = body.getOrDefault("type", "bug");
        return demo.inject(type);
    }

    @PostMapping("/demo/inject/{type}")
    public Map<String, Object> injectPath(@PathVariable String type) {
        return demo.inject(type);
    }
}
