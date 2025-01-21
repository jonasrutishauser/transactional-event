package com.github.jonasrutishauser.transactional.event.cdi.test;

import static org.awaitility.Awaitility.await;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.jonasrutishauser.cdi.test.api.context.TestScoped;
import com.github.jonasrutishauser.cdi.test.core.junit.CdiTestJunitExtension;
import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.store.EventStore;

import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

@ExtendWith(CdiTestJunitExtension.class)
class TransactionalEventTest {

    @Inject
    EventPublisher publisher;

    @Inject
    UserTransaction transaction;

    @Inject
    @Events
    DataSource dataSource;

    @Inject
    ReceivedMessages messages;

    @Inject
    EventStore store;

    @Test
    void testPublish() throws Exception {
        transaction.begin();
        publisher.publish(new TestSerializableEvent("test"));
        transaction.commit();

        await().until(() -> messages.contains("test"));
    }

    @TestScoped
    static class ReceivedMessages {
        private Set<String> messages = new ConcurrentSkipListSet<>();

        public void add(String message) {
            messages.add(message);
        }

        public boolean contains(String message) {
            return messages.contains(message);
        }
    }

    abstract static class AbstractTestHandler<T> extends AbstractHandler<T> {
        @Inject
        ReceivedMessages messages;

        protected void gotMessage(String message) {
            this.messages.add(message);
        }
    }

    @TestScoped
    @EventHandler
    static class TestSerializableHandler extends AbstractTestHandler<TestSerializableEvent> {
        int tries = 0;

        @Override
        protected void handle(TestSerializableEvent event) {
            gotMessage(event.message);
        }
    }

    static class TestSerializableEvent implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String message;

        public TestSerializableEvent(String message) {
            this.message = message;
        }
    }

}
