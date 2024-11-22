package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

@Dependent
@EventHandler
@Typed(Handler.class)
public class TestEventHandler extends AbstractHandler<TestEvent> {

    private Messages messages;

    @Inject
    TestEventHandler(Messages messages) {
        this.messages = messages;
    }

    @Override
    protected void handle(TestEvent event) {
        if (event.getMessage().contains("failure") && messages.addFailure(event.getMessage())) {
            throw new IllegalStateException(event.getMessage());
        }
        if (event.getMessage().contains("blocker")) {
            throw new IllegalArgumentException("blocker not allowed");
        }
        messages.add(event.getMessage());
    }

}
