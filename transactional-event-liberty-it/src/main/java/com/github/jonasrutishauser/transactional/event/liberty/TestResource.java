package com.github.jonasrutishauser.transactional.event.liberty;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.Collection;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;

@Path("/test")
@RequestScoped
public class TestResource {

    @Inject
    private EventPublisher publisher;

    @Inject
    private Messages messages;

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
