package com.github.jonasrutishauser.transactional.event.core.store;

interface Worker {

    boolean process(String eventId);

}
