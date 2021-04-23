package com.github.jonasrutishauser.transactional.event.api.monitoring;

public class ProcessingDeletedEvent extends AbstractProcessingEvent {

	private static final long serialVersionUID = 1L;

	public ProcessingDeletedEvent(String eventId) {
		super(eventId);
	}

	@Override
	public String toString() {
		return "ProcessingDeletedEvent [getEventId()=" + getEventId() + "]";
	}

}
