package com.github.jonasrutishauser.transactional.event.quarkus.handler;

import java.lang.reflect.ParameterizedType;
import java.util.function.Predicate;

import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.cdi.SyntheticHandler;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Instance.Handle;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.invoke.Invoker;
import jakarta.enterprise.util.TypeLiteral;

public class SyntheticHandlerCreator implements SyntheticBeanCreator<Handler> {

    private static final TypeLiteral<EventDeserializer<?>> EVENT_DESIRIALIZERS = new TypeLiteral<EventDeserializer<?>>() {
        private static final long serialVersionUID = 1L;
    };

    @Override
    public Handler create(Instance<Object> lookup, Parameters params) {
        EventDeserializer<?> deserializer = lookup.select(EVENT_DESIRIALIZERS) //
                .handlesStream() //
                .filter(matchesType(params.get("eventType", Class.class))) //
                .findAny() //
                .orElseThrow() //
                .get();
        Invoker<?, ?> invoker = params.get("invoker", Invoker.class);
        return new SyntheticHandler<>(deserializer, invoker);
    }

    private Predicate<Handle<?>> matchesType(Class<?> eventType) {
        return h -> eventType.equals(h.getBean().getTypes().stream() //
                .filter(ParameterizedType.class::isInstance) //
                .map(ParameterizedType.class::cast) //
                .filter(t -> EventDeserializer.class.equals(t.getRawType())) //
                .findAny() //
                .orElseThrow() //
                .getActualTypeArguments()[0]);
    }

}
