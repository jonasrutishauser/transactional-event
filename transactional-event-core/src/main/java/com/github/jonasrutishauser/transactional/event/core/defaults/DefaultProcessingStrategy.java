package com.github.jonasrutishauser.transactional.event.core.defaults;

import java.time.Duration;

import com.github.jonasrutishauser.transactional.event.api.ProcessingStrategy;

import jakarta.enterprise.context.Dependent;

@Dependent
public class DefaultProcessingStrategy implements ProcessingStrategy {

    @Override
    public Duration processingLockDuration() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Duration waitDurationForRetry(int tries) {
        return Duration.ofSeconds(tries * tries * 2l);
    }

    @Override
    public int maxTries() {
        return 5;
    }

}
