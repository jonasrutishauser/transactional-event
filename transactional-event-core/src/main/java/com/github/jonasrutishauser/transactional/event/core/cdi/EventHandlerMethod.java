package com.github.jonasrutishauser.transactional.event.core.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.invoke.Invoker;

class EventHandlerMethod<T> {
    private final Invoker<T, ?> invoker;
    private final Class<?> eventType;
    private final EventHandler eventHandlerQualifier;
    private InjectionPoint injectionPoint;

    public EventHandlerMethod(Invoker<T, ?> invoker, Class<?> eventType, EventHandler eventHandlerQualifier) {
        this.invoker = invoker;
        this.eventType = eventType;
        this.eventHandlerQualifier = eventHandlerQualifier;
    }

    public Annotation[] getEventHandlerQualifiers() {
        if (EventHandler.ABSTRACT_HANDLER_TYPE.equals(eventHandlerQualifier.eventType())) {
            return new Annotation[] {eventHandlerQualifier, TypedEventHandler.Literal.of(eventType)};
        }
        return new Annotation[] {eventHandlerQualifier};
    }

    public Handler createHandler(CreationalContext<?> ctx, BeanManager beanManager) {
        EventDeserializer<?> deserializer = (EventDeserializer<?>) beanManager.getInjectableReference(getEventDeserializerInjectionPoint(beanManager), ctx);
        return new SyntheticHandler<>(deserializer, invoker);
    }
    
    public InjectionPoint getEventDeserializerInjectionPoint(BeanManager beanManager) {
        if (injectionPoint == null) {
            @SuppressWarnings("rawtypes")
            AnnotatedType<SyntheticHandler> annotatedType = beanManager.createAnnotatedType(SyntheticHandler.class);
            @SuppressWarnings("rawtypes")
            AnnotatedParameter<SyntheticHandler> parameter = annotatedType.getConstructors().iterator().next().getParameters().get(0);
            injectionPoint = new InjectionPoint() {
                @Override
                public boolean isTransient() {
                    return false;
                }
                
                @Override
                public boolean isDelegate() {
                    return false;
                }
                
                @Override
                public Type getType() {
                    return new ParameterizedType() {
                        @Override
                        public Type getRawType() {
                            return EventDeserializer.class;
                        }
                        
                        @Override
                        public Type getOwnerType() {
                            return null;
                        }
                        
                        @Override
                        public Type[] getActualTypeArguments() {
                            return new Type[] {eventType};
                        }

                        @Override
                        public int hashCode() {
                            return Arrays.hashCode(getActualTypeArguments()) ^ EventDeserializer.class.hashCode();
                        }

                        @Override
                        public boolean equals(Object obj) {
                            if (this == obj) {
                                return true;
                            } else if (obj instanceof ParameterizedType) {
                                ParameterizedType that = (ParameterizedType) obj;
                                return that.getOwnerType() == null && EventDeserializer.class.equals(that.getRawType())
                                        && Arrays.equals(getActualTypeArguments(), that.getActualTypeArguments());
                            }
                            return false;
                        }
                    };
                }
                
                @Override
                public Set<Annotation> getQualifiers() {
                    return Collections.singleton(Default.Literal.INSTANCE);
                }
                
                @Override
                public Member getMember() {
                    return parameter.getDeclaringCallable().getJavaMember();
                }
                
                @Override
                public Bean<?> getBean() {
                    return null;
                }
                
                @Override
                public Annotated getAnnotated() {
                    return parameter;
                }

                @Override
                public String toString() {
                    return parameter.toString();
                }
            };
        }
        return injectionPoint;
    }
}