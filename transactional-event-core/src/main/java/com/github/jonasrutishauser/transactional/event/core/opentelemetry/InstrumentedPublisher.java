package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;

import java.util.Properties;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.api.context.ContextualPublisher;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

@Decorator
@Priority(LIBRARY_AFTER)
class InstrumentedPublisher implements ContextualPublisher {

    private final ContextualPublisher delegate;

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    InstrumentedPublisher() {
        this(null, null, null);
    }

    @Inject
    InstrumentedPublisher(@Delegate @Any ContextualPublisher delegate, @Events Tracer tracer,
            @Events TextMapPropagator propagator) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public void publish(String id, String type, Properties context, String payload) {
        Span span = tracer.spanBuilder(type + " send") //
                .setSpanKind(PRODUCER) //
                .setAttribute("messaging.system", "transactional-event") //
                .setAttribute("messaging.destination", type) //
                .setAttribute("messaging.message_id", id) //
                .setAttribute("messaging.message_payload_size_bytes", payload.getBytes(UTF_8).length) //
                .startSpan();
        try (Scope unused = span.makeCurrent()) {
            propagator.inject(Context.current(), context, Properties::setProperty);
            delegate.publish(id, type, context, payload);
        } finally {
            span.end();
        }
    }

}
