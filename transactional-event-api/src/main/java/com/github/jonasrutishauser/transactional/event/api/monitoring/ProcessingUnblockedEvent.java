package com.github.jonasrutishauser.transactional.event.api.monitoring;

public class ProcessingUnblockedEvent extends AbstractProcessingEvent {

    private static final long serialVersionUID = 1L;

    public ProcessingUnblockedEvent(String eventId) {
        super(eventId);
    }

    @Override
    public String toString() {
        return "ProcessingUnblockedEvent [getEventId()=" + getEventId() + "]";
    }

}
