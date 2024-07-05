package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;

import org.awaitility.core.ThrowingRunnable;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

public class TransactionalEventChangeIT {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest() //
            .withApplicationRoot(archive -> archive //
                    .addClasses(Messages.class, TestEvent.class, TestEventHandler.class, TestResource.class) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            );

    @Test
    void testRoundTrip() {
        postMessage("test message");
        await().untilAsserted(processedMessagesContains("test message"));

        test.modifySourceFile(TestEventHandler.class, s -> s.replace("Dependent", "ApplicationScoped"));

        await().untilAsserted(() -> postMessage("modified message"));
        await().untilAsserted(processedMessagesContains("modified message"));
    }

    private ThrowingRunnable processedMessagesContains(String content) {
        return () -> RestAssured.when() //
                .get("/test") //
                .then() //
                .assertThat() //
                .body(containsString(content));
    }

    private void postMessage(String message) {
        RestAssured.given() //
                .body(message) //
                .when() //
                .post("/test/publish") //
                .then() //
                .assertThat() //
                .statusCode(204);
    }
}
