package com.github.jonasrutishauser.transactional.event.api.monitoring;

import java.io.Serializable;
import java.util.Objects;

public class PublishingEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String eventId;

    public PublishingEvent(String id) {
        eventId = id;
    }

    public String getEventId() {
        return eventId;
    }

    @Override
    public String toString() {
        return "PublishingEvent [eventId=" + eventId + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PublishingEvent other = (PublishingEvent) obj;
        return Objects.equals(eventId, other.eventId);
    }

}
