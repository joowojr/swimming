package com.swimming.backend.agentwork.application.event;

import com.swimming.backend.agentwork.application.service.AgentWorkSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AgentWorkSseEventListener {
    private final AgentWorkSseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(AgentWorkStatusChangedEvent event) {
        sseService.publish(event);
    }
}
