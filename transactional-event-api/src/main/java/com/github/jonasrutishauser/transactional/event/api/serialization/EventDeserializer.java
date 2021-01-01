package com.github.jonasrutishauser.transactional.event.api.serialization;

public interface EventDeserializer<T> {

    T deserialize(String event); 

}
