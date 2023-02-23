package com.github.jonasrutishauser.transactional.event.core.serialization;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;

@Priority(2000)
@ApplicationScoped
public class JsonbSerialization implements GenericSerialization {

    private final Jsonb jsonb;
    
    @Inject
    JsonbSerialization() {
        this(new JsonbConfig());
    }
    
    protected JsonbSerialization(JsonbConfig config) {
        jsonb = JsonbBuilder.create(config);
    }

    @Override
    public boolean accepts(Class<?> type) {
        return true;
    }

    @Override
    public String serialize(Object event) {
        return jsonb.toJson(event);
    }

    @Override
    public <T> T deserialize(String event, Class<T> type) {
        return jsonb.fromJson(event, type);
    }

}
