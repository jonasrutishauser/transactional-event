package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CountDownLatch;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

class RequestContextIsolationIT {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest() //
            .setFlatClassPath(true) // needed for invoker
            .withApplicationRoot(archive -> archive //
                    .addClasses(RequestContextProbe.class, Rendezvous.class, PublishingService.class,
                            PublishingResource.class, RendezvousHandler.class) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            );

    @Inject
    Rendezvous rendezvous;

    @Test
    void testPublishingRequestKeepsItsRequestContext() {
        RestAssured.when() //
                .post("/request-context") //
                .then() //
                .assertThat() //
                .statusCode(200) //
                .body(is("request context kept"));

        assertFalse(rendezvous.isRequestContextSeenByHandler());
    }

    @Path("/request-context")
    @Dependent
    public static class PublishingResource {

        @Inject
        private RequestContextProbe requestContextProbe;

        @Inject
        private PublishingService service;

        @Inject
        private Rendezvous rendezvous;

        @POST
        @Produces(MediaType.TEXT_PLAIN)
        public String publish() {
            requestContextProbe.mark();
            service.publish();
            try {
                rendezvous.awaitHandlerStarted();
                return requestContextProbe.isMarked() ? "request context kept" : "request context lost";
            } finally {
                rendezvous.requestChecked();
            }
        }

    }

    @Dependent
    public static class PublishingService {

        private final EventPublisher publisher;

        @Inject
        PublishingService(EventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publish() {
            publisher.publish("rendezvous");
        }

    }

    @Dependent
    public static class RendezvousHandler {

        private final RequestContextProbe requestContextProbe;
        private final Rendezvous rendezvous;

        @Inject
        RendezvousHandler(RequestContextProbe requestContextProbe, Rendezvous rendezvous) {
            this.requestContextProbe = requestContextProbe;
            this.rendezvous = rendezvous;
        }

        @EventHandler
        void handle(String event) {
            VertxContextSafetyToggle.validateContextIfExists("processing context is marked unsafe",
                    "processing context is not marked safe");
            rendezvous.handlerStarted(requestContextProbe.isMarked());
        }

    }

    @ApplicationScoped
    public static class Rendezvous {

        private final CountDownLatch handlerStarted = new CountDownLatch(1);
        private final CountDownLatch requestChecked = new CountDownLatch(1);
        private volatile boolean requestContextSeenByHandler;

        void handlerStarted(boolean requestContextSeen) {
            requestContextSeenByHandler = requestContextSeen;
            handlerStarted.countDown();
            await(requestChecked);
        }

        void awaitHandlerStarted() {
            await(handlerStarted);
        }

        void requestChecked() {
            requestChecked.countDown();
        }

        boolean isRequestContextSeenByHandler() {
            return requestContextSeenByHandler;
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(10, SECONDS)) {
                    throw new IllegalStateException("rendezvous timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("rendezvous interrupted", e);
            }
        }

    }

}
