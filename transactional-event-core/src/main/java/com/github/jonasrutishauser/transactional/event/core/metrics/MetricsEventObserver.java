package com.github.jonasrutishauser.transactional.event.core.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.Counted;

import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingBlockedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingDeletedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingFailedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingSuccessEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingUnblockedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.PublishingEvent;

@ApplicationScoped
public class MetricsEventObserver {

    private static final String EVENT_LOG_MESSAGE = "Got event {}";

    private static final Logger LOGGER = LogManager.getLogger();

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        LOGGER.debug("initialized");
    }

    @Counted(name = "com.github.jonasrutishauser.transaction.event.failedattempts",
            description = "counter for failed attempts of processing the events", absolute = true)
    public void processAttemptFailed(@Observes ProcessingFailedEvent e) {
        LOGGER.debug(EVENT_LOG_MESSAGE, e);
    }

    @Counted(name = "com.github.jonasrutishauser.transaction.event.success",
            description = "counter for successfully processed events", absolute = true)
    public void processSuccess(@Observes ProcessingSuccessEvent e) {
        LOGGER.debug(EVENT_LOG_MESSAGE, e);
    }

    @Counted(name = "com.github.jonasrutishauser.transaction.event.blocked",
            description = "counter for blocked events (max attempts reached)", absolute = true)
    public void processBlocked(@Observes(during = TransactionPhase.AFTER_SUCCESS) ProcessingBlockedEvent e) {
        LOGGER.debug(EVENT_LOG_MESSAGE, e);
    }

    @Counted(name = "com.github.jonasrutishauser.transaction.event.unblocked",
            description = "counter for unblocked events", absolute = true)
    public void processUnblocked(@Observes(during = TransactionPhase.AFTER_SUCCESS) ProcessingUnblockedEvent e) {
        LOGGER.debug(EVENT_LOG_MESSAGE, e);
    }

    @Counted(name = "com.github.jonasrutishauser.transaction.event.deleted",
            description = "counter for deleted events", absolute = true)
    public void processDeleted(@Observes(during = TransactionPhase.AFTER_SUCCESS) ProcessingDeletedEvent e) {
        LOGGER.debug(EVENT_LOG_MESSAGE, e);
    }

    @Counted(name = "com.github.jonasrutishauser.transaction.event.published",
            description = "counter for published events", absolute = true)
    public void published(@Observes(during = TransactionPhase.AFTER_SUCCESS) PublishingEvent e) {
        LOGGER.debug(EVENT_LOG_MESSAGE, e);
    }
}
