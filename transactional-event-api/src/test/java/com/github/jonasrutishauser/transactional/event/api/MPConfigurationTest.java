package com.github.jonasrutishauser.transactional.event.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class MPConfigurationTest {

    @Test
    void defaultValues() {
        Configuration testee = new MPConfiguration();

        assertEquals(Configuration.DEFAULT_ALL_IN_USE_INTERVAL, testee.getAllInUseInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL, testee.getMaxDispatchInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL / 2, testee.getInitialDispatchInterval());
        assertEquals(Configuration.DEFAULT_TABLE_NAME, testee.getTableName());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxAquire());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxConcurrentDispatching());
    }

    @Test
    void configuredAllInUseInterval() {
        MPConfiguration testee = new MPConfiguration();
        testee.allInUseInterval = Optional.of(Integer.valueOf(42));

        assertEquals(42, testee.getAllInUseInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL, testee.getMaxDispatchInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL / 2, testee.getInitialDispatchInterval());
        assertEquals(Configuration.DEFAULT_TABLE_NAME, testee.getTableName());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxAquire());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxConcurrentDispatching());
    }

    @Test
    void configuredMaxDispatcherInterval() {
        MPConfiguration testee = new MPConfiguration();
        testee.maxDispatchInterval = Optional.of(Integer.valueOf(13));

        assertEquals(Configuration.DEFAULT_ALL_IN_USE_INTERVAL, testee.getAllInUseInterval());
        assertEquals(13, testee.getMaxDispatchInterval());
        assertEquals(6, testee.getInitialDispatchInterval());
        assertEquals(Configuration.DEFAULT_TABLE_NAME, testee.getTableName());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxAquire());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxConcurrentDispatching());
    }

    @Test
    void configuredInitialDispatchInterval() {
        MPConfiguration testee = new MPConfiguration();
        testee.initialDispatchInterval = Optional.of(Integer.valueOf(42));

        assertEquals(Configuration.DEFAULT_ALL_IN_USE_INTERVAL, testee.getAllInUseInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL, testee.getMaxDispatchInterval());
        assertEquals(42, testee.getInitialDispatchInterval());
        assertEquals(Configuration.DEFAULT_TABLE_NAME, testee.getTableName());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxAquire());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxConcurrentDispatching());
    }

    @Test
    void configuredTableName() {
        MPConfiguration testee = new MPConfiguration();
        testee.tableName = Optional.of("someTable");

        assertEquals(Configuration.DEFAULT_ALL_IN_USE_INTERVAL, testee.getAllInUseInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL, testee.getMaxDispatchInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL / 2, testee.getInitialDispatchInterval());
        assertEquals("someTable", testee.getTableName());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxAquire());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxConcurrentDispatching());
    }

    @Test
    void configuredMaxAquire() {
        MPConfiguration testee = new MPConfiguration();
        testee.maxAquire = Optional.of(Integer.valueOf(7));

        assertEquals(Configuration.DEFAULT_ALL_IN_USE_INTERVAL, testee.getAllInUseInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL, testee.getMaxDispatchInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL / 2, testee.getInitialDispatchInterval());
        assertEquals(Configuration.DEFAULT_TABLE_NAME, testee.getTableName());
        assertEquals(7, testee.getMaxAquire());
        assertEquals(Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING, testee.getMaxConcurrentDispatching());
    }

    @Test
    void configuredMaxConcurrentDispatching() {
        MPConfiguration testee = new MPConfiguration();
        testee.maxConcurrentDispatching = Optional.of(Integer.valueOf(7));

        assertEquals(Configuration.DEFAULT_ALL_IN_USE_INTERVAL, testee.getAllInUseInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL, testee.getMaxDispatchInterval());
        assertEquals(Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL / 2, testee.getInitialDispatchInterval());
        assertEquals(Configuration.DEFAULT_TABLE_NAME, testee.getTableName());
        assertEquals(7, testee.getMaxAquire());
        assertEquals(7, testee.getMaxConcurrentDispatching());
    }

}
