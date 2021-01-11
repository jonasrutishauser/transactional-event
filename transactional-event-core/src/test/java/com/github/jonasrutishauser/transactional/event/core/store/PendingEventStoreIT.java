package com.github.jonasrutishauser.transactional.event.core.store;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.mariadb.jdbc.MariaDbDataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import oracle.jdbc.pool.OracleDataSource;

class PendingEventStoreIT {

    @Testcontainers
    static class PostgreSQLIT extends PendingEventStoreTest {
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

    @Testcontainers
    static class MariaDBIT extends PendingEventStoreTest {
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

    @Testcontainers
    static class MySQLIT extends PendingEventStoreTest {
        private static final class MySQLContainerUsingMariaDbDriver
                extends MySQLContainer<MySQLContainerUsingMariaDbDriver> {
            private MySQLContainerUsingMariaDbDriver() {
                super("mysql");
            }

            @Override
            public String getDriverClassName() {
                return org.mariadb.jdbc.Driver.class.getName();
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

    @Testcontainers
    static class OracleIT extends PendingEventStoreTest {

        @Container
        static OracleContainer oracle = new OracleContainer("wnameless/oracle-xe-11g-r2");

        @Override
        protected DataSource getDataSource() throws SQLException {
            OracleDataSource dataSource = new OracleDataSource();
            dataSource.setURL(oracle.getJdbcUrl());
            dataSource.setUser(oracle.getUsername());
            dataSource.setPassword(oracle.getPassword());
            return dataSource;
        }

        @Override
        protected String ddl() {
            return "/table-oracle.sql";
        }
    }

}
