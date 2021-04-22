package com.github.jonasrutishauser.transactional.event.api.monitoring;

import java.util.Objects;

public class ProcessingFailedEvent extends AbstractProcessingEvent {

    private static final long serialVersionUID = 1L;

    private final Exception cause;

    public ProcessingFailedEvent(String eventId, Exception cause) {
        super(eventId);
        this.cause = cause;
    }

    public Exception getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "ProcessingFailedEvent [cause=" + cause + ", getEventId()=" + getEventId() + ", toString()="
                + super.toString() + ", hashCode()=" + hashCode() + ", getClass()=" + getClass() + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(cause);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProcessingFailedEvent other = (ProcessingFailedEvent) obj;
        return Objects.equals(cause, other.cause);
    }

}
