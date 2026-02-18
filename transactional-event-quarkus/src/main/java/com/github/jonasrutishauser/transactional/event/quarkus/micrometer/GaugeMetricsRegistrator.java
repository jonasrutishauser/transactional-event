package com.github.jonasrutishauser.transactional.event.quarkus.micrometer;


import java.util.Map;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.Arc;
import jakarta.enterprise.invoke.Invoker;

class GaugeMetricsRegistrator {

    private GaugeMetricsRegistrator() {
    }

    public static void register(Map<String, Object> params) {
        var builder = Gauge.builder((String) params.get("name"), (Invoker<?, ?>) params.get("invoker"), invoker -> {
            try {
                return ((Number) invoker.invoke(null, null)).doubleValue();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to invoke gauge method", e);
            }
        }).description((String) params.get("description"));
        if (!"none".equals(params.get("unit"))) {
            builder = builder.baseUnit((String) params.get("unit"));
        }
        builder.strongReference(true).register(Arc.container().instance(MeterRegistry.class).get());
    }

}
