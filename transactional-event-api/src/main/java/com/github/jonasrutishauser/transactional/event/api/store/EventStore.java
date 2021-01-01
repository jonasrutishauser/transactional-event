package com.github.jonasrutishauser.transactional.event.api.store;

import java.util.Collection;

public interface EventStore {

    boolean unblock(String eventId);

    Collection<BlockedEvent> getBlockedEvents(int maxElements);

}
