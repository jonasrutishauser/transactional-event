package com.github.jonasrutishauser.transactional.event.core.cdi;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static java.util.Collections.sort;
import static java.util.Comparator.comparing;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;

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
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.invoke.Invoker;

public class EventHandlerExtension implements Extension, EventHandlers {

    private final Collection<EventHandlerMethod<?>> eventHandlerMethods = new ArrayList<>();

    private final Set<ParameterizedType> requiredEventDeserializers = new HashSet<>();
    private final Set<Class<?>> genericSerializationEventTypes = new HashSet<>();

    private final Set<TypedEventHandler> handlerQualifiers = ConcurrentHashMap.newKeySet();

    @Override
    public Annotation getHandlerQualifier(EventTypeResolver typeResolver, String type) {
        for (TypedEventHandler handlerQualifier : handlerQualifiers) {
            if (type.equals(typeResolver.resolve(handlerQualifier.value()))) {
                return handlerQualifier;
            }
        }
        return EventHandlerLiteral.of(type);
    }

    <T extends AbstractHandler<?>> void processTypedHandlers(
            @Observes @Priority(LIBRARY_AFTER) @WithAnnotations(EventHandler.class) ProcessAnnotatedType<T> typeEvent) {
        AnnotatedType<T> type = typeEvent.getAnnotatedType();
        if (type.isAnnotationPresent(EventHandler.class)
                && EventHandler.ABSTRACT_HANDLER_TYPE.equals(type.getAnnotation(EventHandler.class).eventType())) {
            Class<?> eventType = (Class<?>) getAbstractHandlerType(type.getTypeClosure()).orElseThrow()
                    .getActualTypeArguments()[0];
            typeEvent.configureAnnotatedType().add(TypedEventHandler.Literal.of(eventType));
        }
    }

    <T extends Handler> void processHandlers(@Observes @Priority(LIBRARY_AFTER) ProcessBean<T> beanEvent) {
        Optional<EventHandler> annotation = beanEvent.getBean().getQualifiers().stream() //
            .filter(a -> EventHandler.class.equals(a.annotationType())) //
            .map(EventHandler.class::cast) //
            .findAny();
        if (annotation.isEmpty()) {
            beanEvent.addDefinitionError(
                    new IllegalStateException("EventHandler annotation is missing on bean " + beanEvent.getBean()));
        } else if (!beanEvent.getBean().getTypes().contains(Handler.class)) {
            beanEvent.addDefinitionError(
                    new IllegalStateException(Handler.class + " type is missing on bean " + beanEvent.getBean()));
        } else {
            Optional<TypedEventHandler> typedEventHandler = beanEvent.getBean().getQualifiers().stream() //
                    .filter(a -> TypedEventHandler.class.equals(a.annotationType())) //
                    .map(TypedEventHandler.class::cast) //
                    .findAny();
            if (EventHandler.ABSTRACT_HANDLER_TYPE.equals(annotation.get().eventType())) {
                if (typedEventHandler.isEmpty()) {
                    beanEvent.addDefinitionError(new IllegalStateException("AbstractHandler type is missing on bean "
                            + beanEvent.getBean() + " with implicit event type"));
                } else {
                    handlerQualifiers.add(typedEventHandler.get());
                }
            }

        }
    }

    <T> void processHandlerMethods(@Observes @Priority(LIBRARY_AFTER) ProcessManagedBean<T> beanEvent) {
        try {
            beanEvent.getAnnotatedBeanClass().getMethods().stream() //
                    .filter(m -> m.isAnnotationPresent(EventHandler.class)) //
                    .forEach(m -> addHandlerMethod(beanEvent, m));
        } catch (NoSuchMethodError e) {
            beanEvent.addDefinitionError(
                    new IllegalStateException("direct handler methods need a CDI version >= 4.1", e));
        }
    }

    private <T> void addHandlerMethod(ProcessManagedBean<T> beanEvent, AnnotatedMethod<? super T> method) {
        if (method.getParameters().size() != 1) {
            beanEvent.addDefinitionError(
                    new IllegalStateException("EventHandler method " + method + " must have exactly one argument"));
        } else {
            Invoker<T, ?> invoker = beanEvent.createInvoker(method).withInstanceLookup().build();
            eventHandlerMethods.add(new EventHandlerMethod<>(invoker,
                    method.getParameters().get(0).getJavaParameter().getType(), method.getAnnotation(EventHandler.class)));
        }
    }

    <X extends EventDeserializer<?>> void processEventDeserializerInjections(
            @Observes ProcessInjectionPoint<?, X> event) {
        processEventDeserializerInjections(event.getInjectionPoint());
    }

    void processEventDeserializerInjections(InjectionPoint injectionPoint) {
        Type type = injectionPoint.getType();
        if (type instanceof ParameterizedType
                && EventDeserializer.class.equals(((ParameterizedType) type).getRawType())) {
            requiredEventDeserializers.add((ParameterizedType) type);
        }
    }

    void addSyntheticEventHandlers(@Observes @Priority(LIBRARY_BEFORE) AfterBeanDiscovery event,
            BeanManager beanManager) {
        for (EventHandlerMethod<?> eventHandlerMethod : eventHandlerMethods) {
            InjectionPoint injectionPoint = eventHandlerMethod.getEventDeserializerInjectionPoint(beanManager);
            processEventDeserializerInjections(injectionPoint);
            Annotation[] qualifiers = eventHandlerMethod.getEventHandlerQualifiers();
            event.addBean().beanClass(SyntheticHandler.class) //
                    .types(Handler.class) //
                    .qualifiers(qualifiers) //
                    .addInjectionPoint(injectionPoint) //
                    .createWith(ctx -> eventHandlerMethod.createHandler(ctx, beanManager));
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
