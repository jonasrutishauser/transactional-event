package com.github.jonasrutishauser.transactional.event.core.metrics;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.event.TransactionPhase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.Counted;

import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingBlockedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingFailedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingSuccessEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.PublishingEvent;

@ApplicationScoped
public class MetricsEventObserver {

	private static final Logger LOGGER = LogManager.getLogger();

	public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
		LOGGER.debug("initialized");
	}

	@Counted(name = "com.github.jonasrutishauser.transaction.event.failedattempts", description = "counter for failed attempts of processing the events", absolute = true)
	public void processAttemptFailed(@Observes ProcessingFailedEvent e) {
		LOGGER.debug("Got event {}", e);
	}

	@Counted(name = "com.github.jonasrutishauser.transaction.event.success", description = "counter for successfully processed events", absolute = true)
	public void processSuccess(@Observes ProcessingSuccessEvent e) {
		LOGGER.debug("Got event {}", e);
	}

	@Counted(name = "com.github.jonasrutishauser.transaction.event.blocked", description = "counter for blocked events (max attempts reached)", absolute = true)
	public void processBlocked(@Observes ProcessingBlockedEvent e) {
		LOGGER.debug("Got event {}", e);
	}

	@Counted(name = "com.github.jonasrutishauser.transaction.event.published", description = "counter for published events", absolute = true)
	public void published(@Observes(during = TransactionPhase.AFTER_SUCCESS) PublishingEvent e) {
		LOGGER.debug("Got event {}", e);
	}
}
