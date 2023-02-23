package com.github.jonasrutishauser.transactional.event.core.defaults;

import jakarta.enterprise.context.Dependent;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;

@Dependent
public class DefaultEventTypeResolver implements EventTypeResolver {

    @Override
    public String resolve(Class<?> type) {
        return type.getSimpleName();
    }

}
