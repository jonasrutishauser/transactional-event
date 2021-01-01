package com.github.jonasrutishauser.transactional.event.api.store;

import java.time.LocalDateTime;

public class BlockedEvent {

    private final String eventId;
    private final String eventType;
    private final String payload;
    private final LocalDateTime publishedAt;

    public BlockedEvent(String eventId, String eventType, String payload, LocalDateTime publishedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.publishedAt = publishedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

}
