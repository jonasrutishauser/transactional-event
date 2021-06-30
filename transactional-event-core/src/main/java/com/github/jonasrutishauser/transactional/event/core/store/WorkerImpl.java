package com.github.jonasrutishauser.transactional.event.core.store;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;

import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingFailedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingSuccessEvent;

@Dependent
class WorkerImpl implements Worker {

    private static final Logger LOGGER = LogManager.getLogger();

    private final TransactionalWorker transactional;

    private final Event<ProcessingSuccessEvent> processingSuccessEvent;
    private final Event<ProcessingFailedEvent> processingFailedEvent;

    @Inject
    WorkerImpl(TransactionalWorker transactional, Event<ProcessingSuccessEvent> processingSuccessEvent,
            Event<ProcessingFailedEvent> processingFailedEvent) {
        this.transactional = transactional;
        this.processingSuccessEvent = processingSuccessEvent;
        this.processingFailedEvent = processingFailedEvent;
    }

    @ActivateRequestContext
    @ConcurrentGauge(name = "com.github.jonasrutishauser.transaction.event.processing",
            description = "number of events in processing", absolute = true)
    public boolean process(String eventId) {
        try {
            processAttempt(eventId);
            transactional.process(eventId);
            processSuccess(eventId);
            return true;
        } catch (Exception e) {
            processAttemptFailed(eventId, e);
            transactional.processFailed(eventId);
            return false;
        }
    }

    private void processAttemptFailed(String eventId, Exception e) {
        processingFailedEvent.fire(new ProcessingFailedEvent(eventId, e));
        LOGGER.warn("Failed to process event with id '{}'", eventId, e);
    }

    protected void processSuccess(String eventId) {
        processingSuccessEvent.fire(new ProcessingSuccessEvent(eventId));
        LOGGER.debug("sucessfully processed event with id '{}'", eventId);
    }

    protected void processAttempt(String eventId) {
        LOGGER.debug("processing event with id '{}'", eventId);
    }

}
