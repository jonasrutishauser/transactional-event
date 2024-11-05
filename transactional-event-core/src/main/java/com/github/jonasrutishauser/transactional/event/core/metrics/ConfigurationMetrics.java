package com.github.jonasrutishauser.transactional.event.core.metrics;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;
import static org.eclipse.microprofile.metrics.MetricUnits.NONE;
import static org.eclipse.microprofile.metrics.MetricUnits.SECONDS;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.core.cdi.Startup;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped // needed for gauges
class ConfigurationMetrics implements Startup {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Configuration configuration;

    ConfigurationMetrics() {
        this(null);
    }

    @Inject
    ConfigurationMetrics(Configuration configuration) {
        this.configuration = configuration;
    }

    void init(@Observes @Priority(LIBRARY_BEFORE) @Initialized(ApplicationScoped.class) Object init) {
        LOGGER.debug("initialized");
    }

    @Gauge(name = "com.github.jonasrutishauser.transaction.event.all.in.use.interval",
            description = "Interval between lookups for events to process when maxConcurrentDispatching is reached",
            unit = MILLISECONDS, absolute = true)
    int getAllInUseInterval() {
        return configuration.getAllInUseInterval();
    }

    @Gauge(name = "com.github.jonasrutishauser.transaction.event.max.dispatch.interval",
            description = "Maximum interval between lookups for events to process", unit = SECONDS, absolute = true)
    int getMaxDispatchInterval() {
        return configuration.getMaxDispatchInterval();
    }

    @Gauge(name = "com.github.jonasrutishauser.transaction.event.initial.dispatch.interval",
            description = "Initial interval between lookups for events to process", unit = SECONDS, absolute = true)
    int getInitialDispatchInterval() {
        return configuration.getInitialDispatchInterval();
    }

    @Gauge(name = "com.github.jonasrutishauser.transaction.event.max.aquire",
            description = "Maximum number of events aquired per query", unit = NONE, absolute = true)
    int getMaxAquire() {
        return configuration.getMaxAquire();
    }

    @Gauge(name = "com.github.jonasrutishauser.transaction.event.max.concurrent.dispatching",
            description = "Maximum number of dispatched events being processed concurrently", unit = NONE,
            absolute = true)
    int getMaxConcurrentDispatching() {
        return configuration.getMaxConcurrentDispatching();
    }

}
