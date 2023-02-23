package com.github.jonasrutishauser.transactional.event.liberty;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

public class TestEvent {

    private final String message;
    
    @JsonbCreator
    public TestEvent(@JsonbProperty("message") String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }

}
