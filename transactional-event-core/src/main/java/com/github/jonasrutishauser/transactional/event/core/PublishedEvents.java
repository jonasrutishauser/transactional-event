package com.github.jonasrutishauser.transactional.event.core;

import java.io.Serializable;

import com.github.jonasrutishauser.transactional.event.core.store.EventsPublished;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionScoped;

@TransactionScoped
class PublishedEvents implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Event<EventsPublished> eventEmiter;
    private final EventsPublished events = new EventsPublished();

    PublishedEvents() {
        this(null);
    }

    @Inject
    PublishedEvents(@Any Event<EventsPublished> eventEmiter) {
        this.eventEmiter = eventEmiter;
    }

    public void add(PendingEvent event) {
        if (events.addEvent(event)) {
            eventEmiter.fire(events);
        }
    }

}
