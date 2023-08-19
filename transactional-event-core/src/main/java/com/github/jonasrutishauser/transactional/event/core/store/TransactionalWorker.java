package com.github.jonasrutishauser.transactional.event.core.store;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.jakarta.enterprise.inject.ExtendedInstance;
import com.github.jonasrutishauser.jakarta.enterprise.inject.ExtendedInstance.Handle;
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
    private final ExtendedInstance<Handler> handlers;
    private final EventHandlerExtension handlerExtension;
    private final EventTypeResolver typeResolver;
    private final ContextualProcessor processor;

    TransactionalWorker() {
        this(null, null, null, null, null);
    }

    @Inject
    TransactionalWorker(PendingEventStore store, @Any ExtendedInstance<Handler> handlers,
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
        processor.process(event.getId(), event.getType(), getContextProperties(event.getContext()), event.getPayload(),
                getHandler(event.getType()));
        store.delete(event);
    }

    @Transactional
    public void processFailed(String eventId) {
        PendingEvent event = store.getAndLockEvent(eventId);
        store.updateForRetry(event);
    }

    private Handler getHandler(String eventType) {
        return payload -> {
            Optional<Class<? extends Handler>> handlerClassWithImplicitType = handlerExtension
                    .getHandlerClassWithImplicitType(typeResolver, eventType);
            ExtendedInstance<? extends Handler> handlerInstance;
            if (handlerClassWithImplicitType.isPresent()) {
                handlerInstance = handlers.select(handlerClassWithImplicitType.get());
            } else {
                handlerInstance = handlers.select(new EventHandlerLiteral(eventType));
            }
            try (Handle<? extends Handler> handle = handlerInstance.getPseudoScopeClosingHandle()) {
                handle.get().handle(payload);
            }
        };
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
    private static class EventHandlerLiteral extends AnnotationLiteral<EventHandler> implements EventHandler {
        private final String eventType;

        public EventHandlerLiteral(String eventType) {
            this.eventType = eventType;
        }

        @Override
        public String eventType() {
            return eventType;
        }
    }

}
