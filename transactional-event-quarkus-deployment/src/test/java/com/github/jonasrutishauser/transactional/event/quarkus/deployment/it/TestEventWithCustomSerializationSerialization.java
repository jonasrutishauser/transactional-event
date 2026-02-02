package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventSerializer;

import jakarta.enterprise.context.Dependent;

@Dependent
public class TestEventWithCustomSerializationSerialization implements EventSerializer<TestEventWithCustomSerialization>, EventDeserializer<TestEventWithCustomSerialization> {

    private static final String SERIALIZATION_PREFIX = "serializationprefix-";
    static final String SERIALIZATION_SUFFIX = "-serializationsuffix";

    @Override
    public TestEventWithCustomSerialization deserialize(String event) {
        if (!event.startsWith(SERIALIZATION_PREFIX)) {
            throw new IllegalStateException("Event not serialized by " + getClass().getName() + ": " + event);
        }
        return new TestEventWithCustomSerialization(event.substring(SERIALIZATION_PREFIX.length()) + SERIALIZATION_SUFFIX);
    }

    @Override
    public String serialize(TestEventWithCustomSerialization event) {
        return SERIALIZATION_PREFIX + event.getMessage();
    }
}
