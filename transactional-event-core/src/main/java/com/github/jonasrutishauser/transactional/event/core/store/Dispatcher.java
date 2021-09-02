package com.github.jonasrutishauser.transactional.event.core.store;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static javax.enterprise.event.TransactionPhase.AFTER_SUCCESS;
import static org.eclipse.microprofile.metrics.MetricUnits.NONE;
import static org.eclipse.microprofile.metrics.MetricUnits.SECONDS;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.enterprise.concurrent.ContextService;
import javax.enterprise.concurrent.LastExecution;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.Trigger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.TransientReference;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

@ApplicationScoped
class Dispatcher implements Scheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Configuration configuration;
    private final Scheduler scheduler;
    private final ManagedScheduledExecutorService executor;
    private final PendingEventStore store;
    private final Worker worker;
    private final AtomicInteger dispatchedRunning = new AtomicInteger();

    private volatile int intervalSeconds = 30;

    Dispatcher() {
        this.configuration = null;
        this.scheduler = null;
        this.executor = null;
        this.store = null;
        this.worker = null;
    }

    @Inject
    Dispatcher(Configuration configuration, Scheduler scheduler, @Events ManagedScheduledExecutorService executor,
            PendingEventStore store, Worker worker, @Events @TransientReference ContextService contextService) {
        this.configuration = configuration;
        this.scheduler = contextService.createContextualProxy(scheduler, Scheduler.class);
        this.executor = executor;
        this.store = store;
        this.worker = contextService.createContextualProxy(worker, Worker.class);
    }

    @PostConstruct
    void initIntervalSeconds() {
        intervalSeconds = configuration.getInitialDispatchInterval();
    }

    void directDispatch(@Observes(during = AFTER_SUCCESS) EventsPublished events) {
        try {
            for (PendingEvent event : events.getEvents()) {
                executor.execute(processor(event.getId()));
            }
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Failed to submit events for processing: {}", e.getMessage());
        }
    }

    private Runnable processor(String eventId) {
        return () -> {
            if (!worker.process(eventId)) {
                intervalSeconds = 0;
            }
        };
    }

    void startup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        executor.schedule(scheduler::schedule, new Trigger() {
            @Override
            public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
                if (maxAquire() <= 0) {
                    return Date.from(Instant.now().plusMillis(configuration.getAllInUseInterval()));
                }
                return Date.from(Instant.now().plusSeconds(intervalSeconds));
            }

            @Override
            public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
                return false;
            }
        });
    }

    @ActivateRequestContext
    public synchronized void schedule() {
        boolean processed = false;
        int maxAquire = maxAquire();
        try {
            for (Set<String> events = store.aquire(maxAquire); !events.isEmpty();
                    events = store.aquire(maxAquire = maxAquire())) {
                processed = true;
                events.stream().map(this::processor).forEach(this::executeCounting);
            }
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Failed to dispatch events: {}", e.getMessage());
        }
        if (processed || maxAquire <= 0) {
            intervalSeconds = 0;
        } else {
            intervalSeconds = min(configuration.getMaxDispatchInterval(), max(intervalSeconds * 2, 1));
        }
    }

    @Gauge(name = "com.github.jonasrutishauser.transaction.event.dispatched.processing",
            description = "Number of dispatched events being processed", unit = NONE, absolute = true)
    public int getDispatchedRunning() {
        return dispatchedRunning.get();
    }

    @Gauge(name = "com.github.jonasrutishauser.transaction.event.dispatch.interval",
            description = "Interval between lookups for events to process", unit = SECONDS, absolute = true)
    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    private int maxAquire() {
        return configuration.getMaxConcurrentDispatching() - dispatchedRunning.get();
    }

    private void executeCounting(Runnable task) {
        try {
            executor.execute(counting(task));
        } catch (RejectedExecutionException e) {
            dispatchedRunning.decrementAndGet();
            throw e;
        }
    }

    private Runnable counting(Runnable task) {
        dispatchedRunning.incrementAndGet();
        return () -> {
            try {
                task.run();
            } finally {
                dispatchedRunning.decrementAndGet();
            }
        };
    }

}
