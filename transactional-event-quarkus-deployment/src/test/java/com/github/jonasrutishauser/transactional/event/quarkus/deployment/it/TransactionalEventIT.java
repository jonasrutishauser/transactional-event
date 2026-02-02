package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.awaitility.Awaitility.await;

import java.util.concurrent.Callable;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

import jakarta.inject.Inject;

class TransactionalEventIT {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest() //
            .setFlatClassPath(true) // needed for invoker
            .withApplicationRoot(archive -> archive //
                    .addClasses(Messages.class, TestEvent.class, TestEventHandler.class, TestPublisher.class, TestHandlerMethod.class, //
                            TestEventWithCustomSerialization.class, TestEventWithCustomSerializationHandler.class, TestEventWithCustomSerializationSerialization.class) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            ).overrideRuntimeConfigKey("quarkus.transactional.event.initial-dispatch-interval", "1");

    @Inject
    TestPublisher publisher;

    @Test
    void testRoundTrip() {
        publisher.publish("test message");

        await().until(processedMessagesContains("test message"));
    }

    @Test
    void testCustomSerializer() {
        publisher.publishCustom("custom serialized message");

        await().until(processedMessagesContains("custom serialized message" + TestEventWithCustomSerializationSerialization.SERIALIZATION_SUFFIX));
    }

    @Test
    void testDirectMethodRoundTrip() {
        publisher.publishString("some message");

        await().until(processedMessagesContains("some message"));
    }

    @Test
    void testFailure() {
        publisher.publish("test failure");
        for (int i = 10; i < 50; i++) {
            publisher.publish("failure " + i);
        }

        await().pollInterval(10, MILLISECONDS).until(processedMessagesContains("test failure"));
        for (int i = 10; i < 50; i++) {
            await().pollInterval(10, MILLISECONDS).until(processedMessagesContains("failure " + i));
        }
    }

    private Callable<Boolean> processedMessagesContains(String content) {
        return () -> publisher.getMessages().contains(content);
    }
}
