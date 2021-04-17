package com.github.jonasrutishauser.transactional.event.api.monitoring;

import java.io.Serializable;

public class PublishingEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String eventId;

    public PublishingEvent(String id) {
        eventId = id;
    }

    public String getEventId() {
        return eventId;
    }
}
