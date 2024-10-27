package com.github.jonasrutishauser.transactional.event.quarkus;

import com.github.jonasrutishauser.transactional.event.api.Configuration;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.transactional.event")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TransactionalEventConfiguration {
    /**
     * Time in ms to wait before next dispatching cycle is started if max concurrent
     * dispatching threads are in use.
     */
    @WithDefault("" + Configuration.DEFAULT_ALL_IN_USE_INTERVAL)
    int allInUseInterval();

    /**
     * Max seconds to wait before a new check is made for unprocessed events.
     */
    @WithDefault("" + Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL)
    int maxDispatchInterval();

    /**
     * Initial seconds to wait before a new check is made for unprocessed events.
     */
    @WithDefault("" + Configuration.DEFAULT_MAX_DISPATCHER_INTERVAL / 2)
    int initialDispatchInterval();

    /**
     * Number of unprocessed events to aquire in one transaction.
     */
    @WithDefault("" + Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING)
    int maxAquire();

    /**
     * Max number of events to process concurrently.
     */
    @WithDefault("" + Configuration.DEFAULT_MAX_CONCURRENT_DISPATCHING)
    int maxConcurrentDispatching();
}
