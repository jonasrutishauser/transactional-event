package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import java.util.Collection;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@Dependent
public class TestResource {

    @Inject
    private EventPublisher publisher;

    @Inject
    private Messages messages;

    @Transactional
    @ActivateRequestContext
    public void publish(String message) {
        publisher.publish(new TestEvent(message));
    }

    public Collection<String> getMessages() {
        return messages.get();
    }

}
