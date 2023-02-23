package com.github.jonasrutishauser.transactional.event.core.defaults;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import com.github.jonasrutishauser.transactional.event.api.Events;

@Dependent
public class DefaultConcurrencyProvider {

    @Events
    @Produces
    @Resource
    private ManagedScheduledExecutorService executorService;

}
