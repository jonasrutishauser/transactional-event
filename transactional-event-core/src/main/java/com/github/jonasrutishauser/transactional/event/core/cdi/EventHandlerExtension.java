package com.github.jonasrutishauser.transactional.event.core.cdi;

import static java.util.Collections.sort;
import static java.util.Comparator.comparing;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Any.Literal;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;

public class EventHandlerExtension implements Extension, EventHandlers {

    private final Set<ParameterizedType> requiredEventDeserializers = new HashSet<>();
    private final Set<Class<?>> genericSerializationEventTypes = new HashSet<>();

    private final Map<ParameterizedType, Class<? extends Handler>> handlerClass = new HashMap<>();

    @Override
    public Optional<Class<? extends Handler>> getHandlerClassWithImplicitType(EventTypeResolver typeResolver,
            String type) {
        for (Entry<ParameterizedType, Class<? extends Handler>> handlerClassEntry : handlerClass.entrySet()) {
            if (type.equals(typeResolver.resolve((Class<?>) handlerClassEntry.getKey().getActualTypeArguments()[0]))) {
                return Optional.of(handlerClassEntry.getValue());
            }
        }
        return Optional.empty();
    }

    <T extends Handler> void processHandlers(@Observes @Priority(LIBRARY_AFTER) ProcessBean<T> beanEvent) {
        if (!beanEvent.getAnnotated().isAnnotationPresent(EventHandler.class)) {
            beanEvent.addDefinitionError(
                    new IllegalStateException("EventHandler annotation is missing on bean " + beanEvent.getBean()));
        } else {
            EventHandler annotation = beanEvent.getAnnotated().getAnnotation(EventHandler.class);
            Optional<ParameterizedType> abstractHandlerType = getAbstractHandlerType(beanEvent.getBean().getTypes());
            if (EventHandler.ABSTRACT_HANDLER_TYPE.equals(annotation.eventType())) {
                if (!abstractHandlerType.isPresent()) {
                    beanEvent.addDefinitionError(new IllegalStateException("AbstractHandler type is missing on bean "
                            + beanEvent.getBean() + " with implicit event type"));
                } else if (!beanEvent.getBean().getTypes().contains(beanEvent.getBean().getBeanClass())) {
                    beanEvent.addDefinitionError(
                            new IllegalStateException(beanEvent.getBean().getBeanClass().getSimpleName()
                                    + " type is missing on bean " + beanEvent.getBean() + " with implicit event type"));
                } else {
                    handlerClass.put(abstractHandlerType.get(),
                            beanEvent.getBean().getBeanClass().asSubclass(Handler.class));
                }
            }

        }
    }

    <X extends EventDeserializer<?>> void processEventDeserializerInjections(
            @Observes ProcessInjectionPoint<?, X> event) {
        Type type = event.getInjectionPoint().getType();
        if (type instanceof ParameterizedType
                && EventDeserializer.class.equals(((ParameterizedType) type).getRawType())) {
            requiredEventDeserializers.add((ParameterizedType) type);
        }
    }

    void addMissingEventDeserializers(@Observes @Priority(LIBRARY_AFTER) AfterBeanDiscovery event,
            BeanManager beanManager) {
        for (ParameterizedType type : requiredEventDeserializers) {
            try {
                if (beanManager.resolve(beanManager.getBeans(type)) == null) {
                    Type eventType = type.getActualTypeArguments()[0];
                    Class<?> eventClass = eventType instanceof Class ? (Class<?>) eventType
                            : (Class<?>) ((ParameterizedType) eventType).getRawType();
                    genericSerializationEventTypes.add(eventClass);
                    event.addBean() //
                            .addType(type) //
                            .addType(ExtendedEventDeserializer.class) //
                            .scope(ApplicationScoped.class) //
                            .qualifiers(Default.Literal.INSTANCE) //
                            .produceWith(instance -> createDefaultEventDeserializer(
                                    instance.select(GenericSerialization.class), eventClass));
                }
            } catch (AmbiguousResolutionException e) {
                // ignore, will be reported later
            }
        }
    }

    void verifyGenericSerializationEventTypes(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        List<GenericSerialization> serializations = new ArrayList<>();
        Instance<GenericSerialization> instance = beanManager.createInstance().select(GenericSerialization.class,
                Literal.INSTANCE);
        instance.forEach(serializations::add);
        for (Class<?> eventType : genericSerializationEventTypes) {
            if (serializations.stream().noneMatch(s -> s.accepts(eventType))) {
                event.addDeploymentProblem(
                        new UnsatisfiedResolutionException("No GenericSerialization found for " + eventType));
            }
        }
        serializations.forEach(instance::destroy);
    }

    public static <T> DefaultEventDeserializer<T> createDefaultEventDeserializer(Instance<GenericSerialization> instance,
            Class<T> type) {
        List<GenericSerialization> serializations = new ArrayList<>();
        instance.forEach(serializations::add);
        sort(serializations, comparing(GenericSerialization::priority));
        DefaultEventDeserializer<T> result = null;
        for (GenericSerialization serialization : serializations) {
            if (result == null && serialization.accepts(type)) {
                result = new DefaultEventDeserializer<>(type, serialization);
            } else {
                instance.destroy(serialization);
            }
        }
        if (result == null) {
            throw new UnsatisfiedResolutionException("No GenericSerialization found for " + type);
        }
        return result;
    }

    private Optional<ParameterizedType> getAbstractHandlerType(Set<Type> types) {
        return types.stream() //
                .filter(ParameterizedType.class::isInstance) //
                .map(ParameterizedType.class::cast) //
                .filter(type -> AbstractHandler.class.equals(type.getRawType())) //
                .findAny();
    }

}
