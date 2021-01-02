package com.github.jonasrutishauser.transactional.event.api.serialization;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface EventSerializer<T> {

    String serialize(T event);

    @SuppressWarnings("unchecked")
    default Class<T> getType() {
        for (Type iface : getClass().getGenericInterfaces()) {
            if (iface instanceof ParameterizedType && EventSerializer.class.equals(((ParameterizedType) iface).getRawType())) {
                Type type = ((ParameterizedType) iface).getActualTypeArguments()[0];
                if (type instanceof Class) {
                    return (Class<T>) type;
                } else if (type instanceof ParameterizedType) {
                    return (Class<T>) ((ParameterizedType) type).getRawType();
                }
            }
        }
        throw new IllegalStateException("Class does not implement EventSerializer directly.");
    }

}
