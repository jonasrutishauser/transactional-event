package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import java.util.Collection;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@Dependent
public class TestPublisher {

    private final EventPublisher publisher;

    private final Messages messages;

    private final RequestContextProbe requestContextProbe;

    @Inject
    TestPublisher(EventPublisher publisher, Messages messages, RequestContextProbe requestContextProbe) {
        this.publisher = publisher;
        this.messages = messages;
        this.requestContextProbe = requestContextProbe;
    }

    @Transactional
    @ActivateRequestContext
    public void publish(String message) {
        publisher.publish(new TestEvent(message));
    }

    @Transactional
    @ActivateRequestContext
    public void publishCustom(String message) {
        publisher.publish(new TestEventWithCustomSerialization(message));
    }

    @Transactional
    @ActivateRequestContext
    public void publishString(String message) {
        publisher.publish(message);
    }

    @Transactional
    @ActivateRequestContext
    public void publishWithRequestContext(String message) {
        requestContextProbe.mark();
        publisher.publish(new TestEvent(message));
    }

    public Collection<String> getMessages() {
        return messages.get();
    }

}
