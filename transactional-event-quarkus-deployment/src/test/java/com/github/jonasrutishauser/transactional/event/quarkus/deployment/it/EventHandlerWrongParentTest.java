package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.Dependent;

class EventHandlerWrongParentTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest() //
            .assertException(t -> assertThat(t.getMessage(),
                    stringContainsInOrder("AbstractHandler type is missing on bean with implicit event type",
                            "InvalidEventHandler"))) //
            .withApplicationRoot(archive -> archive //
                    .addClasses(ValidEventHandler.class, InvalidEventHandler.class) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            );

    @Test
    void test() {
        fail("deployment error expected");
    }

    @Dependent
    @EventHandler
    public static class InvalidEventHandler implements Handler {
        @Override
        public void handle(String event) {
            fail();
        }
    }

    @Dependent
    @EventHandler(eventType = "TestEvent")
    public static class ValidEventHandler implements Handler {
        @Override
        public void handle(String event) {
            fail();
        }
    }
}
