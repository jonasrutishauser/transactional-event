package com.github.jonasrutishauser.transactional.event.core.store;

import static java.lang.Math.min;
import static java.sql.Statement.SUCCESS_NO_INFO;
import static java.sql.Types.VARCHAR;
import static java.util.Collections.emptySet;
import static jakarta.enterprise.event.TransactionPhase.BEFORE_COMPLETION;
import static jakarta.transaction.Transactional.TxType.MANDATORY;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.IntPredicate;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import jakarta.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingDeletedEvent;
import com.github.jonasrutishauser.transactional.event.api.monitoring.ProcessingUnblockedEvent;
import com.github.jonasrutishauser.transactional.event.api.store.BlockedEvent;
import com.github.jonasrutishauser.transactional.event.api.store.EventStore;
import com.github.jonasrutishauser.transactional.event.api.store.QueryAdapter;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

@ApplicationScoped
class PendingEventStore implements EventStore {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Configuration configuration;
    private final DataSource dataSource;
    private final QueryAdapterFactory queryAdapterFactory;
    private final LockOwner lockOwner;

    private String insertSQL;
    private String readSQL;
    private String deleteSQL;
    private String deleteBlockedSQL;
    private String updateSQL;
    private String updateSQLwithLockOwner;
    private String aquireSQL;
    private String readBlockedSQL;
    private String readBlockedForUpdateSQL;
    private final Event<ProcessingUnblockedEvent> unblockedEvent;
    private final Event<ProcessingDeletedEvent> deletedEvent;

    PendingEventStore() {
        this(null, null, null, null, null, null);
    }

    @Inject
    PendingEventStore(Configuration configuration, @Events DataSource dataSource,
            QueryAdapterFactory queryAdapterFactory, LockOwner lockOwner,
            Event<ProcessingUnblockedEvent> unblockedEvent, Event<ProcessingDeletedEvent> deletedEvent) {
        this.configuration = configuration;
        this.dataSource = dataSource;
        this.queryAdapterFactory = queryAdapterFactory;
        this.lockOwner = lockOwner;
        this.unblockedEvent = unblockedEvent;
        this.deletedEvent = deletedEvent;
    }

    @PostConstruct
    void initSqlQueries() {
        QueryAdapter adapter = queryAdapterFactory.getQueryAdapter();
        insertSQL = "INSERT INTO " + configuration.getTableName()
                + " (id, event_type, context, payload, published_at, tries, lock_owner, locked_until) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        readSQL = "SELECT * FROM " + configuration.getTableName() + " WHERE id=? FOR UPDATE";
        deleteSQL = "DELETE FROM " + configuration.getTableName() + " WHERE id=? AND lock_owner=?";
        deleteBlockedSQL = "DELETE FROM " + configuration.getTableName() + " WHERE id=? AND locked_until="
                + Long.MAX_VALUE;
        updateSQL = "UPDATE " + configuration.getTableName() + " SET tries=?, lock_owner=?, locked_until=? WHERE id=?";
        updateSQLwithLockOwner = updateSQL + " AND lock_owner=?";
        aquireSQL = adapter.fixLimits(adapter.addSkipLocked("SELECT id, tries FROM " + configuration.getTableName()
                + " WHERE locked_until<=? {LIMIT ?} FOR UPDATE"));
        String readBlocked = "SELECT * FROM " + configuration.getTableName() + " WHERE locked_until=" + Long.MAX_VALUE;
        readBlockedSQL = adapter.fixLimits(readBlocked + " {LIMIT ?}");
        readBlockedForUpdateSQL = adapter.addSkipLocked(readBlocked + " AND id=? FOR UPDATE");
    }

