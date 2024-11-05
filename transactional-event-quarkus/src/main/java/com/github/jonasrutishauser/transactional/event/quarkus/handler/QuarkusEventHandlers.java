package com.github.jonasrutishauser.transactional.event.quarkus.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

class QuarkusEventHandlers implements EventHandlers {

    private final Map<Class<?>, Class<? extends Handler>> handlerClass;

    QuarkusEventHandlers(Map<Class<?>, Class<? extends Handler>> handlerClass) {
        this.handlerClass = handlerClass;
    }

    @Override
    public Optional<Class<? extends Handler>> getHandlerClassWithImplicitType(EventTypeResolver typeResolver,
            String type) {
        for (Entry<Class<?>, Class<? extends Handler>> handlerClassEntry : handlerClass.entrySet()) {
            if (type.equals(typeResolver.resolve(handlerClassEntry.getKey()))) {
                return Optional.of(handlerClassEntry.getValue());
            }
        }
        return Optional.empty();
    }

    static class Creator implements SyntheticBeanCreator<EventHandlers> {

        @Override
        public QuarkusEventHandlers create(Instance<Object> lookup, Parameters params) {
            Class<?>[] types = params.get("types", Class[].class);
            Class<?>[] beans = params.get("beans", Class[].class);
            Map<Class<?>, Class<? extends Handler>> handlerClass = new HashMap<>();
            for (int i = 0; i < types.length; i++) {
                handlerClass.put(types[i], beans[i].asSubclass(Handler.class));
            }
            return new QuarkusEventHandlers(handlerClass);
        }

    }

}
