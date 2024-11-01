package com.github.jonasrutishauser.transactional.event.quarkus.deployment;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static java.util.function.Predicate.isEqual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.github.jonasrutishauser.jakarta.enterprise.inject.ExtendedInstance;
import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.cdi.DefaultEventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.cdi.ExtendedEventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.handler.EventHandlers;
import com.github.jonasrutishauser.transactional.event.core.metrics.ConfigurationMetrics;
import com.github.jonasrutishauser.transactional.event.core.store.Dispatcher;
import com.github.jonasrutishauser.transactional.event.quarkus.DefaultEventDeserializerCreator;
import com.github.jonasrutishauser.transactional.event.quarkus.ExtendedInstanceCreator;

import io.quarkus.runtime.Startup;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.ParameterConfig;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.inject.Singleton;

public class TransactionalEventBuildCompatibleExtension implements BuildCompatibleExtension {

    private final Set<ParameterizedType> requiredEventDeserializers = new HashSet<>();
    private final Set<Type> declaredEventDeserializers = new HashSet<>();

    private final Map<ClassInfo, ClassInfo> handlerClass = new HashMap<>();

    private MethodInfo startupMethod;
    private InvokerInfo startup;

    private InvokerInfo extendedInstanceProducer;
    private Map<Type, Collection<AnnotationInfo>> requiredExtendedInstances = new HashMap<>();

    @Enhancement(types = Object.class, withSubtypes = true)
    public void createLockOwnerOnlyOnce(ClassConfig type) {
        if ("com.github.jonasrutishauser.transactional.event.core.store.LockOwner".equals(type.info().name())) {
            type.removeAnnotation(annotation -> ApplicationScoped.class.getName().equals(annotation.name()));
            type.addAnnotation(Singleton.class);
        }
    }

    @Enhancement(types = ConfigurationMetrics.class)
    public void correctStartupOfConfigurationMetrics(ClassConfig type) {
        type.addAnnotation(Startup.class);
    }

    @Enhancement(types = ConfigurationMetrics.class)
    public void correctStartupOfConfigurationMetrics(MethodConfig method) {
        if (!method.parameters().isEmpty()) {
            ParameterConfig firstParameter = method.parameters().get(0);
            if (firstParameter.info().hasAnnotation(Observes.class) && firstParameter.info()
                    .hasAnnotation(annotation -> Initialized.class.getName().equals(annotation.name())
                            && annotation.value().asType().isClass() && ApplicationScoped.class.getName()
                                    .equals(annotation.value().asType().asClass().declaration().name()))) {
                firstParameter.removeAllAnnotations();
            }
        }
    }

    @Enhancement(types = Dispatcher.class, withSubtypes = true)
    public void disableStaticInitStartup(MethodConfig method) {
        if (!method.parameters().isEmpty()) {
            ParameterConfig firstParameter = method.parameters().get(0);
            if (firstParameter.info().hasAnnotation(Observes.class) && firstParameter.info()
                    .hasAnnotation(annotation -> Initialized.class.getName().equals(annotation.name())
                            && annotation.value().asType().isClass() && ApplicationScoped.class.getName()
                                    .equals(annotation.value().asType().asClass().declaration().name()))) {
                firstParameter.removeAllAnnotations();
                startupMethod = method.info();
            }
        }
    }

    @Registration(types = Dispatcher.class)
    public void getStartupBean(BeanInfo beanInfo, InvokerFactory invokerFactory) {
        if (startupMethod != null && beanInfo.declaringClass().methods().contains(startupMethod)) {
            startup = invokerFactory.createInvoker(beanInfo, startupMethod).withInstanceLookup().build();
        }
    }

    @Registration(types = Handler.class)
    public void processHandlers(BeanInfo beanInfo, Messages messages) {
        Optional<AnnotationInfo> eventHandlerAnnotation = beanInfo.qualifiers().stream()
                .filter(annotation -> EventHandler.class.getName().equals(annotation.name())).findAny();
        if (eventHandlerAnnotation.isEmpty()) {
            messages.error("EventHandler annotation is missing on bean", beanInfo);
        } else {
            if (EventHandler.ABSTRACT_HANDLER_TYPE.equals(eventHandlerAnnotation.get().member("eventType").asString())) {
                Optional<ParameterizedType> abstractHandlerType = getAbstractHandlerType(beanInfo.types());
                if (abstractHandlerType.isEmpty()) {
                    messages.error("AbstractHandler type is missing on bean with implicit event type", beanInfo);
                } else if (beanInfo.types().stream().filter(Type::isClass).map(Type::asClass).map(ClassType::declaration)
                        .noneMatch(isEqual(beanInfo.declaringClass()))) {
                    messages.error(beanInfo.declaringClass().simpleName() + " type is missing on bean with implicit event type", beanInfo);
                } else {
                    handlerClass.put(getClassInfo(abstractHandlerType.get().typeArguments().get(0)), beanInfo.declaringClass());
                }
            }
        }
    }

