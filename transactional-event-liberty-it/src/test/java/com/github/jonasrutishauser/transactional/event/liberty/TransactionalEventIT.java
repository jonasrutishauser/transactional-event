package com.github.jonasrutishauser.transactional.event.liberty;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.Callable;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response.Status;

import org.junit.jupiter.api.Test;

class TransactionalEventIT {

    @Test
    void testRoundTrip() {
        WebTarget target = ClientBuilder.newClient()
                .target("http://localhost:9080/transactional-event-liberty-it/rest");

        postMessage(target, "test message");

        await().until(processMessages(target), containsString("test message"));
    }

    @Test
    void testFailure() {
        WebTarget target = ClientBuilder.newClient()
                .target("http://localhost:9080/transactional-event-liberty-it/rest");

        postMessage(target, "test failure");
        for (int i = 10; i < 50; i++) {
            postMessage(target, "failure " + i);
        }

        await().atMost(1, MINUTES).until(processMessages(target), containsString("test failure"));
        for (int i = 10; i < 50; i++) {
            await().until(processMessages(target), containsString("failure " + i));
        }
    }

    private Callable<String> processMessages(WebTarget target) {
        return () -> target.path("test").request().get(String.class);
    }

    private void postMessage(WebTarget target, String message) {
        int status = target.path("test").request().post(Entity.text(message)).getStatus();

        assertEquals(Status.NO_CONTENT.getStatusCode(), status);
    }

}
