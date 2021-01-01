package com.github.jonasrutishauser.transactional.event.core.store;

import java.util.List;

import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

public final class EventsPublished {

    private final List<PendingEvent> events;

    public EventsPublished(List<PendingEvent> events) {
        this.events = events;
    }

    List<PendingEvent> getEvents() {
        return events;
    }
}
