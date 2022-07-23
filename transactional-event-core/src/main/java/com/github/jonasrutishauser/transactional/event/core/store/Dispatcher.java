package com.github.jonasrutishauser.transactional.event.core.store;

public interface Dispatcher {
    void schedule();

    void processDirect(EventsPublished events);

    Runnable processor(String eventId);
}
