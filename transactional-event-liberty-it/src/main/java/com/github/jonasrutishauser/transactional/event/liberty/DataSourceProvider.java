package com.github.jonasrutishauser.transactional.event.liberty;

import javax.annotation.Resource;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.sql.DataSource;

import com.github.jonasrutishauser.transactional.event.api.Events;

@Dependent
public class DataSourceProvider {

    @Events
    @Produces
    @Resource(lookup = "jdbc/testDatasource")
    private DataSource datasource;

}
