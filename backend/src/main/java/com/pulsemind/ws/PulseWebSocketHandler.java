package com.pulsemind.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsemind.model.PulseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class PulseWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PulseWebSocketHandler.class);

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final List<PulseEvent> history = new ArrayList<>();
    private final ObjectMapper mapper;
    private final Object historyLock = new Object();

    public PulseWebSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Pulse client connected: {}", session.getId());
        List<PulseEvent> snapshot;
        synchronized (historyLock) {
            // replay only recent tail to avoid flooding/closing the socket
            int from = Math.max(0, history.size() - 40);
            snapshot = new ArrayList<>(history.subList(from, history.size()));
        }
        for (PulseEvent event : snapshot) {
            if (!session.isOpen()) {
                break;
            }
            sendQuiet(session, event);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.debug("WS transport error on {}: {}", session.getId(), exception.getMessage());
    }

    public void broadcast(PulseEvent event) {
        synchronized (historyLock) {
            history.add(event);
            if (history.size() > 200) {
                history.remove(0);
            }
        }
        TextMessage message;
        try {
            message = new TextMessage(mapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Failed to serialize pulse event", e);
            return;
        }
        for (WebSocketSession session : sessions) {
            sendRaw(session, message);
        }
    }

    public List<PulseEvent> recent(int limit) {
        synchronized (historyLock) {
            int from = Math.max(0, history.size() - limit);
            return new ArrayList<>(history.subList(from, history.size()));
        }
    }

    private void sendQuiet(WebSocketSession session, PulseEvent event) {
        try {
            if (!session.isOpen()) {
                return;
            }
            sendRaw(session, new TextMessage(mapper.writeValueAsString(event)));
        } catch (Exception ignored) {
            // client may disconnect mid-replay
        }
    }

    private void sendRaw(WebSocketSession session, TextMessage message) {
        if (session == null || !session.isOpen()) {
            sessions.remove(session);
            return;
        }
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | IllegalStateException e) {
                sessions.remove(session);
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
