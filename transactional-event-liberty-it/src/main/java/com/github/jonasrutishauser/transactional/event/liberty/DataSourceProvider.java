package com.github.jonasrutishauser.transactional.event.liberty;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import javax.sql.DataSource;

import com.github.jonasrutishauser.transactional.event.api.Events;

@Dependent
public class DataSourceProvider {

    @Events
    @Produces
    @Resource(lookup = "jdbc/testDatasource")
    private DataSource datasource;

}
