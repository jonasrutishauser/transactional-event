package com.github.jonasrutishauser.transactional.event.core.store;

import static java.sql.Statement.SUCCESS_NO_INFO;
import static java.sql.Types.VARCHAR;
import static java.util.Collections.emptySet;
import static javax.enterprise.event.TransactionPhase.BEFORE_COMPLETION;
import static javax.transaction.Transactional.TxType.MANDATORY;

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

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.api.Events;
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
    private String updateSQL;
    private String aquireSQL;
    private String readBlockedSQL;
    private String readBlockedForUpdateSQL;

    PendingEventStore() {
        this(null, null, null, null);
    }

    @Inject
    PendingEventStore(Configuration configuration, @Events DataSource dataSource,
            QueryAdapterFactory queryAdapterFactory, LockOwner lockOwner) {
        this.configuration = configuration;
        this.dataSource = dataSource;
        this.queryAdapterFactory = queryAdapterFactory;
        this.lockOwner = lockOwner;
    }

    @PostConstruct
    void initSqlQueries() {
        QueryAdapter adapter = queryAdapterFactory.getQueryAdapter();
        insertSQL = "INSERT INTO " + configuration.getTableName()
                + " (id, event_type, payload, published_at, tries, lock_owner, locked_until) VALUES (?, ?, ?, ?, ?, ?, ?)";
        readSQL = "SELECT * FROM " + configuration.getTableName() + " WHERE id=? FOR UPDATE";
        deleteSQL = "DELETE FROM " + configuration.getTableName() + " WHERE id=? AND lock_owner=?";
        updateSQL = "UPDATE " + configuration.getTableName() + " SET tries=?, lock_owner=?, locked_until=? WHERE id=?";
        aquireSQL = adapter.fixLimits(adapter.addSkipLocked("SELECT id, tries FROM " + configuration.getTableName()
                + " WHERE locked_until<=? {LIMIT ?} FOR UPDATE"));
        String readBlocked = "SELECT * FROM " + configuration.getTableName() + " WHERE locked_until=" + Long.MAX_VALUE;
        readBlockedSQL = adapter.fixLimits(readBlocked + " {LIMIT ?}");
        readBlockedForUpdateSQL = adapter.addSkipLocked(readBlocked + " AND id=? FOR UPDATE");
    }

    @Override
    @Transactional
    public boolean unblock(String eventId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement readStatement = connection.prepareStatement(readBlockedForUpdateSQL);
             PreparedStatement updateStatement = connection.prepareStatement(updateSQL)) {
            readStatement.setString(1, eventId);
            try (ResultSet resultSet = readStatement.executeQuery()) {
                if (resultSet.next()) {
                    updateStatement.setInt(1, 0);
                    updateStatement.setNull(2, VARCHAR);
                    updateStatement.setLong(3, lockOwner.getUntilForRetry(0, eventId));
                    updateStatement.setString(4, eventId);
                    return updateStatement.executeUpdate() > 0;
                } else {
                    return false;
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("failed to unblock event '{}'", eventId, exception);
            return false;
        }
    }

    @Override
    @Transactional
    public Collection<BlockedEvent> getBlockedEvents(int maxElements) {
        Collection<BlockedEvent> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(readBlockedSQL)) {
            statement.setInt(1, maxElements);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new BlockedEvent(resultSet.getString("id"), //
                            resultSet.getString("event_type"), //
                            resultSet.getString("payload"), //
                            resultSet.getTimestamp("published_at").toLocalDateTime()));
                }
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
                statement.setString(3, event.getPayload());
                statement.setTimestamp(4, Timestamp.valueOf(event.getPublishedAt()));
                statement.setInt(5, event.getTries());
                statement.setString(6, lockOwner.getId());
                statement.setLong(7, lockOwner.getUntilToProcess());
                statement.addBatch();
            }
            int[] result = statement.executeBatch();
            if (result.length != events.getEvents().size()
                    || Arrays.stream(result).anyMatch(count -> count != 1 && count != SUCCESS_NO_INFO)) {
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
             PreparedStatement statement = connection.prepareStatement(readSQL)) {
            statement.setString(1, id);
            statement.setQueryTimeout(1);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new NoSuchElementException(errorMessage);
                }
                String owner = resultSet.getString("lock_owner");
                long lockedUntil = resultSet.getLong("locked_until");
                if (!lockOwner.isOwningForProcessing(owner, lockedUntil)) {
                    throw new ConcurrentModificationException("No longer the owner");
                }
                return new PendingEvent(id, resultSet.getString("event_type"), resultSet.getString("payload"),
                        resultSet.getTimestamp("published_at").toLocalDateTime(), resultSet.getInt("tries"));
            }
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
             PreparedStatement statement = connection.prepareStatement(updateSQL + " AND lock_owner=?")) {
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
    public Set<String> aquire() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement aquireStatement = connection.prepareStatement(aquireSQL);
             PreparedStatement updateStatement = connection.prepareStatement(updateSQL)) {
            aquireStatement.setLong(1, lockOwner.getMinUntilForAquire());
            aquireStatement.setInt(2, configuration.getMaxAquire());
            Set<String> result = new HashSet<>();
            try (ResultSet resultSet = aquireStatement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("id"));
                    updateStatement.setInt(1, resultSet.getInt("tries"));
                    updateStatement.setString(2, lockOwner.getId());
                    updateStatement.setLong(3, lockOwner.getUntilToProcess());
                    updateStatement.setString(4, resultSet.getString("id"));
                    updateStatement.addBatch();
                }
                if (!result.isEmpty()) {
                    for (int res : updateStatement.executeBatch()) {
                        if (res != 1 && res != SUCCESS_NO_INFO) {
                            LOGGER.warn("failed to aquire pending events (update failed)");
                            return emptySet();
                        }
                    }
                }
                return result;
            }
        } catch (SQLException exception) {
            LOGGER.warn("failed to aquire pending events", exception);
            return emptySet();
        }
    }

}