    @Synthesis
    public void addEventHandlersBean(SyntheticComponents components) throws ClassNotFoundException {
        List<ClassInfo> types = new ArrayList<>();
        List<ClassInfo> beans = new ArrayList<>();
        handlerClass.forEach((type, bean) -> {
            types.add(type);
            beans.add(bean);
        });
        Class<?> quarkusEventHandlers = Class.forName("com.github.jonasrutishauser.transactional.event.quarkus.handler.QuarkusEventHandlers");
        addCreator(components.addBean(quarkusEventHandlers) //
            .type(EventHandlers.class) //
            .type(quarkusEventHandlers) //
                .scope(Singleton.class) //
                .withParam("types", types.toArray(ClassInfo[]::new)) //
                .withParam("beans", beans.toArray(ClassInfo[]::new)) //
                .withParam("startup", startup), quarkusEventHandlers);
    }

    @SuppressWarnings("unchecked")
    private <T> void addCreator(SyntheticBeanBuilder<T> builder, Class<?> creatorHost) {
        builder.createWith(
                Arrays.stream(creatorHost.getNestMembers()).filter(SyntheticBeanCreator.class::isAssignableFrom)
                        .map(c -> (Class<? extends SyntheticBeanCreator<T>>) c).findAny()
                        .orElseThrow(IllegalStateException::new));
    }

    @Registration(types = Object.class)
    public void getExtendedInstanceProducer(BeanInfo beanInfo, Types types, InvokerFactory invokerFactory) {
        if (beanInfo.isClassBean()) {
            Optional<MethodInfo> producer = beanInfo.declaringClass().methods().stream()
                    .filter(method -> method.returnType().isParameterizedType()
                            && types.of(ExtendedInstance.class)
                                    .equals(method.returnType().asParameterizedType().genericClass())
                            && method.parameters().size() == 3
                            && types.of(BeanManager.class).equals(method.parameters().get(0).type())
                            && method.parameters().get(2).type().isParameterizedType()
                            && types.of(Instance.class)
                                    .equals(method.parameters().get(2).type().asParameterizedType().genericClass()))
                    .findAny();
            producer.ifPresent(
                    method -> extendedInstanceProducer = invokerFactory.createInvoker(beanInfo, method).build());
        }
        for (InjectionPointInfo injectionPoint : beanInfo.injectionPoints()) {
            Type type = injectionPoint.type();
            if ((type.isClass() || type.isParameterizedType())
                    && ExtendedInstance.class.getName().equals(getClassInfo(type).name())) {
                Type simplifiedType = types.of(Object.class);
                if (type.isParameterizedType()) {
                    Type typeArgument = type.asParameterizedType().typeArguments().get(0);
                    if (!typeArgument.isTypeVariable() && !typeArgument.isWildcardType()) {
                        simplifiedType = typeArgument;
                    }
                }
                requiredExtendedInstances.computeIfAbsent(simplifiedType, key -> new HashSet<>())
                        .addAll(injectionPoint.qualifiers());
            }
        }
    }

    @Synthesis
    public void registerExtendedInstanceBeans(SyntheticComponents components, Types types) {
        if (extendedInstanceProducer != null) {
            for (Entry<Type, Collection<AnnotationInfo>> extendedInstance : requiredExtendedInstances.entrySet()) {
                @SuppressWarnings("rawtypes")
                SyntheticBeanBuilder<ExtendedInstance> builder = components.addBean(ExtendedInstance.class)
                        .type(types.parameterized(ExtendedInstance.class, extendedInstance.getKey())) //
                        .alternative(true) //
                        .priority(LIBRARY_AFTER);
                extendedInstance.getValue().forEach(builder::qualifier);
                builder.createWith(ExtendedInstanceCreator.class) //
                        .withParam(ExtendedInstanceCreator.PRODUCER, extendedInstanceProducer)
                        .withParam(ExtendedInstanceCreator.TYPE, getClassInfo(extendedInstance.getKey()));
            }
        }
    }

    @Registration(types = Object.class)
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

    private ClassInfo getClassInfo(Type type) {
        if (type.isParameterizedType()) {
            return type.asParameterizedType().declaration();
        }
        return type.asClass().declaration();
    }

    private Optional<ParameterizedType> getAbstractHandlerType(Collection<Type> types) {
        return types.stream() //
                .filter(Type::isParameterizedType) //
                .map(Type::asParameterizedType) //
                .filter(type -> AbstractHandler.class.getName().equals(type.declaration().name())) //
                .findAny();
    }

}
