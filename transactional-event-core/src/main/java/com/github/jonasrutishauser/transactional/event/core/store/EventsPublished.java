package com.github.jonasrutishauser.transactional.event.core.store;

import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

public final class EventsPublished implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<PendingEvent> events = new ArrayList<>();
    private boolean closed;

    public boolean addEvent(PendingEvent event) {
        if (closed) {
            throw new UnsupportedOperationException("events already committed");
        }
        events.add(event);
        return events.size() == 1;
    }

    List<PendingEvent> getEvents() {
        closed = true;
        return unmodifiableList(events);
    }
}
