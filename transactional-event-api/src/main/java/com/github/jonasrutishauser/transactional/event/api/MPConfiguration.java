package com.github.jonasrutishauser.transactional.event.api;

import static org.eclipse.microprofile.metrics.MetricUnits.*;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Specializes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.Gauge;

@Specializes
@ApplicationScoped // needed for gauges
public class MPConfiguration extends Configuration {

    @Inject
    @ConfigProperty(name = "transactional.event.allInUseInterval")
    Optional<Integer> allInUseInterval = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.maxDispatchInterval")
    Optional<Integer> maxDispatchInterval = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.initialDispatchInterval")
    Optional<Integer> initialDispatchInterval = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.table")
    Optional<String> tableName = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.maxAquire")
    Optional<Integer> maxAquire = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.maxConcurrentDispatching")
    Optional<Integer> maxConcurrentDispatching = Optional.empty();

    @Override
    @Gauge(name = "com.github.jonasrutishauser.transaction.event.all.in.use.interval",
            description = "Interval between lookups for events to process when maxConcurrentDispatching is reached",
            unit = MILLISECONDS, absolute = true)
    public int getAllInUseInterval() {
        return allInUseInterval.orElseGet(super::getAllInUseInterval).intValue();
    }

    @Override
    @Gauge(name = "com.github.jonasrutishauser.transaction.event.max.dispatch.interval",
            description = "Maximum interval between lookups for events to process", unit = SECONDS, absolute = true)
    public int getMaxDispatchInterval() {
        return maxDispatchInterval.orElseGet(super::getMaxDispatchInterval).intValue();
    }

    @Override
    @Gauge(name = "com.github.jonasrutishauser.transaction.event.initial.dispatch.interval",
            description = "Initial interval between lookups for events to process", unit = SECONDS, absolute = true)
    public int getInitialDispatchInterval() {
        return initialDispatchInterval.orElseGet(super::getInitialDispatchInterval).intValue();
    }

    @Override
    public String getTableName() {
        return tableName.orElseGet(super::getTableName);
    }

    @Override
    @Gauge(name = "com.github.jonasrutishauser.transaction.event.max.aquire",
            description = "Maximum number of events aquired per query", unit = NONE, absolute = true)
    public int getMaxAquire() {
        return maxAquire.orElseGet(super::getMaxAquire).intValue();
    }

    @Override
    @Gauge(name = "com.github.jonasrutishauser.transaction.event.max.concurrent.dispatching",
            description = "Maximum number of dispatched events being processed concurrently", unit = NONE,
            absolute = true)
    public int getMaxConcurrentDispatching() {
        return maxConcurrentDispatching.orElseGet(super::getMaxConcurrentDispatching).intValue();
    }

}
