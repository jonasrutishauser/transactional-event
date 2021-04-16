package com.github.jonasrutishauser.transactional.event.liberty;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;

@Dependent
@EventHandler
public class TestEventHandler extends AbstractHandler<TestEvent> {

    @Inject
    private Messages messages;

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
