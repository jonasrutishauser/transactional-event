package com.github.jonasrutishauser.transactional.event.quarkus;

import com.github.jonasrutishauser.transactional.event.api.Configuration;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class QuarkusConfiguration extends Configuration {

    private final TransactionalEventConfiguration runTimeConfiguration;
    private final TransactionalEventBuildTimeConfiguration buildTimeConfiguration;

    @Inject
    public QuarkusConfiguration(TransactionalEventConfiguration runTimeConfiguration,
            TransactionalEventBuildTimeConfiguration buildTimeConfiguration) {
        this.runTimeConfiguration = runTimeConfiguration;
        this.buildTimeConfiguration = buildTimeConfiguration;
    }

    @Override
    public int getAllInUseInterval() {
        return runTimeConfiguration.allInUseInterval();
    }

    @Override
    public int getMaxDispatchInterval() {
        return runTimeConfiguration.maxDispatchInterval();
    }

    @Override
    public int getInitialDispatchInterval() {
        return runTimeConfiguration.initialDispatchInterval();
    }

    @Override
    public String getTableName() {
        return buildTimeConfiguration.tableName();
    }

    @Override
    public int getMaxAquire() {
        return runTimeConfiguration.maxAquire();
    }

    @Override
    public int getMaxConcurrentDispatching() {
        return runTimeConfiguration.maxConcurrentDispatching();
    }

}
