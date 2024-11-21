package com.github.jonasrutishauser.transactional.event.quarkus.deployment;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.cdi.DefaultEventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.cdi.ExtendedEventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.cdi.TypedEventHandler;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;
import com.github.jonasrutishauser.transactional.event.quarkus.DefaultEventDeserializerCreator;
import com.github.jonasrutishauser.transactional.event.quarkus.handler.SyntheticHandlerCreator;

import io.quarkus.runtime.Startup;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.ParameterConfig;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.inject.Singleton;

public class TransactionalEventBuildCompatibleExtension implements BuildCompatibleExtension {

    private static final String EVENT_TYPE = "eventType";

    private final Map<MethodInfo, InvokerInfo> eventHandlerMethods = new HashMap<>();

    private final Set<ParameterizedType> requiredEventDeserializers = new HashSet<>();
    private final Set<Type> declaredEventDeserializers = new HashSet<>();

    private final List<ClassInfo> handledTypes = new ArrayList<>();

    @Enhancement(types = Object.class, withSubtypes = true)
    @Priority(LIBRARY_AFTER)
    public void createLockOwnerOnlyOnce(ClassConfig type) {
        if ("com.github.jonasrutishauser.transactional.event.core.store.LockOwner".equals(type.info().name())) {
            type.removeAnnotation(annotation -> ApplicationScoped.class.getName().equals(annotation.name()));
            type.addAnnotation(Singleton.class);
        }
    }

    @Enhancement(types = com.github.jonasrutishauser.transactional.event.core.cdi.Startup.class, withSubtypes = true)
    @Priority(LIBRARY_AFTER)
    public void fixStaticInitStartup(ClassConfig type) {
        if (!com.github.jonasrutishauser.transactional.event.core.cdi.Startup.class.getName().equals(type.info().name())) {
            changeInitializedApplicationScopedObserverToStartup(type);
        }
    }

    private void changeInitializedApplicationScopedObserverToStartup(ClassConfig type) {
        type.addAnnotation(Startup.class);
        type.methods().forEach(method -> {
            if (!method.parameters().isEmpty()) {
                ParameterConfig firstParameter = method.parameters().get(0);
                if (firstParameter.info().hasAnnotation(Observes.class) && firstParameter.info()
                        .hasAnnotation(annotation -> Initialized.class.getName().equals(annotation.name())
                                && annotation.value().asType().isClass() && ApplicationScoped.class.getName()
                                        .equals(annotation.value().asType().asClass().declaration().name()))) {
                    firstParameter.removeAllAnnotations();
                }
            }
        });
    }

    @Enhancement(types = AbstractHandler.class, withSubtypes = true, withAnnotations = EventHandler.class)
    @Priority(LIBRARY_AFTER)
    public void processTypedHandlers(ClassConfig typeConfig, Types types, Messages messages) {
        ClassInfo type = typeConfig.info();
        if (type.hasAnnotation(EventHandler.class) && EventHandler.ABSTRACT_HANDLER_TYPE
                .equals(type.annotation(EventHandler.class).member(EVENT_TYPE).asString())) {
            Optional<ClassType> eventType = type.methods().stream() //
                    .filter(m -> "handle".equals(m.name())) //
                    .filter(m -> types.ofVoid().equals(m.returnType())) //
                    .filter(m -> !m.isAbstract()) //
                    .filter(m -> !m.isStatic()) //
                    .filter(m -> m.parameters().size() == 1) //
                    .map(MethodInfo::parameters) //
                    .map(params -> params.get(0)) //
                    .map(ParameterInfo::type) //
                    .filter(Type::isClass) //
                    .map(Type::asClass) //
                    .findAny();
            if (eventType.isEmpty()) {
                messages.warn("Failed to determine event type", type);
            } else {
                typeConfig.addAnnotation(AnnotationBuilder.of(TypedEventHandler.class).value(eventType.get()).build());
            }
        }
    }

    @Registration(types = Handler.class)
    @Priority(LIBRARY_AFTER)
    public void processHandlers(BeanInfo beanInfo, Messages messages) {
        Optional<AnnotationInfo> eventHandlerAnnotation = beanInfo.qualifiers().stream()
                .filter(annotation -> EventHandler.class.getName().equals(annotation.name())).findAny();
        if (eventHandlerAnnotation.isEmpty()) {
            messages.error("EventHandler annotation is missing on bean", beanInfo);
        } else {
            if (EventHandler.ABSTRACT_HANDLER_TYPE
                    .equals(eventHandlerAnnotation.get().member(EVENT_TYPE).asString())) {
                Optional<AnnotationInfo> typedEventHandler = beanInfo.qualifiers().stream() //
                        .filter(a -> TypedEventHandler.class.getName().equals(a.name())) //
                        .findAny();
                if (typedEventHandler.isEmpty()) {
                    messages.error("AbstractHandler type is missing on bean with implicit event type", beanInfo);
                } else {
                    handledTypes.add(typedEventHandler.get().value().asType().asClass().declaration());
                }
            }
        }
    }

