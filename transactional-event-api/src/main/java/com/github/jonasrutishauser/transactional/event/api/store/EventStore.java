package com.github.jonasrutishauser.transactional.event.api.store;

import java.util.Collection;

public interface EventStore {

    boolean unblock(String eventId);

    /**
     * Delete a blocked event.
     * @param eventId
     * @return <code>true</code> if the event has been deleted. <code>false</code> otherwise. 
     */
    boolean delete(String eventId);

    Collection<BlockedEvent> getBlockedEvents(int maxElements);

}
