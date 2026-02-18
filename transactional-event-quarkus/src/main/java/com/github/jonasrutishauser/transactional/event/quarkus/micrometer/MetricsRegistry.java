package com.github.jonasrutishauser.transactional.event.quarkus.micrometer;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class MetricsRegistry {

    private final MeterRegistry registry;
    private final Map<Class<?>, Map<String, Counter>> counters = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, AtomicLong>> gauges = new ConcurrentHashMap<>();

    @Inject
    MetricsRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    public void addCounter(Class<?> beanClass, String methodName, String metricName, String description) {
        counters.computeIfAbsent(beanClass, k -> new ConcurrentHashMap<>()).put(methodName,
                Counter.builder(metricName).description(description).register(registry));
    }

    public void addConcurrentGauge(Class<?> beanClass, String methodName, String metricName, String description) {
        AtomicLong gauge = new AtomicLong();
        gauges.computeIfAbsent(beanClass, k -> new ConcurrentHashMap<>()).put(methodName,
                gauge);
        Gauge.builder(metricName + ".current", gauge, AtomicLong::get).description(description).register(registry);
    }

    public void incrementCounter(Class<?> declaringClass, String method) {
        Counter counter = counters.getOrDefault(declaringClass, Collections.emptyMap()).get(method);
        if (counter != null) {
            counter.increment();
        }
    }

    public AtomicLong getConcurrentGauge(Class<?> declaringClass, String method) {
        return gauges.getOrDefault(declaringClass, Collections.emptyMap()).get(method);
    }

}
