package com.github.jonasrutishauser.transactional.event.core.cdi;

import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;

public class DefaultEventDeserializer<T> implements ExtendedEventDeserializer<T> {

    private final Class<T> eventType;
    private final GenericSerialization serialization;

    protected DefaultEventDeserializer(Class<T> eventType, GenericSerialization serialization) {
        this.eventType = eventType;
        this.serialization = serialization;
        if (!serialization.accepts(eventType)) {
            throw new IllegalArgumentException("wrong GenericSerialization");
        }
    }

    @Override
    public T deserialize(String event) {
        return serialization.deserialize(event, eventType);
    }

}
