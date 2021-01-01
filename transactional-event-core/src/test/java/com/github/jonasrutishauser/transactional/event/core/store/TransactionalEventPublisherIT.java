package com.github.jonasrutishauser.transactional.event.core.store;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Priority;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Alternative;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventSerializer;
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

    @Test
    void testPublish() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(Files.readString(Paths.get(getClass().getResource("/table.sql").toURI())));
        }
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

    @Dependent
    @Priority(1)
    static class Configuration {

        @Events
        @Produces
        @Resource(name = "testDb")
        private DataSource ds;
        
//        @Resource
//        private ManagedScheduledExecutorService executorService;
//        
//        @Events
//        @Produces
//        @Alternative
//        public ManagedScheduledExecutorService getExecutorService() {
//            return executorService;
//        }

    }

    @ApplicationScoped
    static class ReceivedMessages {
        private Set<String> messages = new HashSet<>();
        
        public void add(String message) {
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
