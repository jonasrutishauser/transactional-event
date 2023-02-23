package com.github.jonasrutishauser.transactional.event.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import com.github.jonasrutishauser.transactional.event.api.serialization.EventSerializer;

@ApplicationScoped
class Serializer {

    private final Map<Class<?>, EventSerializer<?>> serializers = new ConcurrentHashMap<>();

    Serializer() {
        // proxy only
    }

    @Inject
    Serializer(@Any Instance<EventSerializer<?>> serializers) {
        for (EventSerializer<?> serializer : serializers) {
            this.serializers.put(serializer.getType(), serializer);
        }
    }

    String serialize(Object event) {
        return serialize(getSerializer(event), event);
    }

    private <T> String serialize(EventSerializer<T> serializer, Object event) {
        return serializer.serialize(serializer.getType().cast(event));
    }

    private EventSerializer<?> getSerializer(Object event) {
        return serializers.computeIfAbsent(event.getClass(), this::getSerializer);
    }

    private EventSerializer<?> getSerializer(Class<?> eventType) {
        for (Class<?> type = eventType.getSuperclass(); type != null; type = type.getSuperclass()) {
            if (serializers.containsKey(type)) {
                return serializers.get(type);
            }
        }
        throw new IllegalArgumentException("Serializer not found for " + eventType.getName());
    }

}
