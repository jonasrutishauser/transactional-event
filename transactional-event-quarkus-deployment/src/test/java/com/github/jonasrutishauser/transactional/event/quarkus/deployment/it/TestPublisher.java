package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import java.util.Collection;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@Dependent
public class TestPublisher {

    @Inject
    private EventPublisher publisher;

    @Inject
    private Messages messages;

    @Transactional
    @ActivateRequestContext
    public void publish(String message) {
        publisher.publish(new TestEvent(message));
    }

    @Transactional
    @ActivateRequestContext
    public void publishString(String message) {
        publisher.publish(message);
    }

    public Collection<String> getMessages() {
        return messages.get();
    }

    @EventHandler
    void handle(String event) {
        messages.add(event);
    }

}
