package com.github.jonasrutishauser.transactional.event.core.cdi;

import java.lang.reflect.UndeclaredThrowableException;

import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;

import jakarta.enterprise.invoke.Invoker;

public class SyntheticHandler<T> implements Handler {
    private final EventDeserializer<T> deserializer;
    private final Invoker<?, ?> invoker;

    public SyntheticHandler(EventDeserializer<T> deserializer, Invoker<?, ?> invoker) {
        this.deserializer = deserializer;
        this.invoker = invoker;
    }

    @Override
    public void handle(String event) {
        try {
            invoker.invoke(null, new Object[] {deserializer.deserialize(event)});
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }
}