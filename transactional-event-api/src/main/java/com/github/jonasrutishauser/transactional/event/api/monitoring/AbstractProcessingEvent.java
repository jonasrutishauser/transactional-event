package com.github.jonasrutishauser.transactional.event.api.monitoring;

import java.io.Serializable;

public abstract class AbstractProcessingEvent implements Serializable {

	private static final long serialVersionUID = 1L;

	private String eventId;

	public AbstractProcessingEvent() {
	}

	public AbstractProcessingEvent(String eventId) {
		this.eventId = eventId;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	@Override
	public String toString() {
		return "AbstractProcessingEvent [eventId=" + eventId + "]";
	}

}
