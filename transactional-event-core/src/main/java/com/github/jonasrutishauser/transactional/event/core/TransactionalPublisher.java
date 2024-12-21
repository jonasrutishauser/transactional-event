package com.github.jonasrutishauser.transactional.event.core;

import static jakarta.transaction.Transactional.TxType.MANDATORY;
import static java.time.LocalDateTime.now;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.context.ContextualPublisher;
import com.github.jonasrutishauser.transactional.event.api.monitoring.PublishingEvent;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@Dependent
class TransactionalPublisher implements ContextualPublisher {

    static final String DELAYED_UNTIL_KEY = "___delayed_until___";

    private static final Logger LOGGER = LogManager.getLogger();

    private final PublishedEvents publishedEvents;
    private final Event<PublishingEvent> publishingEvent;

    TransactionalPublisher() {
        this(null, null);
    }

    @Inject
    TransactionalPublisher(PublishedEvents publishedEvents, Event<PublishingEvent> publishingEvent) {
        this.publishedEvents = publishedEvents;
        this.publishingEvent = publishingEvent;
    }

    @Override
    @Transactional(MANDATORY)
    public void publish(String id, String type, Properties context, String payload) {
        Instant delayedUntil = getAndRemoveDelayedUntil(context);
        PendingEvent pendingEvent = new PendingEvent(id, type, getContextString(context), payload, now(), delayedUntil);
        publishedEvents.add(pendingEvent);
        publishingEvent.fire(new PublishingEvent(id));
    }

    private Instant getAndRemoveDelayedUntil(Properties context) {
        Object delayedUntil = context.remove(DELAYED_UNTIL_KEY);
        if (delayedUntil instanceof Instant) {
            return (Instant) delayedUntil;
        }
        return null;
    }

    private String getContextString(Properties context) {
        if (context.isEmpty()) {
            return null;
        }
        try (StringWriter writer = new StringWriter()) {
            context.store(writer, null);
            return writer.getBuffer().substring(writer.getBuffer().indexOf("\n"));
        } catch (IOException e) {
            LOGGER.warn("unexpected IOException while writing context", e);
            return null;
        }
    }

}
