package com.github.jonasrutishauser.transactional.event.core.store;

import static com.github.jonasrutishauser.transactional.event.core.random.Random.randomId;
import static java.time.temporal.ChronoUnit.MINUTES;

import java.time.Clock;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingBlockedEvent;

@ApplicationScoped
class LockOwner {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Clock clock;
    private final String id;
    private final Event<ProcessingBlockedEvent> processingBlockedEvent;

    LockOwner() {
        this(null);
    }

    @Inject
    public LockOwner(Event<ProcessingBlockedEvent> processingBlockedEvent) {
        this(Clock.systemUTC(), randomId(), processingBlockedEvent);
    }

    LockOwner(Clock clock, String id, Event<ProcessingBlockedEvent> processingBlockedEvent) {
        LOGGER.info("using lock id: {}", id);
        this.id = id;
        this.clock = clock;
        this.processingBlockedEvent = processingBlockedEvent;
    }

    @Produces
    @Named("lockOwner.id")
    public String getId() {
        return id;
    }

    public long getUntilToProcess() {
        return Instant.now(clock).plus(5, MINUTES).toEpochMilli();
    }

    public long getUntilForRetry(int tries, String eventId) {
        if (tries > 5) {
            maxAttemptsReached(eventId);
            return Long.MAX_VALUE;
        }
        return Instant.now(clock).plusSeconds(tries * tries * 2l).toEpochMilli();
    }

    protected void maxAttemptsReached(String eventId) {
        LOGGER.info("max attempts used, event with id '{}' will be blocked", eventId);
        processingBlockedEvent.fire(new ProcessingBlockedEvent(eventId));
    }

    public long getMinUntilForAquire() {
        return Instant.now(clock).toEpochMilli();
    }

    public boolean isOwningForProcessing(String owner, long lockedUntil) {
        return id.equals(owner) && lockedUntil > Instant.now(clock).toEpochMilli();
    }

}