    @Override
    @Transactional
    public boolean unblock(String eventId) {
        boolean result;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement readStatement = connection.prepareStatement(readBlockedForUpdateSQL);
             ResultSet resultSet = executeQuery(readStatement, eventId);
             PreparedStatement updateStatement = connection.prepareStatement(updateSQL)) {
            if (resultSet.next()) {
                updateStatement.setInt(1, 0);
                updateStatement.setNull(2, VARCHAR);
                updateStatement.setLong(3, lockOwner.getUntilForRetry(0, eventId));
                updateStatement.setString(4, eventId);
                result = updateStatement.executeUpdate() > 0;
            } else {
                result = false;
            }
        } catch (SQLException exception) {
            LOGGER.error("failed to unblock event '{}'", eventId, exception);
            result = false;
        }
        if (result) {
            unblockedEvent.fire(new ProcessingUnblockedEvent(eventId));
        }
        return result;
    }

    @Override
    @Transactional
    public boolean delete(String eventId) {
        boolean result;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(deleteBlockedSQL)) {
            statement.setString(1, eventId);
            statement.execute();
            result = statement.getUpdateCount() > 0;
        } catch (SQLException exception) {
            LOGGER.error("failed to unblock event '{}'", eventId, exception);
            result = false;
        }
        if (result) {
            deletedEvent.fire(new ProcessingDeletedEvent(eventId));
        }
        return result;
    }

    @Override
    @Transactional
    public Collection<BlockedEvent> getBlockedEvents(int maxElements) {
        Collection<BlockedEvent> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(readBlockedSQL);
             ResultSet resultSet = executeQuery(statement, maxElements)) {
            while (resultSet.next()) {
                result.add(new BlockedEvent(resultSet.getString("id"), //
                        resultSet.getString("event_type"), //
                        resultSet.getString("payload"), //
                        resultSet.getTimestamp("published_at").toLocalDateTime()));
            }
        } catch (SQLException exception) {
            LOGGER.error("failed to read blocked events", exception);
        }
        return result;
    }

    @Transactional(MANDATORY)
    void store(@Observes(during = BEFORE_COMPLETION) EventsPublished events) {
        String errorMessage = "failed to insert pending events";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertSQL)) {
            for (PendingEvent event : events.getEvents()) {
                statement.setString(1, event.getId());
                statement.setString(2, event.getType());
                statement.setString(3, event.getContext());
                statement.setString(4, event.getPayload());
                statement.setTimestamp(5, Timestamp.valueOf(event.getPublishedAt()));
                statement.setInt(6, event.getTries());
                statement.setString(7, lockOwner.getId());
                statement.setLong(8, lockOwner.getUntilToProcess());
                statement.addBatch();
            }
            int[] result = statement.executeBatch();
            if (result.length != events.getEvents().size() || Arrays.stream(result).anyMatch(updateCountIsNot(1))) {
                LOGGER.error("failed to insert pending events (results: {})", result);
                throw new IllegalStateException(errorMessage);
            }
        } catch (SQLException exception) {
            LOGGER.error(errorMessage, exception);
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    @Transactional(value = MANDATORY, dontRollbackOn = NoSuchElementException.class)
    public PendingEvent getAndLockEvent(String id) {
        String errorMessage = "failed to read pending event with id " + id;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(readSQL);
             ResultSet resultSet = executeQueryWithTimeout(statement, id, 1)) {
            if (!resultSet.next()) {
                throw new NoSuchElementException(errorMessage);
            }
            String owner = resultSet.getString("lock_owner");
            long lockedUntil = resultSet.getLong("locked_until");
            if (!lockOwner.isOwningForProcessing(owner, lockedUntil)) {
                throw new ConcurrentModificationException("No longer the owner");
            }
            return new PendingEvent(id, resultSet.getString("event_type"), resultSet.getString("context"),
                    resultSet.getString("payload"), resultSet.getTimestamp("published_at").toLocalDateTime(),
                    resultSet.getInt("tries"));
        } catch (SQLException exception) {
            LOGGER.error(errorMessage, exception);
            throw new IllegalStateException(errorMessage);
        }
    }

    @Transactional(MANDATORY)
    public void delete(PendingEvent event) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(deleteSQL)) {
            statement.setString(1, event.getId());
            statement.setString(2, lockOwner.getId());
            statement.setQueryTimeout(10);
            if (statement.executeUpdate() < 1) {
                throw new NoSuchElementException("failed to delete pending event with id " + event.getId());
            }
        } catch (SQLException exception) {
            String errorMessage = "failed to delete pending event with id " + event.getId();
            LOGGER.error(errorMessage, exception);
            throw new IllegalStateException(errorMessage);
        }
    }

    @Transactional(MANDATORY)
    public void updateForRetry(PendingEvent event) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateSQLwithLockOwner)) {
            statement.setInt(1, event.getTries() + 1);
            statement.setNull(2, VARCHAR);
            statement.setLong(3, lockOwner.getUntilForRetry(event.getTries(), event.getId()));
            statement.setString(4, event.getId());
            statement.setString(5, lockOwner.getId());
            statement.setQueryTimeout(10);
            if (statement.executeUpdate() < 1) {
                throw new NoSuchElementException("failed to update pending event with id " + event.getId());
            }
        } catch (SQLException exception) {
            String errorMessage = "failed to update pending event with id " + event.getId();
            LOGGER.error(errorMessage, exception);
            throw new IllegalStateException(errorMessage);
        }
    }

    @Transactional
    public Set<String> aquire(int maxAquire) {
        Set<String> result = new HashSet<>();
        int limit = min(maxAquire, configuration.getMaxAquire());
        if (limit < 1) {
            return emptySet();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement aquireStatement = connection.prepareStatement(aquireSQL);
             ResultSet resultSet = executeQuery(aquireStatement, lockOwner.getMinUntilForAquire(), limit);
             PreparedStatement updateStatement = connection.prepareStatement(updateSQL)) {
            while (resultSet.next()) {
                result.add(resultSet.getString("id"));
                updateStatement.setInt(1, resultSet.getInt("tries"));
                updateStatement.setString(2, lockOwner.getId());
                updateStatement.setLong(3, lockOwner.getUntilToProcess());
                updateStatement.setString(4, resultSet.getString("id"));
                updateStatement.addBatch();
            }
            if (!result.isEmpty()) {
                int[] res = updateStatement.executeBatch();
                if (res.length != result.size() || Arrays.stream(res).anyMatch(updateCountIsNot(1))) {
                    LOGGER.warn("failed to aquire pending events (update failed; results: {})", res);
                    result = emptySet();
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("failed to aquire pending events", exception);
            result = emptySet();
        }
        return result;
    }

    private IntPredicate updateCountIsNot(int expected) {
        return count -> count != expected && count != SUCCESS_NO_INFO;
    }

    private ResultSet executeQuery(PreparedStatement statement, String stringParam) throws SQLException {
        statement.setString(1, stringParam);
        return statement.executeQuery();
    }

    private ResultSet executeQueryWithTimeout(PreparedStatement statement, String stringParam, int seconds)
            throws SQLException {
        statement.setString(1, stringParam);
        statement.setQueryTimeout(seconds);
        return statement.executeQuery();
    }

    private ResultSet executeQuery(PreparedStatement statement, int intParam) throws SQLException {
        statement.setInt(1, intParam);
        return statement.executeQuery();
    }

    private ResultSet executeQuery(PreparedStatement statement, long param1, int param2) throws SQLException {
        statement.setLong(1, param1);
        statement.setInt(2, param2);
        return statement.executeQuery();
    }

}
