package com.github.jonasrutishauser.transactional.event.core;

import static com.github.jonasrutishauser.transactional.event.core.random.Random.randomId;
import static jakarta.transaction.Transactional.TxType.MANDATORY;

import java.util.Properties;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.context.ContextualPublisher;

@Dependent
public class TransactionalEventPublisher implements EventPublisher {

    private static final Logger LOGGER = LogManager.getLogger();

    private final EventTypeResolver typeResolver;
    private final Serializer eventSerializer;
    private final ContextualPublisher publisher;

    TransactionalEventPublisher() {
        this(null, null, null);
    }

    @Inject
    TransactionalEventPublisher(EventTypeResolver typeResolver, Serializer eventSerializer,
            ContextualPublisher publisher) {
        this.typeResolver = typeResolver;
        this.eventSerializer = eventSerializer;
        this.publisher = publisher;
    }

    @Override
    @Transactional(MANDATORY)
    public void publish(Object event) {
        String id = randomId();
        String type = typeResolver.resolve(event.getClass());
        String payload = eventSerializer.serialize(event);

        publisher.publish(id, type, new Properties(), payload);

        LOGGER.debug("enqueued event '{}' with type '{}' (payload '{}')", id, type, payload);
    }

}
