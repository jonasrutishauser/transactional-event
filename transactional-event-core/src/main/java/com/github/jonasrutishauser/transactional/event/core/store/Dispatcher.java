package com.github.jonasrutishauser.transactional.event.core.store;

import static java.lang.Math.min;
import static javax.enterprise.event.TransactionPhase.AFTER_SUCCESS;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

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

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

@ApplicationScoped
class Dispatcher implements Scheduler {

    private static final int MAX_INTERVAL = 60;

    private final Scheduler scheduler;
    private final ManagedScheduledExecutorService executor;
    private final PendingEventStore store;
    private final Worker worker;

    private volatile int intervalSeconds = MAX_INTERVAL / 2;

    Dispatcher() {
        this.scheduler = null;
        this.executor = null;
        this.store = null;
        this.worker = null;
    }

    @Inject
    Dispatcher(Scheduler scheduler, @Events ManagedScheduledExecutorService executor, PendingEventStore store,
            Worker worker, @Events @TransientReference ContextService contextService) {
        this.scheduler = contextService.createContextualProxy(scheduler, Scheduler.class);
        this.executor = executor;
        this.store = store;
        this.worker = contextService.createContextualProxy(worker, Worker.class);
    }

    void directDispatch(@Observes(during = AFTER_SUCCESS) EventsPublished events) {
        for (PendingEvent event : events.getEvents()) {
            executor.execute(processor(event.getId()));
        }
    }

    private Runnable processor(String eventId) {
        return () -> {
            if (!worker.process(eventId)) {
                intervalSeconds = 1;
            }
        };
    }

    void startup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        executor.schedule(scheduler::schedule, new Trigger() {
            @Override
            public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
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
        for (Set<String> events = store.aquire(); !events.isEmpty(); events = store.aquire()) {
            processed = true;
            events.stream().map(this::processor).forEach(executor::execute);
        }
        if (processed) {
            intervalSeconds = 1;
        } else {
            intervalSeconds = min(MAX_INTERVAL, intervalSeconds * 2);
        }
    }

}
