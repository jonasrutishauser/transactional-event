package com.github.jonasrutishauser.transactional.event.core.store;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static jakarta.enterprise.event.TransactionPhase.AFTER_SUCCESS;
import static org.eclipse.microprofile.metrics.MetricUnits.NONE;
import static org.eclipse.microprofile.metrics.MetricUnits.SECONDS;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.Trigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

@ApplicationScoped
class DispatcherImpl implements Dispatcher {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Configuration configuration;
    private final Dispatcher dispatcher;
    private final ManagedScheduledExecutorService executor;
    private final PendingEventStore store;
    private final Worker worker;
    private final AtomicInteger dispatchedRunning = new AtomicInteger();

    private volatile int intervalSeconds = 30;

    private ScheduledFuture<?> scheduled;

    DispatcherImpl() {
        this.configuration = null;
        this.dispatcher = null;
        this.executor = null;
        this.store = null;
        this.worker = null;
    }

    @Inject
    DispatcherImpl(Configuration configuration, Dispatcher dispatcher, @Events ManagedScheduledExecutorService executor,
            PendingEventStore store, Worker worker) {
        this.configuration = configuration;
        this.dispatcher = dispatcher;
        this.executor = executor;
        this.store = store;
        this.worker = worker;
    }

    @PostConstruct
    void initIntervalSeconds() {
        intervalSeconds = configuration.getInitialDispatchInterval();
    }

    void directDispatch(@Observes(during = AFTER_SUCCESS) EventsPublished events) {
        dispatcher.processDirect(events);
    }

    @Override
    public void processDirect(EventsPublished events) {
        try {
            for (PendingEvent event : events.getEvents()) {
                executor.execute(dispatcher.processor(event.getId()));
            }
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Failed to submit events for processing: {}", e.getMessage());
        }
    }

    @Override
    public Runnable processor(String eventId) {
        return () -> {
            if (!worker.process(eventId)) {
                intervalSeconds = 0;
            }
        };
    }

    void startup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        scheduled = executor.schedule(dispatcher::schedule, new Trigger() {
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

    @PreDestroy
    void stop() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    public synchronized void schedule() {
        boolean processed = false;
        int maxAquire = maxAquire();
        try {
            for (Set<String> events = store.aquire(maxAquire); !events.isEmpty(); events = store.aquire(maxAquire)) {
                processed = true;
                events.stream().map(dispatcher::processor).forEach(this::executeCounting);
                maxAquire = maxAquire();
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
