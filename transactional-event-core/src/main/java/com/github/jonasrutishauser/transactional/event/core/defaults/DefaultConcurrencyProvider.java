package com.github.jonasrutishauser.transactional.event.core.defaults;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ContextService;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import com.github.jonasrutishauser.transactional.event.api.Events;

@Dependent
public class DefaultConcurrencyProvider {

    @Events
    @Produces
    @Resource
    private ManagedScheduledExecutorService executorService;

    @Events
    @Produces
    @Resource
    private ContextService contextService;

}
