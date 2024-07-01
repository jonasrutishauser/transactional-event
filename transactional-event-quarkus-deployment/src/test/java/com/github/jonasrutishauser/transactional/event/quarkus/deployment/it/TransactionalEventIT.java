package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.Callable;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;

class TransactionalEventIT {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest() //
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class) //
                    .addPackages(false, path -> true, TestResource.class.getPackage()) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            );

    @Inject
    TestResource testResource;

    @Test
    void testRoundTrip() {
        testResource.publish("test message");

        await().until(processedMessagesContains("test message"));
    }

    @Test
    void testFailure() {
        testResource.publish("test failure");
        for (int i = 10; i < 50; i++) {
            testResource.publish("failure " + i);
        }

        await().atMost(1, MINUTES).until(processedMessagesContains("test failure"));
        for (int i = 10; i < 50; i++) {
            await().until(processedMessagesContains("failure " + i));
        }
    }

    private Callable<Boolean> processedMessagesContains(String content) {
        return () -> testResource.getMessages().contains(content);
    }
}
