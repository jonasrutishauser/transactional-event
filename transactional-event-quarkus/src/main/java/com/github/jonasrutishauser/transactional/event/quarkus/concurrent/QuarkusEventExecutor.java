package com.github.jonasrutishauser.transactional.event.quarkus.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    public Task schedule(Runnable command, LongSupplier intervalInMillis) {
        ScheduledTask task = new ScheduledTask(command, intervalInMillis);
        task.start();
        return task;
    }

    private class ScheduledTask implements Task {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicReference<Future<?>> future = new AtomicReference<>();
        private final Runnable command;
        private final LongSupplier intervalInMillis;

        public ScheduledTask(Runnable command, LongSupplier intervalInMillis) {
            this.command = command;
            this.intervalInMillis = intervalInMillis;
        }

        public void start() {
            if (running.get()) {
                CompletableFuture<Void> commandRun = CompletableFuture.runAsync(command,
                        runnable -> scheduler.schedule(() -> {
                            if (running.get()) {
                                runnable.run();
                            }
                        }, intervalInMillis.getAsLong(), MILLISECONDS));
                future.set(commandRun);
                commandRun.thenRun(this::start);
            }
        }

        @Override
        public void cancel() {
            running.set(false);
            future.get().cancel(true);
        }
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
