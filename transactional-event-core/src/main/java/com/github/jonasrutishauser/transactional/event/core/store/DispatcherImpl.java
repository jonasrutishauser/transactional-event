package com.github.jonasrutishauser.transactional.event.core.store;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.eclipse.microprofile.metrics.MetricUnits.NONE;
import static org.eclipse.microprofile.metrics.MetricUnits.SECONDS;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;
import com.github.jonasrutishauser.transactional.event.core.concurrent.EventExecutor;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class DispatcherImpl implements Dispatcher {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Configuration configuration;
    private final WorkProcessorImpl processor;
    private final EventExecutor executor;
    private final PendingEventStore store;
    private final AtomicInteger dispatchedRunning = new AtomicInteger();
    private final BlockingQueue<String> eventsToDispatch = new LinkedBlockingQueue<>();

    private volatile int intervalSeconds = 30;

    DispatcherImpl() {
        this.configuration = null;
        this.processor = null;
        this.executor = null;
        this.store = null;
    }

    @Inject
    DispatcherImpl(Configuration configuration, WorkProcessorImpl dispatcher, EventExecutor executor, PendingEventStore store) {
        this.configuration = configuration;
        this.processor = dispatcher;
        this.executor = executor;
        this.store = store;
    }

    @PostConstruct
    void initIntervalSeconds() {
        intervalSeconds = configuration.getInitialDispatchInterval();
    }

    @Override
    public void processDirect(EventsPublished events) {
        for (PendingEvent event : events.getEvents()) {
            String eventId = event.getId();
            if (dispatchable() > 0) {
                try {
                    executeCounting(eventId);
                } catch (RejectedExecutionException e) {
                    LOGGER.warn("Failed to submit event {} for processing: {}", eventId, e.getMessage());
                }
            } else if (eventsToDispatch.size() < 8 * configuration.getMaxAquire()) {
                if (!eventsToDispatch.offer(eventId)) {
                    LOGGER.warn("Failed to submit event {} for processing", eventId);
                }
            } else {
                LOGGER.warn("There are already too many events to process event {}", eventId);
            }
        }
    }

    @Override
    public long dispatchInterval() {
        if (dispatchable() <= 0) {
            return configuration.getAllInUseInterval();
        }
        return intervalSeconds * 1000l;
    }

    public void schedule() {
        try {
            scheduleImpl();
        } catch (RuntimeException e) {
            intervalSeconds = min(configuration.getMaxDispatchInterval(), max(intervalSeconds * 2, 1));
            throw e;
        }
    }

    private synchronized void scheduleImpl() {
        for (boolean empty = false; !empty && eventsToDispatch.size() < configuration.getMaxConcurrentDispatching();) {
            Set<String> events = store.aquire(configuration.getMaxAquire());
            events.forEach(eventsToDispatch::offer);
            empty = events.isEmpty();
        }
        if (dispatchable() > 0 || !eventsToDispatch.isEmpty()) {
            intervalSeconds = 0;
        } else {
            intervalSeconds = min(configuration.getMaxDispatchInterval(), max(intervalSeconds * 2, 1));
        }
        while (dispatchable() > 0 && !eventsToDispatch.isEmpty()) {
            try {
                executeCounting(eventsToDispatch.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RejectedExecutionException e) {
                LOGGER.warn("Failed to dispatch events: {}", e.getMessage());
            }
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

    private int dispatchable() {
        return configuration.getMaxConcurrentDispatching() - dispatchedRunning.get();
    }

    private void executeCounting(String eventId) {
        Callable<Boolean> supplier = processor.get(eventId);
        try {
            executor.execute(counting(() -> {
                try {
                    if (!Boolean.TRUE.equals(supplier.call())) {
                        intervalSeconds = 0;
                    }
                } catch (Exception e) {
                    LOGGER.catching(e);
                }
            }));
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
