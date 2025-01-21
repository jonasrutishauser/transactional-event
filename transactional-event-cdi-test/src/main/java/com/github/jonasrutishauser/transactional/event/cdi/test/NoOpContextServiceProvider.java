package com.github.jonasrutishauser.transactional.event.cdi.test;

import static java.util.Collections.emptyMap;

import java.util.Map;

import jakarta.enterprise.concurrent.ContextService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
class NoOpContextServiceProvider {
    @Produces
    ContextService createContextService() {
        return new ContextService() {
            @Override
            public Map<String, String> getExecutionProperties(Object contextualProxy) {
                return emptyMap();
            }

            @Override
            public Object createContextualProxy(Object instance, Map<String, String> executionProperties,
                    Class<?>... interfaces) {
                return instance;
            }

            @Override
            public <T> T createContextualProxy(T instance, Map<String, String> executionProperties, Class<T> intf) {
                return intf.cast(createContextualProxy(instance, executionProperties, new Class<?>[] {intf}));
            }

            @Override
            public Object createContextualProxy(Object instance, Class<?>... interfaces) {
                return createContextualProxy(instance, emptyMap(), interfaces);
            }

            @Override
            public <T> T createContextualProxy(T instance, Class<T> intf) {
                return intf.cast(createContextualProxy(instance, new Class<?>[] {intf}));
            }
        };
    }
}
