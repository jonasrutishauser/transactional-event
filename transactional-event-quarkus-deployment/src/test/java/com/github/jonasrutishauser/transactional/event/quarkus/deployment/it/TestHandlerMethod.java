package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class TestHandlerMethod {

    private final Messages messages;

    @Inject
    TestHandlerMethod(Messages messages) {
        this.messages = messages;
    }

    @EventHandler
    void handle(String event) {
        messages.add(event);
    }

}
