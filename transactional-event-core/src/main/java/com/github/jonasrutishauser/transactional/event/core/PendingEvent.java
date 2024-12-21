package com.github.jonasrutishauser.transactional.event.core;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

public class PendingEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String type;
    private final String context;
    private final String payload;
    private final LocalDateTime publishedAt;
    private final Instant delayedUntil;
    private final int tries;

    public PendingEvent(String id, String type, String context, String payload, LocalDateTime publishedAt, Instant delayedUntil) {
        this(id, type, context, payload, publishedAt, delayedUntil, 0);
    }

    public PendingEvent(String id, String type, String context, String payload, LocalDateTime publishedAt, Instant delayedUntil, int tries) {
        this.id = id;
        this.type = type;
        this.context = context;
        this.payload = payload;
        this.publishedAt = publishedAt;
        this.delayedUntil = delayedUntil;
        this.tries = tries;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getContext() {
        return context;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public Optional<Instant> getDelayedUntil() {
        return Optional.ofNullable(delayedUntil);
    }

    public int getTries() {
        return tries;
    }

}
