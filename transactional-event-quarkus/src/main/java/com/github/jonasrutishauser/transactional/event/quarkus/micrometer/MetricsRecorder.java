package com.github.jonasrutishauser.transactional.event.quarkus.micrometer;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MetricsRecorder {

    public void addCounter(Class<?> beanClass, String methodName, String metricName, String description) {
        Arc.container().instance(MetricsRegistry.class).get().addCounter(beanClass, methodName, metricName, description);
    }

    public void addConcurrentGauge(Class<?> beanClass, String methodName, String metricName, String description) {
        Arc.container().instance(MetricsRegistry.class).get().addConcurrentGauge(beanClass, methodName, metricName, description);
    }

}
