package com.github.jonasrutishauser.transactional.event.api;

import jakarta.enterprise.context.Dependent;

@Dependent
public class Configuration {

    public static final int DEFAULT_ALL_IN_USE_INTERVAL = 100;
    public static final int DEFAULT_MAX_DISPATCHER_INTERVAL = 60;
    public static final String DEFAULT_TABLE_NAME = "event_store";
    public static final int DEFAULT_MAX_CONCURRENT_DISPATCHING = 10;

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
        return getMaxConcurrentDispatching();
    }

    public int getMaxConcurrentDispatching() {
        return DEFAULT_MAX_CONCURRENT_DISPATCHING;
    }

}
