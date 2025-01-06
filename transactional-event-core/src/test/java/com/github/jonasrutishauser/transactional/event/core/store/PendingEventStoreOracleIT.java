package com.github.jonasrutishauser.transactional.event.core.store;

import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import oracle.jdbc.pool.OracleDataSource;

@Testcontainers
class PendingEventStoreOracleIT extends PendingEventStoreTest {
    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:slim-faststart");

    @Override
    protected DataSource getDataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        Properties properties = new Properties();
        properties.setProperty("oracle.jdbc.ReadTimeout", "10000");
        dataSource.setConnectionProperties(properties);
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
