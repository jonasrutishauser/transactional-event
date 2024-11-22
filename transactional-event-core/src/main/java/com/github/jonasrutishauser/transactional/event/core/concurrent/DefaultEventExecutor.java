package com.github.jonasrutishauser.transactional.event.core.concurrent;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.function.LongSupplier;

import com.github.jonasrutishauser.transactional.event.api.Events;

import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.Trigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DefaultEventExecutor implements EventExecutor {

    private final ManagedScheduledExecutorService executor;

    DefaultEventExecutor() {
        this(null);
    }

    @Inject
    DefaultEventExecutor(@Events ManagedScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    @Override
    public Task schedule(Runnable command, LongSupplier interval) {
        ScheduledFuture<?> future = executor.schedule(command, new Trigger() {
            @Override
            public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
                return false;
            }

            @Override
            public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
                return Date.from(Instant.now().plusMillis(interval.getAsLong()));
            }
        });
        return () -> future.cancel(false);
    }

}
