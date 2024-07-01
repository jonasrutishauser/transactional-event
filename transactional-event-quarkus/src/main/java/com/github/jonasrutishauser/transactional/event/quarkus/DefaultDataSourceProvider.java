package com.github.jonasrutishauser.transactional.event.quarkus;

import javax.sql.DataSource;

import com.github.jonasrutishauser.transactional.event.api.Events;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Dependent
class DefaultDataSourceProvider {

    private final DataSource datasource;

    @Inject
    DefaultDataSourceProvider(DataSource datasource) {
        this.datasource = datasource;
    }

    @Events
    @Produces
    @DefaultBean
    DataSource getDataSource() {
        return datasource;
    }

}
