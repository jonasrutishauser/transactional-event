package com.github.jonasrutishauser.transactional.event.quarkus.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.LongSupplier;

import org.eclipse.microprofile.context.ManagedExecutor;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.concurrent.EventExecutor;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
class QuarkusEventExecutor implements EventExecutor {

    private final ManagedExecutor executor;
    private final ScheduledExecutorService scheduler;

    @Inject
    QuarkusEventExecutor(@Events ManagedExecutor executor, ScheduledExecutorService scheduler) {
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
    static class DefaultManagedExecutor {
        private final ManagedExecutor executor;

        @Inject
        DefaultManagedExecutor(ManagedExecutor executor) {
            this.executor = executor;
        }

        @Events
        @Produces
        @DefaultBean
        ManagedExecutor getExecutor() {
            return executor;
        }
    }
}
