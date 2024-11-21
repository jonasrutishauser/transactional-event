package com.github.jonasrutishauser.transactional.event.quarkus.handler;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.core.cdi.EventHandlerLiteral;
import com.github.jonasrutishauser.transactional.event.core.cdi.TypedEventHandler;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

class QuarkusEventHandlers implements EventHandlers {

    private final Set<Class<?>> handledTypes;

    QuarkusEventHandlers(Set<Class<?>> handledTypes) {
        this.handledTypes = handledTypes;
    }

    @Override
    public Annotation getHandlerQualifier(EventTypeResolver typeResolver, String type) {
        for (Class<?> handledType : handledTypes) {
            if (type.equals(typeResolver.resolve(handledType))) {
                return TypedEventHandler.Literal.of(handledType);
            }
        }
        return EventHandlerLiteral.of(type);
    }

    static class Creator implements SyntheticBeanCreator<EventHandlers> {
        @Override
        public QuarkusEventHandlers create(Instance<Object> lookup, Parameters params) {
            Class<?>[] types = params.get("types", Class[].class);
            Set<Class<?>> typeSet = ConcurrentHashMap.newKeySet();
            typeSet.addAll(Arrays.asList(types));
            return new QuarkusEventHandlers(typeSet);
        }
    }
}
