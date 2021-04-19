package com.github.jonasrutishauser.transactional.event.api.monitoring;

public class ProcessingBlockedEvent extends AbstractProcessingEvent {

    private static final long serialVersionUID = 1L;

    public ProcessingBlockedEvent(String eventId) {
        super(eventId);
    }

    @Override
    public String toString() {
        return "ProcessingBlockedEvent [getEventId()=" + getEventId() + "]";
    }

}
