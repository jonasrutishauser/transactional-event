package com.github.jonasrutishauser.transactional.event.core.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;

import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;

@Dependent
@Priority(1000)
public class SerializableSerialization implements GenericSerialization {

    @Override
    public boolean accepts(Class<?> type) {
        return Serializable.class.isAssignableFrom(type);
    }

    @Override
    public String serialize(Object event) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(Base64.getEncoder().wrap(bout))) {
            out.writeObject(event);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new String(bout.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(String event, Class<T> type) {
        try (ObjectInputStream in = new ObjectInputStream(
                Base64.getDecoder().wrap(new ByteArrayInputStream(event.getBytes(StandardCharsets.UTF_8))))) {
            return type.cast(in.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

}
