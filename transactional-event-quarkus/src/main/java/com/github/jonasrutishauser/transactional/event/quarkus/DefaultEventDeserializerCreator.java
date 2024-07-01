package com.github.jonasrutishauser.transactional.event.quarkus;

import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;
import com.github.jonasrutishauser.transactional.event.core.cdi.DefaultEventDeserializer;
import com.github.jonasrutishauser.transactional.event.core.cdi.EventHandlerExtension;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

@SuppressWarnings("rawtypes")
public class DefaultEventDeserializerCreator implements SyntheticBeanCreator<DefaultEventDeserializer> {

    public static final String TYPE = "type";

    @Override
    public DefaultEventDeserializer create(Instance<Object> lookup, Parameters params) {
        Instance<GenericSerialization> instance = lookup.select(GenericSerialization.class);
        Class<?> type = params.get(TYPE, Class.class);

        return EventHandlerExtension.createDefaultEventDeserializer(instance, type);
    }

}
