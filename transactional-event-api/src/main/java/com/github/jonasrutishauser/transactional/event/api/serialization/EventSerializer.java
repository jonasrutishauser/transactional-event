package com.github.jonasrutishauser.transactional.event.api.serialization;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface EventSerializer<T> {

    String serialize(T event);

    @SuppressWarnings("unchecked")
    default Class<T> getType() {
        for (Type iface : getClass().getGenericInterfaces()) {
            if (iface instanceof ParameterizedType && EventSerializer.class.equals(((ParameterizedType) iface).getRawType())) {
                return (Class<T>) ((ParameterizedType) iface).getActualTypeArguments()[0];
            }
        }
        throw new IllegalStateException("Class does not implement EventSerializer directly.");
    }

}
