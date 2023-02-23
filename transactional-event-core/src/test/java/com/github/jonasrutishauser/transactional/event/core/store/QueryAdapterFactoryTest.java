package com.github.jonasrutishauser.transactional.event.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import jakarta.enterprise.inject.Instance;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.github.jonasrutishauser.transactional.event.api.store.QueryAdapter;

class QueryAdapterFactoryTest {

    @SuppressWarnings("unchecked")
    private Instance<QueryAdapter> instance = mock(Instance.class);

    @Test
    void proxyConstructor() {
        assertNotNull(new QueryAdapterFactory());
    }

    @Test
    void userDefinedAdapter() {
        QueryAdapter adapter = mock(QueryAdapter.class);
        when(instance.get()).thenReturn(adapter);

        QueryAdapterFactory testee = new QueryAdapterFactory(instance, null);
        QueryAdapter result = testee.getQueryAdapter();

        assertSame(adapter, result);
    }

    @Test
    void invalidDataSource() throws SQLException {
        doReturn(Boolean.TRUE).when(instance).isUnsatisfied();
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(SQLException.class);

        assertThrows(IllegalStateException.class, () -> new QueryAdapterFactory(instance, dataSource));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({//
            "'PostgreSQL',' SKIP LOCKED','LIMIT 42'", //
            "'MySQL',' SKIP LOCKED','LIMIT 42'", //
            "'MariaDB','','LIMIT 42'", //
            "'Oracle',' SKIP LOCKED','AND rownum <= 42'"})
    void dataSource(String databaseProductName, String skipLocked, String limit) throws SQLException {
        doReturn(Boolean.TRUE).when(instance).isUnsatisfied();
        DataSource dataSource = createDataSource(databaseProductName, "");

        QueryAdapterFactory testee = new QueryAdapterFactory(instance, dataSource);
        QueryAdapter queryAdapter = testee.getQueryAdapter();

        assertEquals("foo FOR UPDATE" + skipLocked, queryAdapter.addSkipLocked("foo FOR UPDATE"));
        assertEquals("foo " + limit + " bar", queryAdapter.fixLimits("foo {LIMIT 42} bar"));
    }

    @Test
    void simpleDataSource() throws SQLException {
        doReturn(Boolean.TRUE).when(instance).isUnsatisfied();
        DataSource dataSource = createDataSource("testDB", "test,foo,bar,skip");

        QueryAdapterFactory testee = new QueryAdapterFactory(instance, dataSource);
        QueryAdapter queryAdapter = testee.getQueryAdapter();

        assertEquals("foo FOR UPDATE", queryAdapter.addSkipLocked("foo FOR UPDATE"));
        assertEquals("foo {LIMIT 42} bar", queryAdapter.fixLimits("foo {LIMIT 42} bar"));
    }

    @Test
    void skipLockedDataSource() throws SQLException {
        doReturn(Boolean.TRUE).when(instance).isUnsatisfied();
        DataSource dataSource = createDataSource("testDB", "test,skip,foo,bar,locked");

        QueryAdapterFactory testee = new QueryAdapterFactory(instance, dataSource);
        QueryAdapter queryAdapter = testee.getQueryAdapter();

        assertEquals("foo FOR UPDATE SKIP LOCKED", queryAdapter.addSkipLocked("foo FOR UPDATE"));
        assertEquals("foo {LIMIT 42} bar", queryAdapter.fixLimits("foo {LIMIT 42} bar"));
    }

    private DataSource createDataSource(String databaseProductName, String sqlKeywords) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(databaseProductName);
        when(metaData.getSQLKeywords()).thenReturn(sqlKeywords);
        return dataSource;
    }

}
