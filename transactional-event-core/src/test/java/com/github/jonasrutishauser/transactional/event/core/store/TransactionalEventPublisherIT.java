package com.github.jonasrutishauser.transactional.event.core.store;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.logging.log4j.LogManager;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.ContainerProperties;
import org.apache.openejb.testing.ContainerProperties.Property;
import org.apache.openejb.testing.Default;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventSerializer;
import com.github.jonasrutishauser.transactional.event.api.store.BlockedEvent;
import com.github.jonasrutishauser.transactional.event.api.store.EventStore;
import com.github.jonasrutishauser.transactional.event.core.openejb.ApplicationComposerExtension;

@Default
@Classes(cdi = true)
@ExtendWith(ApplicationComposerExtension.class)
@ContainerProperties({@Property(name = "testDb", value = "new://Resource?type=DataSource"),
        @Property(name = "testDb.JdbcUrl",
                value = "jdbc:h2:mem:test;LOCK_TIMEOUT=20000;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE"),
        @Property(name = "testDb.JdbcDriver", value = "org.h2.Driver")})
public class TransactionalEventPublisherIT {

    @Inject
    EventPublisher publisher;

    @Inject
    UserTransaction transaction;

    @Inject
    @Events
    DataSource dataSource;

    @Inject
    Dispatcher dispatcher;
    
    @Inject
    ReceivedMessages messages;

    @Inject
    EventStore store;

    @BeforeEach
    void initDb() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(new String(Files.readAllBytes(Paths.get(getClass().getResource("/table.sql").toURI())),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    void testPublish() throws Exception {
        transaction.begin();
        publisher.publish(new TestSerializableEvent("test"));
        publisher.publish(new TestJaxbTypeEvent("foo"));
        publisher.publish(new TestJaxbElementEvent("bar"));
        publisher.publish(new TestJsonbEvent("jsonb"));
        publisher.publish(Integer.MAX_VALUE);
        transaction.commit();

        await().until(() -> messages.contains("foo"));
        await().until(() -> messages.contains("bar"));
        await().until(() -> messages.contains("jsonb"));

        await().conditionEvaluationListener(condition -> dispatcher.schedule()).until(() -> messages.contains("test"));
    }

    @Test
    void testBlocking() throws Exception {
        assertFalse(store.unblock("unknown-id"));

        messages.setBlock(true);
        LocalDateTime start = LocalDateTime.now();
        transaction.begin();
        publisher.publish(new TestJsonbEvent("blocking message"));
        transaction.commit();

        assertThat(store.getBlockedEvents(10), is(empty()));
        await().atMost(2, MINUTES).conditionEvaluationListener(condition -> dispatcher.schedule()) //
                .until(() -> store.getBlockedEvents(10), is(not(empty())));

        messages.setBlock(false);
        BlockedEvent event = store.getBlockedEvents(10).iterator().next();
        assertEquals("TestJsonbEvent", event.getEventType());
        assertEquals("{\"message\":\"blocking message\"}", event.getPayload());
        assertThat(event.getPublishedAt(), is(both(greaterThanOrEqualTo(start)).and(lessThan(LocalDateTime.now()))));
        assertTrue(store.unblock(event.getEventId()));
        assertFalse(store.unblock(event.getEventId()));

        dispatcher.schedule();

        assertThat(store.getBlockedEvents(10), is(empty()));
        await().until(() -> messages.contains("blocking message"));
        assertThat(store.getBlockedEvents(10), is(empty()));
    }

    @Dependent
    static class Configuration {

        @Events
        @Produces
        @Resource(name = "testDb")
        private DataSource ds;

    }

    @ApplicationScoped
    static class ReceivedMessages {
        private Set<String> messages = new HashSet<>();

        private boolean block;

        public void setBlock(boolean block) {
            this.block = block;
        }
        
        public void add(String message) {
            if (block) {
                throw new IllegalStateException("block requested");
            }
            messages.add(message);
        }
        
        public boolean contains(String message) {
            return messages.contains(message);
        }
    }

    static abstract class AbstractTestHandler<T> extends AbstractHandler<T> {
        @Inject
        ReceivedMessages messages;
        
        protected void gotMessage(String message) {
            this.messages.add(message);;
        }
    }

    @ApplicationScoped
    @EventHandler
    static class TestSerializableHandler extends AbstractTestHandler<TestSerializableEvent> {
        int tries = 0;
        @Override
        protected void handle(TestSerializableEvent event) {
            if (tries++ < 2) {
                throw new IllegalStateException("test retry " + tries);
            }
            gotMessage(event.message);
        }
    }

    @Dependent
    @EventHandler
    static class TestJaxbTypeHandler extends AbstractTestHandler<TestJaxbTypeEvent> {
        @Override
        protected void handle(TestJaxbTypeEvent event) {
            gotMessage(event.message);
        }
    }

    @Dependent
    @EventHandler
    static class TestJaxbElementHandler extends AbstractTestHandler<TestJaxbElementEvent> {
        @Override
        protected void handle(TestJaxbElementEvent event) {
            gotMessage(event.message);
        }
    }

    @Dependent
    @EventHandler
    static class TestJsonbHandler extends AbstractTestHandler<TestJsonbEvent> {
        @Override
        protected void handle(TestJsonbEvent event) {
            gotMessage(event.message);
        }
    }

    @Dependent
    @EventHandler(eventType = "Integer")
    static class TestIntegerHandler extends AbstractHandler<Integer> {
        @Override
        protected void handle(Integer event) {
            LogManager.getLogger().info("got int: {}", event);
        }
    }

    @Dependent
    static class IntegerSerializer implements EventSerializer<Integer>, EventDeserializer<Integer> {
        @Override
        public String serialize(Integer event) {
            return event.toString();
        }
        @Override
        public Integer deserialize(String event) {
            return Integer.valueOf(event);
        }
    }

    static class TestSerializableEvent implements Serializable {
        private final String message;

        public TestSerializableEvent(String message) {
            this.message = message;
        }
    }

    @XmlType
    static class TestJaxbTypeEvent {
        @XmlElement
        private final String message;
        
        TestJaxbTypeEvent() {
            this(null);
        }

        public TestJaxbTypeEvent(String message) {
            this.message = message;
        }
    }

    @XmlRootElement
    static class TestJaxbElementEvent {
        @XmlElement
        private final String message;
        
        TestJaxbElementEvent() {
            this(null);
        }

        public TestJaxbElementEvent(String message) {
            this.message = message;
        }
    }

    static class TestJsonbEvent {
        @JsonbProperty
        private final String message;
        
        @JsonbCreator
        public TestJsonbEvent(@JsonbProperty("message") String message) {
            this.message = message;
        }
    }

}
