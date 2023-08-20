package com.github.jonasrutishauser.transactional.event.cdi.test;

import javax.sql.DataSource;

import com.github.jonasrutishauser.cdi.test.api.annotations.GlobalTestImplementation;
import com.github.jonasrutishauser.transactional.event.api.Events;

import jakarta.annotation.Resource;
import jakarta.enterprise.inject.Produces;

@GlobalTestImplementation
class DbConfiguration {
    @Events
    @Produces
    @Resource(lookup = "ds/transactionalEvents")
    private DataSource ds;
}
