package com.github.jonasrutishauser.transactional.event.core.store;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.jonasrutishauser.transactional.event.api.MPConfiguration;
import com.github.jonasrutishauser.transactional.event.api.store.BlockedEvent;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

class PendingEventStoreTest {

    private DataSource dataSource;
    private PendingEventStore testee;

    protected DataSource getDataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:testPendingEventStore;LOCK_TIMEOUT=10");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    @BeforeEach
    void initDb() throws Exception {
        dataSource = getDataSource();
        testee = new PendingEventStore(new MPConfiguration(), dataSource, new QueryAdapterFactory(dataSource),
                new LockOwner(Clock.fixed(Instant.ofEpochMilli(42424242), ZoneOffset.UTC), "lock_id"));
        testee.initSqlQueries();
        try (Statement statement = dataSource.getConnection().createStatement()) {
            for (String sql : new String(Files.readAllBytes(Paths.get(getClass().getResource(ddl()).toURI())),
                    StandardCharsets.UTF_8).split(";")) {
                if (!sql.trim().isEmpty()) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException e) {
                        if (!sql.contains("DROP ")) {
                            throw e;
                        }
                    }
                }
            }
            statement.execute("TRUNCATE TABLE event_store");
        }
    }

    protected String ddl() {
        return "/table.sql";
    }

    @Test
    void proxyConstructor() {
        assertNotNull(new PendingEventStore());
    }

    @Test
    void unblockWhenEmpty() {
        assertFalse(testee.unblock("foo"));
    }

    @Test
    void unblockWhenExists() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute("INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 0, null, "
                    + Long.MAX_VALUE + ")");
        }

        assertTrue(testee.unblock("foo"));

        try (Statement statement = dataSource.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM event_store WHERE id='foo'")) {
            assertTrue(resultSet.next());
            assertEquals(42424242, resultSet.getLong("locked_until"));
            assertNull(resultSet.getString("lock_owner"));
        }
    }

    @Test
    void unblockWhenLocked() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 0, null, "
                    + Long.MAX_VALUE + ")");
            connection.commit();
            statement.execute("SELECT * FROM event_store WHERE id='foo' FOR UPDATE");

            assertFalse(testee.unblock("foo"));
        }
    }

    @Test
    void getBlockedEventsWhenEmpty() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 0, null, 42)");
        }

        Collection<BlockedEvent> result = testee.getBlockedEvents(10);

        assertThat(result, is(empty()));
    }

    @Test
    void getBlockedEventsWhenSomeEvents() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 0, null, "
                            + Long.MAX_VALUE + ")");
        }

        Collection<BlockedEvent> result = testee.getBlockedEvents(10);

        assertThat(result, hasSize(1));
        BlockedEvent event = result.iterator().next();
        assertEquals("foo", event.getEventId());
        assertEquals("type", event.getEventType());
        assertEquals("payload", event.getPayload());
        assertEquals(LocalDateTime.of(2021, 1, 1, 12, 42), event.getPublishedAt());
    }

    @Test
    void getBlockedEventsWhenMoreEventsThanLimit() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 0, null, "
                            + Long.MAX_VALUE + ")");
            statement.execute(
                    "INSERT INTO event_store VALUES ('bar', 'type2', 'payload2', {ts '2021-01-01 13:42:00'}, 0, null, "
                            + Long.MAX_VALUE + ")");
        }

        Collection<BlockedEvent> result = testee.getBlockedEvents(1);

        assertThat(result, hasSize(1));
    }

    @Test
    void getBlockedEventsWhenTableNotExists() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute("DROP TABLE event_store");
        }

        Collection<BlockedEvent> result = testee.getBlockedEvents(42);

        assertThat(result, is(empty()));
    }

    @Test
    void storeSingleEvent() throws Exception {
        testee.store(new EventsPublished(asList(new PendingEvent("test", "type", "payload", LocalDateTime.now()))));

        try (Statement statement = dataSource.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM event_store")) {
            assertTrue(resultSet.next());
            assertEquals("test", resultSet.getString("id"));
            assertEquals("type", resultSet.getString("event_type"));
            assertEquals("payload", resultSet.getString("payload"));
            assertEquals(0, resultSet.getLong("tries"));
            assertEquals(42724242, resultSet.getLong("locked_until"));
            assertEquals("lock_id", resultSet.getString("lock_owner"));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void storeSingleEventDuplicateId() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('test', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 0, null, 12)");
        }
        EventsPublished events = new EventsPublished(
                asList(new PendingEvent("test", "type", "payload", LocalDateTime.now())));

        assertThrows(IllegalStateException.class, () -> testee.store(events));
    }

    @Test
    void storeMultipleEvent() throws Exception {
        testee.store(new EventsPublished(asList(new PendingEvent("test", "type", "payload", LocalDateTime.now()),
                new PendingEvent("foo", "t", "p", LocalDateTime.now()),
                new PendingEvent("bar", "a", "b", LocalDateTime.now()))));

        try (Statement statement = dataSource.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM event_store")) {
            for (int i = 0; i < 3; i++) {
                assertTrue(resultSet.next());
                assertEquals(42724242, resultSet.getLong("locked_until"));
                assertEquals("lock_id", resultSet.getString("lock_owner"));
            }
            assertFalse(resultSet.next());
        }
    }

    @Test
    void getAndLockEventWhenNotExists() throws Exception {
        assertThrows(NoSuchElementException.class, () -> testee.getAndLockEvent("foo"));
    }

    @Test
    void getAndLockEventWhenOtherTheOwner() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 0, null, 999999999)");
        }

        assertThrows(ConcurrentModificationException.class, () -> testee.getAndLockEvent("foo"));
    }

    @Test
    void getAndLockEventWhenNoLongerTheOwner() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 0, 'lock_id', 12)");
        }

        assertThrows(ConcurrentModificationException.class, () -> testee.getAndLockEvent("foo"));
    }

    @Test
    void getAndLockEventWhenLockedByOther() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 0, 'other', 999999999)");
            connection.commit();
            statement.execute("SELECT * FROM event_store WHERE id='foo' FOR UPDATE");

            assertThrows(IllegalStateException.class, () -> testee.getAndLockEvent("foo"));
        }
    }

    @Test
    void getAndLockEventWhenSuccessful() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 42, 'lock_id', 999999999)");
        }

        PendingEvent result = testee.getAndLockEvent("foo");

        assertNotNull(result);
        assertEquals("foo", result.getId());
        assertEquals("type", result.getType());
        assertEquals("payload", result.getPayload());
        assertEquals(42, result.getTries());
    }

    @Test
    void deleteWhenNotExists() throws Exception {
        PendingEvent event = new PendingEvent("foo", "t", "p", LocalDateTime.now());

        assertThrows(NoSuchElementException.class, () -> testee.delete(event));
    }

    @Test
    void deleteWhenLocked() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 0, 'lock_id', 999999999)");
            connection.commit();
            statement.execute("SELECT * FROM event_store WHERE id='foo' FOR UPDATE");
            PendingEvent event = new PendingEvent("foo", "t", "p", LocalDateTime.of(2012, 1, 1, 12, 42));

            assertThrows(IllegalStateException.class, () -> testee.delete(event));
        }
    }

    @Test
    void deleteWhenSuccessful() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 42, 'lock_id', 999999999)");
        }
        PendingEvent event = new PendingEvent("foo", "t", "p", LocalDateTime.of(2012, 1, 1, 12, 42), 42);

        testee.delete(event);

        try (Statement statement = dataSource.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM event_store")) {
            assertFalse(resultSet.next());
        }
    }

    @Test
    void updateForRetryWhenNotExists() throws Exception {
        PendingEvent event = new PendingEvent("foo", "t", "p", LocalDateTime.now());

        assertThrows(NoSuchElementException.class, () -> testee.updateForRetry(event));
    }

    @Test
    void updateForRetryWhenLocked() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 0, 'lock_id', 999999999)");
            connection.commit();
            statement.execute("SELECT * FROM event_store WHERE id='foo' FOR UPDATE");
            PendingEvent event = new PendingEvent("foo", "t", "p", LocalDateTime.of(2012, 1, 1, 12, 42));

            assertThrows(IllegalStateException.class, () -> testee.updateForRetry(event));
        }
    }

    @Test
    void updateForRetryWhenSuccessful() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 1, 'lock_id', 999999999)");
        }
        PendingEvent event = new PendingEvent("foo", "t", "p", LocalDateTime.of(2012, 1, 1, 12, 42), 1);

        testee.updateForRetry(event);

        try (Statement statement = dataSource.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM event_store")) {
            assertTrue(resultSet.next());
            assertEquals("foo", resultSet.getString("id"));
            assertEquals("t", resultSet.getString("event_type"));
            assertEquals("p", resultSet.getString("payload"));
            assertEquals(2, resultSet.getLong("tries"));
            assertEquals(42426242, resultSet.getLong("locked_until"));
            assertNull(resultSet.getString("lock_owner"));
        }
    }

    @Test
    void updateForRetryWhenSuccessfulAfterTooManyTries() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('foo', 't', 'p', {ts '2021-01-01 12:42:00'}, 42, 'lock_id', 999999999)");
        }
        PendingEvent event = new PendingEvent("foo", "t", "p", LocalDateTime.of(2012, 1, 1, 12, 42), 42);

        testee.updateForRetry(event);

        try (Statement statement = dataSource.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM event_store")) {
            assertTrue(resultSet.next());
            assertEquals("foo", resultSet.getString("id"));
            assertEquals("t", resultSet.getString("event_type"));
            assertEquals("p", resultSet.getString("payload"));
            assertEquals(43, resultSet.getLong("tries"));
            assertEquals(Long.MAX_VALUE, resultSet.getLong("locked_until"));
            assertNull(resultSet.getString("lock_owner"));
        }
    }

    @Test
    void aquireWhenEmpty() {
        Set<String> result = testee.aquire();

        assertEquals(emptySet(), result);
    }

    @Test
    void aquireWhenTableNotExists() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute("DROP TABLE event_store");
        }

        Set<String> result = testee.aquire();

        assertEquals(emptySet(), result);
    }

    @Test
    void aquireWhenSomeMessages() throws Exception {
        try (Statement statement = dataSource.getConnection().createStatement()) {
            statement.execute(
                    "INSERT INTO event_store VALUES ('test', 'type', 'payload', {ts '2021-01-01 12:42:00'}, 0, null, 999999999)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e01', 't', 'p', {ts '2021-01-01 12:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e02', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e03', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e04', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e05', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e06', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e07', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e08', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e09', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e10', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e11', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
            statement.execute(
                    "INSERT INTO event_store VALUES ('e12', 't', 'p', {ts '2021-01-01 13:42:00'}, 0, null, 12)");
        }

        Set<String> result = testee.aquire();

        assertThat(result, hasSize(10));
    }

}
