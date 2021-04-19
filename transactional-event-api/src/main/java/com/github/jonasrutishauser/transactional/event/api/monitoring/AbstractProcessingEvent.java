package com.github.jonasrutishauser.transactional.event.api.monitoring;

import java.io.Serializable;

public abstract class AbstractProcessingEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String eventId;

    protected AbstractProcessingEvent(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    @Override
    public String toString() {
        return "AbstractProcessingEvent [eventId=" + eventId + "]";
    }

}
