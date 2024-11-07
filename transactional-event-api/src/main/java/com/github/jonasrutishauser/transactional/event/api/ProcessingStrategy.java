package com.github.jonasrutishauser.transactional.event.api;

import java.time.Duration;

public interface ProcessingStrategy {

    Duration processingLockDuration();

    Duration waitDurationForRetry(int tries);

    int maxTries();

}
