package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import java.util.Collection;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/test")
@Dependent
public class TestResource {

    @Inject
    private EventPublisher publisher;

    @Inject
    private Messages messages;

    @POST
    @Path("/publish")
    @Transactional
    public void publish(String message) {
        publisher.publish(new TestEvent(message));
    }

    @GET
    public Collection<String> getMessages() {
        return messages.get();
    }

}
