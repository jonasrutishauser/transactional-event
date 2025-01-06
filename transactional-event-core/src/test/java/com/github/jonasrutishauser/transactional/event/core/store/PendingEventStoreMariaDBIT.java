package com.github.jonasrutishauser.transactional.event.core.store;

import javax.sql.DataSource;

import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PendingEventStoreMariaDBIT extends PendingEventStoreTest {
    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb");

    @Override
    protected DataSource getDataSource() throws Exception {
        MariaDbDataSource dataSource = new MariaDbDataSource();
        dataSource.setUrl(mariadb.getJdbcUrl());
        dataSource.setUser(mariadb.getUsername());
        dataSource.setPassword(mariadb.getPassword());
        return dataSource;
    }
}
