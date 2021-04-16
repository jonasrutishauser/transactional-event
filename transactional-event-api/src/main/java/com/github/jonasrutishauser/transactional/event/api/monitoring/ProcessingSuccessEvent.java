package com.github.jonasrutishauser.transactional.event.api.monitoring;

public class ProcessingSuccessEvent extends AbstractProcessingEvent {

	private static final long serialVersionUID = 1L;

	public ProcessingSuccessEvent() {
	}

	public ProcessingSuccessEvent(String eventId) {
		super(eventId);
	}

}
