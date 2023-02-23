package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import com.github.jonasrutishauser.transactional.event.api.Events;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

@Dependent
class Instrumenter {
    private static final String NAME = Instrumenter.class.getPackage().getName().replace(".core.opentelemetry", "");

    private Instrumenter() {
    }

    @Events
    @Produces
    static TextMapPropagator getPropagator(@Events OpenTelemetry openTelemetry) {
        return openTelemetry.getPropagators().getTextMapPropagator();
    }

    @Events
    @Produces
    static Tracer getTracer(@Events OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(NAME, Instrumenter.class.getPackage().getImplementationVersion());
    }

    @Events
    @Produces
    static OpenTelemetry getOpenTelemetry(Instance<OpenTelemetry> defaultOpenTelemetry) {
        if (defaultOpenTelemetry.isResolvable()) {
            // This should be the case if microprofile-telemetry is available
            return defaultOpenTelemetry.get();
        }
        return GlobalOpenTelemetry.get();
    }
}
