package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@EventHandler
public class TestEventWithCustomSerializationHandler extends AbstractHandler<TestEventWithCustomSerialization> {

    private final Messages customMessages;

    @Inject
    TestEventWithCustomSerializationHandler(Messages customMessages) {
        this.customMessages = customMessages;
    }

    @Override
    protected void handle(TestEventWithCustomSerialization event) {
        customMessages.add(event.getMessage());

    }
}
