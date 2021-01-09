package com.github.jonasrutishauser.transactional.event.core.store;

import static java.util.Arrays.asList;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.TransientReference;
import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.store.QueryAdapter;

@ApplicationScoped
class QueryAdapterFactory {

    private static final Logger LOGGER = LogManager.getLogger();

    private final QueryAdapter queryAdapter;

    public QueryAdapter getQueryAdapter() {
        return queryAdapter;
    }

    QueryAdapterFactory() {
        queryAdapter = null;
    }

    QueryAdapterFactory(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            queryAdapter = getQueryAdapter(connection);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Inject
    QueryAdapterFactory(Instance<QueryAdapter> cdiInstance, @TransientReference @Events DataSource dataSource) {
        if (cdiInstance.isUnsatisfied()) {
            try (Connection connection = dataSource.getConnection()) {
                queryAdapter = getQueryAdapter(connection);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        } else {
            queryAdapter = cdiInstance.get();
        }
    }

    private static QueryAdapter getQueryAdapter(Connection connection) throws SQLException {
        QueryAdapter queryAdapter;
        String productName = connection.getMetaData().getDatabaseProductName();
        if (productName.contains("Oracle")) {
            queryAdapter = new OracleQueryAdapter();
        } else if (productName.contains("MariaDB")) {
            queryAdapter = new MariaDBQueryAdapter();
        } else if (productName.contains("PostgreSQL") || productName.contains("MySQL")) {
            queryAdapter = new LimitQueryAdapter();
        } else {
            Set<String> keywords = new HashSet<>(asList(connection.getMetaData().getSQLKeywords().split(",")));
            if (keywords.contains("skip") && keywords.contains("locked")) {
                queryAdapter = new SkipLockedQueryAdapter();
            } else {
                queryAdapter = new SimpleQueryAdapter();
            }
        }
        LOGGER.debug(() -> "DB '" + productName + "' uses " + queryAdapter.getClass().getSimpleName());
        return queryAdapter;
    }

    private static class SimpleQueryAdapter implements QueryAdapter {
        @Override
        public String fixLimits(String sql) {
            return sql;
        }

        @Override
        public String addSkipLocked(String sql) {
            return sql;
        }
    }

    private static class SkipLockedQueryAdapter extends SimpleQueryAdapter {
        @Override
        public String addSkipLocked(String sql) {
            return sql.replace("FOR UPDATE", "FOR UPDATE SKIP LOCKED");
        }
    }

    private static class OracleQueryAdapter extends SkipLockedQueryAdapter {
        @Override
        public String fixLimits(String sql) {
            return sql.replaceAll("\\{LIMIT ([^}]+)\\}", "AND rownum <= $1");
        }
    }

    private static class MariaDBQueryAdapter extends SimpleQueryAdapter {
        @Override
        public String fixLimits(String sql) {
            return sql.replaceAll("\\{LIMIT ([^}]+)\\}", "LIMIT $1");
        }
    }

    private static class LimitQueryAdapter extends SkipLockedQueryAdapter {
        @Override
        public String fixLimits(String sql) {
            return sql.replaceAll("\\{LIMIT ([^}]+)\\}", "LIMIT $1");
        }
    }

}