    @Registration(types = Object.class)
    @Priority(LIBRARY_AFTER)
    public void processHandlerMethods(BeanInfo beanInfo, InvokerFactory invokerFactory, Messages messages) {
        if (beanInfo.isClassBean()) {
            beanInfo.declaringClass().methods().stream() //
                .filter(m -> m.hasAnnotation(EventHandler.class)) //
                .forEach(m -> addHandlerMethod(beanInfo, m, invokerFactory, messages));
        }
    }

    private void addHandlerMethod(BeanInfo beanInfo, MethodInfo method, InvokerFactory invokerFactory, Messages messages) {
        if (method.parameters().size() != 1) {
            messages.error("EventHandler method must have exactly one argument", method);
        } else {
            InvokerInfo invoker = invokerFactory.createInvoker(beanInfo, method).withInstanceLookup().build();
            eventHandlerMethods.put(method, invoker);
        }
    }

    @Synthesis
    @Priority(LIBRARY_BEFORE)
    public void addSyntheticEventHandlers(SyntheticComponents components, Types types) {
        for (Entry<MethodInfo, InvokerInfo> eventHandlerMethod : eventHandlerMethods.entrySet()) {
            ClassType eventType = eventHandlerMethod.getKey().parameters().get(0).type().asClass();
            components.addBean(Handler.class) //
                    .type(Handler.class) //
                    .qualifier(eventHandlerMethod.getKey().annotation(EventHandler.class)) //
                    .qualifier(AnnotationBuilder.of(TypedEventHandler.class).value(eventType.declaration()).build()) //
                    .createWith(SyntheticHandlerCreator.class) //
                    .withParam("type", eventType.declaration()) //
                    .withParam("invoker", eventHandlerMethod.getValue());
            if (EventHandler.ABSTRACT_HANDLER_TYPE.equals(
                    eventHandlerMethod.getKey().annotation(EventHandler.class).member(EVENT_TYPE).asString())) {
                handledTypes.add(eventType.declaration());
            }
            requiredEventDeserializers.add(types.parameterized(EventDeserializer.class, eventType));
        }
    }

    @Synthesis
    @Priority(LIBRARY_AFTER)
    public void addEventHandlersBean(SyntheticComponents components) throws ClassNotFoundException {
        Class<?> quarkusEventHandlers = Class
                .forName("com.github.jonasrutishauser.transactional.event.quarkus.handler.QuarkusEventHandlers");
        addCreator( //
                components.addBean(quarkusEventHandlers) //
                        .type(EventHandlers.class) //
                        .type(quarkusEventHandlers) //
                        .scope(Singleton.class) //
                        .withParam("types", handledTypes.toArray(ClassInfo[]::new)), //
                quarkusEventHandlers);
    }

    @SuppressWarnings("unchecked")
    private <T> void addCreator(SyntheticBeanBuilder<T> builder, Class<?> creatorHost) {
        builder.createWith(
                Arrays.stream(creatorHost.getNestMembers()).filter(SyntheticBeanCreator.class::isAssignableFrom)
                        .map(c -> (Class<? extends SyntheticBeanCreator<T>>) c).findAny()
                        .orElseThrow(IllegalStateException::new));
    }

    @Registration(types = Object.class)
    @Priority(LIBRARY_BEFORE)
    public void processEventDeserializerInjections(BeanInfo beanInfo, Types types, Messages messages) {
        for (InjectionPointInfo injectionPoint : beanInfo.injectionPoints()) {
            Type type = injectionPoint.type();
            if (type.isParameterizedType()
                    && EventDeserializer.class.getName().equals(type.asParameterizedType().declaration().name())) {
                requiredEventDeserializers.add(type.asParameterizedType());
            }
        }
    }

    @Registration(types = EventDeserializer.class)
    @Priority(LIBRARY_BEFORE)
    public void processEventDeserializers(BeanInfo beanInfo, Messages messages) {
        declaredEventDeserializers.addAll(beanInfo.types());
    }

    @Synthesis
    @Priority(LIBRARY_AFTER)
    public void addMissingEventDeserializers(SyntheticComponents components) {
        requiredEventDeserializers.removeAll(declaredEventDeserializers);
        for (ParameterizedType type : requiredEventDeserializers) {
            Type eventType = type.typeArguments().get(0);
            ClassType eventClass = eventType.isClass() ? eventType.asClass()
                    : eventType.asParameterizedType().genericClass();
            components.addBean(DefaultEventDeserializer.class) //
                .type(type) //
                .type(ExtendedEventDeserializer.class) //
                .scope(Singleton.class) //
                .qualifier(Default.Literal.INSTANCE) //
                .createWith(DefaultEventDeserializerCreator.class) //
                .withParam(DefaultEventDeserializerCreator.TYPE, eventClass.declaration());
        }
    }

}
