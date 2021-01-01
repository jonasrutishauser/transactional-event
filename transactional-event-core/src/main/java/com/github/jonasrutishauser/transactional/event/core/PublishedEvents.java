package com.github.jonasrutishauser.transactional.event.core;

import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.transaction.TransactionScoped;

import com.github.jonasrutishauser.transactional.event.core.store.EventsPublished;

@TransactionScoped
class PublishedEvents implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Event<EventsPublished> eventEmiter;
    private final List<PendingEvent> events = new ArrayList<>();

    PublishedEvents() {
        this(null);
    }

    @Inject
    PublishedEvents(@Any Event<EventsPublished> eventEmiter) {
        this.eventEmiter = eventEmiter;
    }

    public void add(PendingEvent event) {
        events.add(event);
        if (events.size() == 1) {
            eventEmiter.fire(new EventsPublished(unmodifiableList(events)));
        }
    }

}
