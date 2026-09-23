package com.swimming.backend.agentwork.application.service;

import com.swimming.backend.agentwork.application.dto.AgentWorkChangeResponse;
import com.swimming.backend.agentwork.application.event.AgentWorkStatusChangedEvent;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 보드 변경 알림 스트림. 단일 서버 메모리에서 사용자별로 여러 탭의 연결을 관리한다.
 *
 * <p>이벤트는 영속 저장하거나 다시 보내지 않는다. 클라이언트는 연결될 때마다 snapshot을 다시 조회해 복구한다.
 */
@Service
public class AgentWorkSseService {
    /** 끊긴 연결도 늦어도 이 시간 안에 정리된다. 살아 있는 탭은 EventSource가 표준 방식으로 다시 연결한다. */
    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(5).toMillis();
    /** SSE retry 필드. 스트림이 끊긴 뒤 브라우저가 다시 연결하기까지 기다리는 시간이다. */
    private static final long RECONNECT_MILLIS = Duration.ofSeconds(3).toMillis();

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            // 완료 콜백은 비동기 디스패치 뒤에 오므로 목록에서 먼저 뺀다.
            remove.run();
            emitter.complete();
        });
        emitter.onError(ignored -> remove.run());
        try {
            // 첫 전송으로 응답을 열어 클라이언트의 open을 발생시키고 재연결 간격을 알린다.
            emitter.send(SseEmitter.event().reconnectTime(RECONNECT_MILLIS).comment("connected"));
        } catch (IOException exception) {
            remove.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(AgentWorkStatusChangedEvent event) {
        List<SseEmitter> userEmitters = emitters.get(event.userId());
        if (userEmitters == null) return;
        var data = new AgentWorkChangeResponse(event.sessionId(), event.status(), event.occurredAt());
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("agent-work").data(data, MediaType.APPLICATION_JSON));
            } catch (IOException exception) {
                // 전송 실패는 이미 커밋된 상태 변경을 되돌리지 않는다. 끊긴 연결만 정리한다.
                remove(event.userId(), emitter);
                emitter.completeWithError(exception);
            } catch (IllegalStateException alreadyCompleted) {
                // 완료 콜백 전에 이미 끝난 연결이다.
                remove(event.userId(), emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        var userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters);
    }
}
