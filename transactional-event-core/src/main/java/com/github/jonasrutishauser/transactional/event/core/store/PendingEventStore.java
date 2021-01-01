package com.github.jonasrutishauser.transactional.event.core.store;

import static java.sql.Types.VARCHAR;
import static java.util.Arrays.asList;
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

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.store.BlockedEvent;
import com.github.jonasrutishauser.transactional.event.api.store.EventStore;
import com.github.jonasrutishauser.transactional.event.core.PendingEvent;

@Dependent
class PendingEventStore implements EventStore {

    private static final String INSERT_SQL = "INSERT INTO event_store (id, event_type, payload, published_at, tries, lock_owner, locked_until) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String READ_SQL = "SELECT id, event_type, payload, published_at, tries, lock_owner, locked_until FROM event_store WHERE id=? FOR UPDATE";
    private static final String DELETE_SQL = "DELETE FROM event_store WHERE id=? AND lock_owner=?";
    private static final String UPDATE_SQL = "UPDATE event_store SET tries=?, lock_owner=?, locked_until=? WHERE id=?";
    private static final String AQUIRE_SQL = "SELECT id, tries FROM event_store WHERE locked_until<=? {LIMIT 10} FOR UPDATE";
    private static final String READ_BLOCKED_SQL = "SELECT id, event_type, payload, published_at FROM event_store WHERE locked_until=" + Long.MAX_VALUE;

    private static final Logger LOGGER = LogManager.getLogger();

    private Boolean supportsSkipLocked;
    private final DataSource dataSource;
    private final LockOwner lockOwner;

    PendingEventStore() {
        this(null, null);
    }

    @Inject
    PendingEventStore(@Events DataSource dataSource, LockOwner lockOwner) {
        this.dataSource = dataSource;
        this.lockOwner = lockOwner;
    }

    @Override
    @Transactional
    public boolean unblock(String eventId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement readStatement = connection
                     .prepareStatement(addSkipLocked(connection, READ_BLOCKED_SQL + " AND id=? FOR UPDATE"));
             PreparedStatement updateStatement = connection.prepareStatement(UPDATE_SQL)) {
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
             PreparedStatement statement = connection
                     .prepareStatement(READ_BLOCKED_SQL + " {LIMIT " + maxElements + "}");
             ResultSet resultSet = statement.executeQuery()) {
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
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
            if (result.length != events.getEvents().size() || Arrays.stream(result).anyMatch(count -> count != 1)) {
                String message = "failed to insert pending events";
                LOGGER.error(message);
                throw new IllegalStateException(message);
            }
        } catch (SQLException exception) {
            String message = "failed to insert pending events";
            LOGGER.error(message, exception);
            throw new IllegalStateException(message, exception);
        }
    }

    @Transactional(value=MANDATORY, dontRollbackOn = NoSuchElementException.class)
    public PendingEvent getAndLockEvent(String id) {
        String errorMessage = "failed to read pending event with id " + id;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(READ_SQL)) {
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
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, event.getId());
            statement.setString(2, lockOwner.getId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            String errorMessage = "failed to delete pending event with id " + event.getId();
            LOGGER.error(errorMessage, exception);
            throw new IllegalStateException(errorMessage);
        }
    }

    @Transactional(MANDATORY)
    public void updateForRetry(PendingEvent event) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL + " AND lock_owner=?")) {
            statement.setInt(1, event.getTries() + 1);
            statement.setNull(2, VARCHAR);
            statement.setLong(3, lockOwner.getUntilForRetry(event.getTries(), event.getId()));
            statement.setString(4, event.getId());
            statement.setString(5, lockOwner.getId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            String errorMessage = "failed to update pending event with id " + event.getId();
            LOGGER.error(errorMessage, exception);
            throw new IllegalStateException(errorMessage);
        }
    }

    @Transactional
    public Set<String> aquire() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement aquireStatement = connection.prepareStatement(addSkipLocked(connection, AQUIRE_SQL));
             PreparedStatement updateStatement = connection.prepareStatement(UPDATE_SQL)) {
            aquireStatement.setLong(1, lockOwner.getMinUntilForAquire());
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
                        if (res < 1) {
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

    private String addSkipLocked(Connection connection, String sql) throws SQLException {
        if (supportsSkipLocked == null) {
            Set<String> keywords = new HashSet<>(asList(connection.getMetaData().getSQLKeywords().split(",")));
            supportsSkipLocked = Boolean.valueOf(keywords.contains("SKIP") && keywords.contains("LOCKED"));
        }
        if (supportsSkipLocked.booleanValue()) {
            return sql.replace("FOR UPDATE", "FOR UPDATE SKIP LOCKED");
        }
        return sql;
    }

}
