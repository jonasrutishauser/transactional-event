package com.github.jonasrutishauser.transactional.event.liberty;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.Collection;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;

@Path("/test")
@RequestScoped
public class TestResource {

    private final EventPublisher publisher;

    private final Messages messages;

    public TestResource() {
        this(null, null);
    }

    @Inject
    public TestResource(EventPublisher publisher, Messages messages) {
        this.publisher = publisher;
        this.messages = messages;
    }

    @POST
    @Transactional
    public void publish(String message) {
        publisher.publish(new TestEvent(message));
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Collection<String> getMessages() {
        return messages.get();
    }

}
