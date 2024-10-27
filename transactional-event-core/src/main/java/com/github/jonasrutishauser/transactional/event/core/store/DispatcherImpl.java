package com.github.jonasrutishauser.transactional.event.core.store;

import static jakarta.enterprise.event.TransactionPhase.AFTER_SUCCESS;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.eclipse.microprofile.metrics.MetricUnits.NONE;
import static org.eclipse.microprofile.metrics.MetricUnits.SECONDS;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;
import com.github.jonasrutishauser.transactional.event.core.concurrent.EventExecutor;
import com.github.jonasrutishauser.transactional.event.core.concurrent.EventExecutor.Task;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
class DispatcherImpl implements Dispatcher {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Configuration configuration;
    private final Dispatcher dispatcher;
    private final EventExecutor executor;
    private final PendingEventStore store;
    private final Worker worker;
    private final AtomicInteger dispatchedRunning = new AtomicInteger();
    private final BlockingQueue<String> eventsToDispatch = new LinkedBlockingQueue<>();

    private volatile int intervalSeconds = 30;

    private Task scheduled;

    DispatcherImpl() {
        this.configuration = null;
        this.dispatcher = null;
        this.executor = null;
        this.store = null;
        this.worker = null;
    }

    @Inject
    DispatcherImpl(Configuration configuration, Dispatcher dispatcher, EventExecutor executor, PendingEventStore store,
            Worker worker) {
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
        for (PendingEvent event : events.getEvents()) {
            String eventId = event.getId();
            if (dispatchable() > 0) {
                try {
                    executor.execute(dispatcher.processor(eventId));
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
    public Runnable processor(String eventId) {
        return () -> {
            if (!worker.process(eventId)) {
                intervalSeconds = 0;
            }
        };
    }

    void startup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        scheduled = executor.schedule(dispatcher::schedule, configuration.getAllInUseInterval(), () -> {
                if (dispatchable() <= 0) {
                    return configuration.getAllInUseInterval();
                }
                return intervalSeconds * 1000l;
        });
    }

    @PreDestroy
    void stop() {
        if (scheduled != null) {
            scheduled.cancel();
            scheduled = null;
        }
    }

    public synchronized void schedule() {
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
                executeCounting(dispatcher.processor(eventsToDispatch.take()));
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
