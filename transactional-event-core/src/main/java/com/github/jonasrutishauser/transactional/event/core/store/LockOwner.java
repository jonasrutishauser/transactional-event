package com.github.jonasrutishauser.transactional.event.core.store;

import static com.github.jonasrutishauser.transactional.event.core.random.Random.randomId;

import java.time.Clock;
import java.time.Instant;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ApplicationScoped
class LockOwner {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Clock clock;
    private final String id;

    @Inject
    public LockOwner() {
        this(Clock.systemUTC());
    }
    
    LockOwner(Clock clock) {
        id = randomId();
        LOGGER.info("using lock id: {}", id);
        this.clock = clock;
    }

    public String getId() {
        return id;
    }

    public long getUntilToProcess() {
        return Instant.now(clock).plusSeconds(300).toEpochMilli();
    }

    public long getUntilForRetry(int tries) {
        if (tries > 5) {
            return Long.MAX_VALUE;
        }
        return Instant.now(clock).plusSeconds(tries * tries * 2).toEpochMilli();
    }

    public long getMinUntilForAquire() {
        return Instant.now(clock).toEpochMilli();
    }

    public boolean isOwningForProcessing(String owner, long lockedUntil) {
        return id.equals(owner) && lockedUntil > Instant.now(clock).toEpochMilli();
    }

}
