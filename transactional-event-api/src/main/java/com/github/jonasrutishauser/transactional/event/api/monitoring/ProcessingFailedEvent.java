package com.github.jonasrutishauser.transactional.event.api.monitoring;

public class ProcessingFailedEvent extends AbstractProcessingEvent {

    private static final long serialVersionUID = 1L;

    private Exception cause;

    public ProcessingFailedEvent() {
    }

    public ProcessingFailedEvent(String eventId, Exception cause) {
        super(eventId);
        this.cause = cause;
    }

    public Exception getCause() {
        return cause;
    }

    public void setCause(Exception cause) {
        this.cause = cause;
    }

}
