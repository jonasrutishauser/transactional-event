package com.github.jonasrutishauser.transactional.event.quarkus.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.LongSupplier;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.concurrent.EventExecutor;

import io.quarkus.arc.DefaultBean;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
class QuarkusEventExecutor implements EventExecutor {

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    QuarkusEventExecutor() {
        this(null, null);
    }

    @Inject
    QuarkusEventExecutor(@Events ExecutorService executor, ScheduledExecutorService scheduler) {
        this.executor = executor;
        this.scheduler = scheduler;
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    @Override
    public Task schedule(Runnable command, long minInterval, LongSupplier intervalInMillis) {
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(new Runnable() {
            private Instant nextRun = Instant.now().plusMillis(intervalInMillis.getAsLong());
            @Override
            public void run() {
                if (!Instant.now().isBefore(nextRun)) {
                    command.run();
                    nextRun = Instant.now().plusMillis(intervalInMillis.getAsLong());
                }
            }
        }, minInterval, minInterval, MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Dependent
    static class DefaultExecutorService {
        private final ExecutorService executor;

        @Inject
        DefaultExecutorService(@VirtualThreads ExecutorService executor) {
            this.executor = executor;
        }

        @Events
        @Produces
        @DefaultBean
        ExecutorService getExecutor() {
            return executor;
        }
    }
}
