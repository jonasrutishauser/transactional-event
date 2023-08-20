package com.github.jonasrutishauser.transactional.event.liberty;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.jonasrutishauser.cdi.test.core.junit.CdiTestJunitExtension;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Inject;

@ExtendWith(CdiTestJunitExtension.class)
class TestResourceTest {

    @Inject
    private TestResource testResource;

    @Inject
    private RequestContextController requestContextController;

    @Test
    void testRoundTrip() {
        testResource.publish("test message");

        await().until(this::getMessages, contains("test message"));
    }

    @Test
    void testFailure() {
        testResource.publish("test failure");
        for (int i = 10; i < 50; i++) {
            testResource.publish("failure " + i);
        }

        await().atMost(1, MINUTES).until(this::getMessages, hasItem("test failure"));
        for (int i = 10; i < 50; i++) {
            await().until(this::getMessages, hasItem("failure " + i));
        }
    }

    private Collection<String> getMessages() {
        requestContextController.activate();
        try {
            return testResource.getMessages();
        } finally {
            requestContextController.deactivate();
        }
    }

}
