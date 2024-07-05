package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Typed;

class EventHandlerWrongTypesTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest() //
            .assertException(t -> assertThat(t.getMessage(),
                    containsString("InvalidEventHandler type is missing on bean with implicit event type"))) //
            .withApplicationRoot(archive -> archive //
                    .addClasses(TestEvent.class, InvalidEventHandler.class) //
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
            );

    @Test
    void test() {
        fail("deployment error expected");
    }

    @Dependent
    @EventHandler
    @Typed({AbstractHandler.class, Handler.class})
    public static class InvalidEventHandler extends AbstractHandler<TestEvent> {
        @Override
        protected void handle(TestEvent event) {
            fail();
        }
    }
}
