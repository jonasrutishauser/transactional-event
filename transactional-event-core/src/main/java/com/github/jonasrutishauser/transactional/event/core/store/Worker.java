package com.github.jonasrutishauser.transactional.event.core.store;

public interface Worker {

    boolean process(String eventId);

}
