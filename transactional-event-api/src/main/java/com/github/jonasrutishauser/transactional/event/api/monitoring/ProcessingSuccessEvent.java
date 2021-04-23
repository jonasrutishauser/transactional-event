package com.github.jonasrutishauser.transactional.event.api.monitoring;

public class ProcessingSuccessEvent extends AbstractProcessingEvent {

    private static final long serialVersionUID = 1L;

    public ProcessingSuccessEvent(String eventId) {
        super(eventId);
    }

    @Override
    public String toString() {
        return "ProcessingSuccessEvent [getEventId()=" + getEventId() + ", toString()=" + super.toString()
                + ", hashCode()=" + hashCode() + ", getClass()=" + getClass() + "]";
    }

}
