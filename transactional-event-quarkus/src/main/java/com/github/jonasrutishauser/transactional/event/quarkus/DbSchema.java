package com.github.jonasrutishauser.transactional.event.quarkus;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.github.jonasrutishauser.transactional.event.api.Events;

import jakarta.inject.Singleton;

@Singleton
public class DbSchema {

    private final DataSource datasource;

    private List<String> statements = new ArrayList<>();

    DbSchema(@Events DataSource datasource) {
        this.datasource = datasource;
    }

    void reset() {
        for (String item : statements) {
            try (Connection connection = datasource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(item);
            } catch (SQLException e) {
                if (!item.startsWith("DROP")) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    void setStatements(List<String> statements) {
        this.statements = statements;
    }
}
