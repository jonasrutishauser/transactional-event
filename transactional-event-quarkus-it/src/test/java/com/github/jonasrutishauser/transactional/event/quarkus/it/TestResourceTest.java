package com.github.jonasrutishauser.transactional.event.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class TestResourceTest {
    @Test
    void testRoundTrip() {
        given().when().body("test message").post("/test").then().statusCode(Response.Status.NO_CONTENT.getStatusCode());


        await().until(processMessages(), containsString("test message"));
    }

    @Test
    void testFailure() {
        postMessage("test failure");
        for (int i = 10; i < 50; i++) {
            postMessage("failure " + i);
        }

        await().atMost(1, MINUTES).until(processMessages(), containsString("test failure"));
        for (int i = 10; i < 50; i++) {
            await().until(processMessages(), containsString("failure " + i));
        }
    }

    private Callable<String> processMessages() {
        return () -> given().when().get("/test").then()
                .log().all()
                .statusCode(Response.Status.OK.getStatusCode()).extract().body().asString();
    }

    private void postMessage(String message) {
        given().when().body(message).post("/test").then().statusCode(Response.Status.NO_CONTENT.getStatusCode());
    }
}