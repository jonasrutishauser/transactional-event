package com.github.jonasrutishauser.transactional.event.core;

import java.io.Serializable;
import java.time.LocalDateTime;

public class PendingEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String type;
    private final String payload;
    private final LocalDateTime publishedAt;
    private final int tries;

    public PendingEvent(String id, String type, String payload, LocalDateTime publishedAt) {
        this(id, type, payload, publishedAt, 0);
    }

    public PendingEvent(String id, String type, String payload, LocalDateTime publishedAt, int tries) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.publishedAt = publishedAt;
        this.tries = tries;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
    
    public int getTries() {
        return tries;
    }

}
