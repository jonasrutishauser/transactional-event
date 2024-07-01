package com.github.jonasrutishauser.transactional.event.quarkus.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.invoke.Invoker;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

class QuarkusEventHandlers implements EventHandlers {

    private final Map<Class<?>, Class<? extends Handler>> handlerClass;
    private final Invoker<?, ?> startupMethod;

    QuarkusEventHandlers(Map<Class<?>, Class<? extends Handler>> handlerClass, Invoker<?, ?> startupMethod) {
        this.handlerClass = handlerClass;
        this.startupMethod = startupMethod;
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

    void startup(StartupEvent event) throws Exception {
        if (startupMethod != null) {
            startupMethod.invoke(null, new Object[] {event});
        }
    }

    @Singleton
    static class Startup {
        private final QuarkusEventHandlers handlers;
        
        @Inject
        Startup(QuarkusEventHandlers handlers) {
            this.handlers = handlers;
        }
        
        void startup(@Observes StartupEvent event) throws Exception {
            handlers.startup(event);
        }
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
            return new QuarkusEventHandlers(handlerClass, params.get("startup", Invoker.class));
        }

    }

}
