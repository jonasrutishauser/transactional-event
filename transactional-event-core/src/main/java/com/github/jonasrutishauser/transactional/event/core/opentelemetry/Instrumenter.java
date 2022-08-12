package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

@Dependent
class Instrumenter {
    private static final String NAME = Instrumenter.class.getPackage().getName().replace(".core.opentelemetry", "");

    private Instrumenter() {
    }

    @Produces
    static TextMapPropagator getPropagator(OpenTelemetry openTelemetry) {
        return openTelemetry.getPropagators().getTextMapPropagator();
    }

    @Produces
    static Tracer getTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(NAME, Instrumenter.class.getPackage().getImplementationVersion());
    }

    @Produces
    static OpenTelemetry getGlobalOpenTelemetry() {
        return GlobalOpenTelemetry.get();
    }
}
