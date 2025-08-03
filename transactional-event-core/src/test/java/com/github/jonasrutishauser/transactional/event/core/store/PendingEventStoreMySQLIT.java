package com.github.jonasrutishauser.transactional.event.core.store;

import javax.sql.DataSource;

import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PendingEventStoreMySQLIT extends PendingEventStoreTest {
    private static final class MySQLContainerUsingMariaDbDriver
            extends MySQLContainer<MySQLContainerUsingMariaDbDriver> {
        private MySQLContainerUsingMariaDbDriver() {
            super("mysql:8");
        }

        @Override
        public String getDriverClassName() {
            return org.mariadb.jdbc.Driver.class.getName();
        }

        @Override
        public String getJdbcUrl() {
            return super.getJdbcUrl().replace("jdbc:mysql", "jdbc:mariadb");
        }
    }

    @Container
    static MySQLContainer<?> mysql = new MySQLContainerUsingMariaDbDriver();

    @Override
    protected DataSource getDataSource() throws Exception {
        MariaDbDataSource dataSource = new MariaDbDataSource();
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUser(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        return dataSource;
    }

    @Override
    protected String ddl() {
        return "/table-mysql.sql";
    }
}
