package com.github.jonasrutishauser.transactional.event.api.handler;

import jakarta.inject.Inject;

import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;

public abstract class AbstractHandler<T> implements Handler {

    private EventDeserializer<T> deserializer;

    @Inject
    protected void setDeserializer(EventDeserializer<T> deserializer) {
        this.deserializer = deserializer;
    }

    @Override
    public void handle(String event) {
        handle(deserializer.deserialize(event));
    }
    
    protected abstract void handle(T event);

}
