package com.github.jonasrutishauser.transactional.event.core.store;

import static com.github.jonasrutishauser.transactional.event.core.random.Random.randomId;

import java.time.Clock;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingBlockedEvent;
import com.github.jonasrutishauser.transactional.event.api.ProcessingStrategy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
class LockOwner {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Clock clock;
    private final String id;
    private final Event<ProcessingBlockedEvent> processingBlockedEvent;
    private final ProcessingStrategy processingStrategy;

    LockOwner() {
        this(null, null);
    }

    @Inject
    public LockOwner(Event<ProcessingBlockedEvent> processingBlockedEvent, ProcessingStrategy processingStrategy) {
        this(Clock.systemUTC(), randomId(), processingBlockedEvent, processingStrategy);
    }

    LockOwner(Clock clock, String id, Event<ProcessingBlockedEvent> processingBlockedEvent,
            ProcessingStrategy processingStrategy) {
        LOGGER.info("using lock id: {}", id);
        this.id = id;
        this.clock = clock;
        this.processingBlockedEvent = processingBlockedEvent;
        this.processingStrategy = processingStrategy;
    }

    @Produces
    @Named("lockOwner.id")
    public String getId() {
        return id;
    }

    public long getUntilToProcess() {
        return Instant.now(clock).plus(processingStrategy.processingLockDuration()).toEpochMilli();
    }

    public long getUntilForRetry(int tries, String eventId) {
        if (tries > processingStrategy.maxTries()) {
            maxAttemptsReached(eventId);
            return Long.MAX_VALUE;
        }
        return Instant.now(clock).plus(processingStrategy.waitDurationForRetry(tries)).toEpochMilli();
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
