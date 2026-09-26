package com.pulsemind.bus;

import com.pulsemind.model.WorkspaceSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class EventBus {

    private final ConcurrentLinkedQueue<WorkspaceSignal> queue = new ConcurrentLinkedQueue<>();

    public void publish(WorkspaceSignal signal) {
        queue.offer(signal);
    }

    public List<WorkspaceSignal> drain(int max) {
        List<WorkspaceSignal> batch = new ArrayList<>();
        WorkspaceSignal next;
        while (batch.size() < max && (next = queue.poll()) != null) {
            batch.add(next);
        }
        return batch;
    }

    public int size() {
        return queue.size();
    }
}
