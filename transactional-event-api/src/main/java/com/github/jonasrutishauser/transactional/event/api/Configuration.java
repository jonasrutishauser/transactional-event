package com.github.jonasrutishauser.transactional.event.api;

import javax.enterprise.context.Dependent;

@Dependent
public class Configuration {

    protected static final int DEFAULT_ALL_IN_USE_INTERVAL = 100;
    protected static final int DEFAULT_MAX_DISPATCHER_INTERVAL = 60;
    protected static final String DEFAULT_TABLE_NAME = "event_store";
    protected static final int DEFAULT_MAX_AQUIRE = 10;

    public int getAllInUseInterval() {
        return DEFAULT_ALL_IN_USE_INTERVAL;
    }

    public int getMaxDispatchInterval() {
        return DEFAULT_MAX_DISPATCHER_INTERVAL;
    }

    public int getInitialDispatchInterval() {
        return getMaxDispatchInterval() / 2;
    }

    public String getTableName() {
        return DEFAULT_TABLE_NAME;
    }

    public int getMaxAquire() {
        return DEFAULT_MAX_AQUIRE;
    }

    public int getMaxConcurrentDispatching() {
        return getMaxAquire();
    }

}
