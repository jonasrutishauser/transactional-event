package com.github.jonasrutishauser.transactional.event.core;

import static com.github.jonasrutishauser.transactional.event.core.random.Random.randomId;
import static java.time.LocalDateTime.now;
import static javax.transaction.Transactional.TxType.MANDATORY;

import java.time.LocalDateTime;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.monitoring.PublishingEvent;

@Dependent
public class TransactionalEventPublisher implements EventPublisher {

    private static final Logger LOGGER = LogManager.getLogger();

    private final EventTypeResolver typeResolver;
    private final Serializer eventSerializer;
    private final PublishedEvents publishedEvents;
    private final Event<PublishingEvent> publishingEvent;

    TransactionalEventPublisher() {
        this(null, null, null, null);
    }

    @Inject
    TransactionalEventPublisher(EventTypeResolver typeResolver, Serializer eventSerializer,
            PublishedEvents publishedEvents, @Any Event<PublishingEvent> publishingEvent) {
        this.typeResolver = typeResolver;
        this.eventSerializer = eventSerializer;
        this.publishedEvents = publishedEvents;
        this.publishingEvent = publishingEvent;
    }

    @Override
    @Transactional(MANDATORY)
    public void publish(Object event) {
        String id = randomId();
        String type = typeResolver.resolve(event.getClass());
        String payload = eventSerializer.serialize(event);
        LocalDateTime publishedAt = now();

        PendingEvent pendingEvent = new PendingEvent(id, type, payload, publishedAt);
        publishedEvents.add(pendingEvent);
        publishingEvent.fire(new PublishingEvent(id));

        LOGGER.debug("enqueued event '{}' with type '{}' (payload '{}')", id, type, payload);
    }

}
