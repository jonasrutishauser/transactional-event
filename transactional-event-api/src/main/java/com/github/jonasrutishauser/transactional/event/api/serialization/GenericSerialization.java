package com.github.jonasrutishauser.transactional.event.api.serialization;

import javax.annotation.Priority;

public interface GenericSerialization {

    boolean accepts(Class<?> type);

    String serialize(Object event);
    
    <T> T deserialize(String event, Class<T> type);

    default int priority() {
        Priority priority = getClass().getAnnotation(Priority.class);
        return priority == null ? 0 : priority.value();
    }

}
