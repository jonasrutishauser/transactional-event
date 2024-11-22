package com.github.jonasrutishauser.transactional.event.core.store;

import static jakarta.enterprise.event.Reception.IF_EXISTS;
import static jakarta.enterprise.event.TransactionPhase.AFTER_SUCCESS;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.core.cdi.Startup;
import com.github.jonasrutishauser.transactional.event.core.concurrent.EventExecutor;
import com.github.jonasrutishauser.transactional.event.core.concurrent.EventExecutor.Task;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
class Lifecycle implements Startup {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Dispatcher dispatcher;
    private final EventExecutor executor;

    private Task scheduled;

    Lifecycle() {
        this(null, null);
    }

    @Inject
    Lifecycle(Dispatcher dispatcher, EventExecutor executor) {
        this.dispatcher = dispatcher;
        this.executor = executor;
    }

    void directDispatch(@Observes(during = AFTER_SUCCESS) @Priority(LIBRARY_BEFORE) EventsPublished events) {
        dispatcher.processDirect(events);
    }

    void startup(@Observes @Priority(LIBRARY_AFTER + 500) @Initialized(ApplicationScoped.class) Object event) {
        LOGGER.debug("initialized");
    }

    @PostConstruct
    void startup() {
        scheduled = executor.schedule(this::safeSchedule, dispatcher::dispatchInterval);
    }

    private void safeSchedule() {
        try {
            dispatcher.schedule();
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to schedule event processing", e);
        }
    }

    void shutdown(@Observes(notifyObserver = IF_EXISTS) @Priority(LIBRARY_BEFORE) @BeforeDestroyed(ApplicationScoped.class) Object event) {
        stop();
    }

    @PreDestroy
    void stop() {
        if (scheduled != null) {
            scheduled.cancel();
            scheduled = null;
        }
    }

}
