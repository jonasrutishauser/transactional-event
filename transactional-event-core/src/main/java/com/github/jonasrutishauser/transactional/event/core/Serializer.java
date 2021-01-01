package com.github.jonasrutishauser.transactional.event.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.github.jonasrutishauser.transactional.event.api.serialization.EventSerializer;

@ApplicationScoped
class Serializer {

    private final Map<Class<?>, EventSerializer<?>> serializers = new ConcurrentHashMap<>();

    Serializer() {
        // proxy only
    }

    @Inject
    Serializer(@Any Instance<EventSerializer<?>> serializerFactory) {
        for (EventSerializer<?> serializer : serializerFactory) {
            serializers.put(serializer.getType(), serializer);
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
