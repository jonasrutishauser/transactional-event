package com.github.jonasrutishauser.transactional.event.core.serialization;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.github.jonasrutishauser.transactional.event.api.serialization.EventSerializer;
import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;

@ApplicationScoped
public class DefaultEventSerializer implements EventSerializer<Object> {

    private final List<GenericSerialization> serializers;

    DefaultEventSerializer() {
        this(emptyList());
    }

    @Inject
    DefaultEventSerializer(@Any Instance<GenericSerialization> serializers) {
        this((Iterable<GenericSerialization>) serializers);
    }

    private DefaultEventSerializer(Iterable<GenericSerialization> serializers) {
        this.serializers = new ArrayList<>();
        serializers.forEach(this.serializers::add);
        Collections.sort(this.serializers, comparing(GenericSerialization::priority));
    }

    @Override
    public String serialize(Object event) {
        for (GenericSerialization serializer : serializers) {
            if (serializer.accepts(event.getClass())) {
                return serializer.serialize(event);
            }
        }
        throw new IllegalArgumentException("No GenericSerialization found for " + event.getClass());
    }

}
