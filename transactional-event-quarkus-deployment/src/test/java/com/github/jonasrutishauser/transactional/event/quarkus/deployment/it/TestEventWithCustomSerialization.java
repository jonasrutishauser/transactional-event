package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

public class TestEventWithCustomSerialization {

    private final String message;

    public TestEventWithCustomSerialization(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
