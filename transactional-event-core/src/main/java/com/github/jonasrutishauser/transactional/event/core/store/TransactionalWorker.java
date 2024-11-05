package com.github.jonasrutishauser.transactional.event.core.store;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.context.ContextualProcessor;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;

@Dependent
class TransactionalWorker {

    private static final Logger LOGGER = LogManager.getLogger();

    private final PendingEventStore store;
    private final HandlerProvider handlerProvider;
    private final ContextualProcessor processor;

    TransactionalWorker() {
        this(null, null, null, null, null);
    }

    @Inject
    TransactionalWorker(PendingEventStore store, @Any Instance<Handler> handlers, EventHandlers handlerExtension,
            EventTypeResolver typeResolver, ContextualProcessor processor) {
        this.store = store;
        this.handlerProvider = new HandlerProvider(handlers, handlerExtension, typeResolver);
        this.processor = processor;
    }

    @Transactional
    public void process(String eventId) {
        PendingEvent event = store.getAndLockEvent(eventId);
        processor.process(event.getId(), event.getType(), getContextProperties(event.getContext()), event.getPayload(),
                handlerProvider.handler(event.getType()));
        store.delete(event);
    }

    @Transactional
    public void processFailed(String eventId) {
        PendingEvent event = store.getAndLockEvent(eventId);
        store.updateForRetry(event);
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

    private static class HandlerProvider {
        private final ConcurrentMap<String, Handler> handlerMap = new ConcurrentHashMap<>();
        private final Instance<Handler> handlers;
        private final EventHandlers handlerExtension;
        private final EventTypeResolver typeResolver;

        public HandlerProvider(Instance<Handler> handlers, EventHandlers handlerExtension,
                EventTypeResolver typeResolver) {
            this.handlers = handlers;
            this.handlerExtension = handlerExtension;
            this.typeResolver = typeResolver;
        }

        public Handler handler(String eventType) {
            return handlerMap.computeIfAbsent(eventType, this::getHandler);
        }

        private synchronized Handler getHandler(String eventType) {
            if (handlerMap.containsKey(eventType)) {
                // because this method is synchronized we can ensure that an instance is only
                // created once
                return handlerMap.get(eventType);
            }
            Optional<Class<? extends Handler>> handlerClassWithImplicitType = handlerExtension
                    .getHandlerClassWithImplicitType(typeResolver, eventType);
            if (handlerClassWithImplicitType.isPresent()) {
                return handlers.select(handlerClassWithImplicitType.get()).get();
            }
            return handlers.select(new EventHandlerLiteral(eventType)).get();
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
}
