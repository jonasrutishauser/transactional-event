package com.github.jonasrutishauser.transactional.event.core.store;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PendingEventStorePostgreSQLIT extends PendingEventStoreTest {
    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres");

    @Override
    protected DataSource getDataSource() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(postgresql.getJdbcUrl());
        dataSource.setUser(postgresql.getUsername());
        dataSource.setPassword(postgresql.getPassword());
        return dataSource;
    }
}
