package com.github.jonasrutishauser.transactional.event.core.store;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.context.ContextualProcessor;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;
import com.github.jonasrutishauser.transactional.event.core.cdi.EventHandlerExtension;

@Dependent
class TransactionalWorker {

    private static final Logger LOGGER = LogManager.getLogger();

    private final PendingEventStore store;
    private final Instance<Handler> handlers;
    private final EventHandlerExtension handlerExtension;
    private final EventTypeResolver typeResolver;
    private final ContextualProcessor processor;

    TransactionalWorker() {
        this(null, null, null, null, null);
    }

    @Inject
    TransactionalWorker(PendingEventStore store, @Any Instance<Handler> handlers,
            EventHandlerExtension handlerExtension, EventTypeResolver typeResolver, ContextualProcessor processor) {
        this.store = store;
        this.handlers = handlers;
        this.handlerExtension = handlerExtension;
        this.typeResolver = typeResolver;
        this.processor = processor;
    }

    @Transactional
    public void process(String eventId) {
        PendingEvent event = store.getAndLockEvent(eventId);
        Optional<Class<? extends Handler>> handlerClassWithImplicitType = handlerExtension
                .getHandlerClassWithImplicitType(typeResolver, event.getType());
        Instance<? extends Handler> handlerInstance;
        if (handlerClassWithImplicitType.isPresent()) {
            handlerInstance = handlers.select(handlerClassWithImplicitType.get());
        } else {
            handlerInstance = handlers.select(new EventHandlerLiteral() {
                @Override
                public String eventType() {
                    return event.getType();
                }
            });
        }
        process(event, handlerInstance);
        store.delete(event);
    }

    @Transactional
    public void processFailed(String eventId) {
        PendingEvent event = store.getAndLockEvent(eventId);
        store.updateForRetry(event);
    }

    private <T extends Handler> void process(PendingEvent event, Instance<T> handlerInstance) {
        T handler = handlerInstance.get();
        try {
            processor.process(event.getId(), event.getType(), getContextProperties(event.getContext()),
                    event.getPayload(), handler);
        } finally {
            handlerInstance.destroy(handler);
        }
    }

    private Properties getContextProperties(String context) {
        Properties properties = new Properties();
        if (context != null) {
            try {
                properties.load(new StringReader(context));
            } catch (IOException e) {
                LOGGER.warn("unexpected IOException while reading context", e);
            }
        }
        return properties;
    }

    @SuppressWarnings("serial")
    private abstract static class EventHandlerLiteral extends AnnotationLiteral<EventHandler> implements EventHandler {}

}
