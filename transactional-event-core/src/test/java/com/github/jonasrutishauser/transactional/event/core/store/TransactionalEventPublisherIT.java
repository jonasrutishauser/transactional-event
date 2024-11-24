package com.github.jonasrutishauser.transactional.event.core.store;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.jonasrutishauser.cdi.test.api.annotations.GlobalTestImplementation;
import com.github.jonasrutishauser.cdi.test.api.context.TestScoped;
import com.github.jonasrutishauser.cdi.test.core.junit.CdiTestJunitExtension;
import com.github.jonasrutishauser.cdi.test.jndi.DataSourceEntry;
import com.github.jonasrutishauser.cdi.test.microprofile.config.ConfigPropertyValue;
import com.github.jonasrutishauser.transactional.event.api.EventPublisher;
import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.handler.AbstractHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventDeserializer;
import com.github.jonasrutishauser.transactional.event.api.serialization.EventSerializer;
import com.github.jonasrutishauser.transactional.event.api.store.BlockedEvent;
import com.github.jonasrutishauser.transactional.event.api.store.EventStore;
import com.github.jonasrutishauser.transactional.event.core.defaults.DefaultProcessingStrategy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.smallrye.metrics.MetricRegistries;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.transaction.UserTransaction;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@ExtendWith(CdiTestJunitExtension.class)
@DataSourceEntry(name = "testDb", compEnv = true,
        url = "jdbc:h2:mem:test;LOCK_TIMEOUT=20000;INIT=RUNSCRIPT FROM 'classpath:table.sql'", driver = Driver.class,
        user = "sa", password = "sa")
@ConfigPropertyValue(name="transactional.event.initialDispatchInterval", value="2")
@ConfigPropertyValue(name="transactional.event.allInUseInterval", value="2")
class TransactionalEventPublisherIT {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Inject
    EventPublisher publisher;

    @Inject
    UserTransaction transaction;

    @Inject
    @Events
    DataSource dataSource;

    @Inject
    DispatcherImpl dispatcher;

    @Inject
    ReceivedMessages messages;

    @Inject
    EventStore store;

    @Test
    void testPublish() throws Exception {
        transaction.begin();
        publisher.publish(new TestSerializableEvent("test"));
        publisher.publish(new TestJaxbTypeEvent("foo"));
        publisher.publish(new TestJaxbElementEvent("bar"));
        publisher.publish(new TestJsonbEvent("jsonb"));
        publisher.publish(Integer.MAX_VALUE);
        publisher.publish("test string");
        transaction.commit();

        await().until(() -> messages.contains("foo"));
        await().until(() -> messages.contains("bar"));
        await().until(() -> messages.contains("jsonb"));
        await().until(() -> messages.contains("test string"));

        await().conditionEvaluationListener(condition -> dispatcher.schedule()).until(() -> messages.contains("test"));
    }

    @Test
    void testManyDispatching() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (int i = 10; i < 5000; i++) {
                statement.addBatch("INSERT INTO event_store VALUES ('event" + i
                        + "', 'TestJsonbEvent', null, '{\"message\":\"slow event" + i
                        + "\"}', {ts '2021-01-01 12:42:00'}, 0, null, 0)");
            }
            statement.executeBatch();
        }

        await().atMost(1, MINUTES).until(() -> messages.size() == 5000 - 10);
    }

    @Test
    void testMetrics() {
        assertThat(MetricRegistries.get(APPLICATION).getMetrics(), is(aMapWithSize(14)));
        MetricRegistries.get(APPLICATION).getMetrics().forEach((id, metric) -> {
            if (metric instanceof Counter) {
                assertEquals(0, ((Counter) metric).getCount());
            } else if (metric instanceof Gauge) {
                assertNotNull(((Gauge<?>) metric).getValue());
            }
        });
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

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true",
            disabledReason = "Not stable when running with github actions")
    void testOpenTelemtry() throws Exception {
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("test");
        Span span = tracer.spanBuilder("client span").startSpan();
        try (Scope unused = span.makeCurrent()) {
            transaction.begin();
            publisher.publish(new TestSerializableEvent("test"));
            publisher.publish(new TestJaxbTypeEvent("foo"));
            transaction.commit();
        } finally {
            span.end();
        }

        assertThat(otelTesting.getSpans(), hasSize(greaterThanOrEqualTo(4)));

        await().until(() -> messages.contains("foo"));

        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans, hasSize(greaterThanOrEqualTo(8)));
        assertEquals("TestSerializableEvent send", spans.get(0).getName());
        assertEquals(span.getSpanContext(), spans.get(0).getParentSpanContext());
        assertEquals("TestJaxbTypeEvent send", spans.get(1).getName());
        assertEquals(span.getSpanContext(), spans.get(1).getParentSpanContext());
        assertEquals("transactional-event receive", spans.get(2).getName());
        assertEquals(span.getSpanContext(), spans.get(2).getParentSpanContext());
        assertEquals(span.getSpanContext(), spans.get(3).getSpanContext());

        String serializableId = spans.get(0).getAttributes().get(stringKey("messaging.message_id"));
        String jaxbId = spans.get(1).getAttributes().get(stringKey("messaging.message_id"));
        assertThat(spans.subList(4, 8), containsInAnyOrder( //
                hasProperty("name", equalTo("TestSerializableEvent process")), //
                allOf(hasProperty("name", equalTo("transactional-event process")), //
                        hasProperty("parentSpanContext", equalTo(spans.get(2).getSpanContext())), //
                        hasProperty("attributes", hasEntry(stringKey("messaging.message_id"), serializableId))), //
                hasProperty("name", equalTo("TestJaxbTypeEvent process")), //
                allOf(hasProperty("name", equalTo("transactional-event process")), //
                        hasProperty("parentSpanContext", equalTo(spans.get(2).getSpanContext())), //
                        hasProperty("attributes", hasEntry(stringKey("messaging.message_id"), jaxbId))) //
        ));

        await().until(() -> messages.contains("test"));

        spans = otelTesting.getSpans();
        assertThat(spans.subList(8, spans.size()),
                containsInRelativeOrder(hasProperty("name", equalTo("TestSerializableEvent process")), //
                        hasProperty("name", equalTo("transactional-event process")), //
                        hasProperty("name", equalTo("transactional-event receive"))));
    }

    @Dependent
    static class DbConfiguration {

        @Events
        @Produces
        @Resource(name = "testDb")
        private DataSource ds;

    }

    @TestScoped
    static class ReceivedMessages {
        private Set<String> messages = new ConcurrentSkipListSet<>();

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

        public int size() {
            return messages.size();
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
    static class TestCDI41Handler {
        @Inject
        ReceivedMessages messages;

        @EventHandler
        void handle(String event) {
            this.messages.add(event);
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
        private static final long serialVersionUID = 1L;

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

    public static class TestJsonbEvent {
        private final String message;

        @JsonbCreator
        public TestJsonbEvent(@JsonbProperty("message") String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }

    @GlobalTestImplementation
    static class TestProcessingStrategy extends DefaultProcessingStrategy {
        @Override
        public Duration waitDurationForRetry(int tries) {
            return Duration.ofMillis(1);
        }
    }

}
