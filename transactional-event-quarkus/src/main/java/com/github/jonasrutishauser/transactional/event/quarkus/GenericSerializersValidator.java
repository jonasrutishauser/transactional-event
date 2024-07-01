package com.github.jonasrutishauser.transactional.event.quarkus;

import java.util.List;

import com.github.jonasrutishauser.transactional.event.core.cdi.ExtendedEventDeserializer;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class GenericSerializersValidator {

    @SuppressWarnings("rawtypes")
    void validate(@Observes @Initialized(ApplicationScoped.class) Object event, @All List<ExtendedEventDeserializer> deserializers) {
        // nothing to do here, eager injection will initialize the beans (and fail if no generic implementation is available)
    }

}
