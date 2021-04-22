package com.github.jonasrutishauser.transactional.event.api.monitoring;

import java.io.Serializable;
import java.util.Objects;

abstract class AbstractProcessingEvent implements Serializable {

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
        AbstractProcessingEvent other = (AbstractProcessingEvent) obj;
        return Objects.equals(eventId, other.eventId);
    }

}
