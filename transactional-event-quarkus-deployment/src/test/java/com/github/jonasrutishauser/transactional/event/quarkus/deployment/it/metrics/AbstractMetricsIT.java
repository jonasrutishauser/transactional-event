package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.metrics;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.Test;

import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.Messages;
import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.TestEvent;
import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.TestEventHandler;
import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.TestEventWithCustomSerialization;
import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.TestEventWithCustomSerializationHandler;
import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.TestEventWithCustomSerializationSerialization;
import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.TestHandlerMethod;
import com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.TestPublisher;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;

abstract class AbstractMetricsIT {

    static QuarkusUnitTest config() {
        return new QuarkusUnitTest() //
                .setFlatClassPath(true) // needed for invoker
                .withApplicationRoot(archive -> archive //
                        .addClasses(Messages.class, TestEvent.class, TestEventHandler.class, TestPublisher.class,
                                TestHandlerMethod.class, //
                                TestEventWithCustomSerialization.class, TestEventWithCustomSerializationHandler.class,
                                TestEventWithCustomSerializationSerialization.class) //
                        .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
                ).overrideRuntimeConfigKey("quarkus.transactional.event.initial-dispatch-interval", "1");
    }

    @Inject
    TestPublisher publisher;

    @Test
    void testMetrics() {
        publisher.publish("test failure");

        await().pollInterval(10, MILLISECONDS).until(() -> publisher.getMessages().contains("test failure"));

        RestAssured.when().get("/q/metrics").then().statusCode(200).body(
                containsString("com_github_jonasrutishauser_transaction_event_max_concurrent_dispatching 10.0"),
                containsString("com_github_jonasrutishauser_transaction_event_failedattempts_total 1"),
                containsString("com_github_jonasrutishauser_transaction_event_dispatched_processing 0"),
                containsString("com_github_jonasrutishauser_transaction_event_processing_current 0"));
    }

}
