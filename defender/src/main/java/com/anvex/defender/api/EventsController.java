package com.anvex.defender.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.anvex.defender.events.LiveEventPublisher;
import com.anvex.defender.persistence.Entities;
import com.anvex.defender.persistence.SecurityEventRepository;

/**
 * Event history and the live update stream. Clients always fetch a snapshot first and then
 * subscribe; on reconnect they fetch the snapshot again, so no event is assumed to survive a
 * disconnect.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventsController {

    private final LiveEventPublisher liveEventPublisher;
    private final SecurityEventRepository securityEventRepository;

    public EventsController(LiveEventPublisher liveEventPublisher, SecurityEventRepository securityEventRepository) {
        this.liveEventPublisher = liveEventPublisher;
        this.securityEventRepository = securityEventRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> events(@RequestParam(defaultValue = "50") int limit) {
        return securityEventRepository.findTop50ByOrderByOccurredAtDesc().stream()
                .limit(Math.max(1, Math.min(limit, 200)))
                .map(this::describe)
                .toList();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return liveEventPublisher.subscribe(lastEventId);
    }

    private Map<String, Object> describe(Entities.SecurityEventEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", entity.getEventId());
        body.put("type", entity.getEventType());
        body.put("occurredAt", entity.getOccurredAt());
        body.put("sourceIp", entity.getSourceIp());
        body.put("requestId", entity.getRequestId());
        body.put("critical", entity.isCritical());
        body.put("aggregated", entity.isAggregated());
        return body;
    }
}
