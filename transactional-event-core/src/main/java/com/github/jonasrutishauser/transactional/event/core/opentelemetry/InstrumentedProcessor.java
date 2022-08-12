package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import java.util.Properties;

import javax.annotation.Priority;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.inject.Named;

import com.github.jonasrutishauser.transactional.event.api.context.ContextualProcessor;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

@Decorator
@Priority(LIBRARY_BEFORE)
class InstrumentedProcessor implements ContextualProcessor {

    private final ContextualProcessor delegate;

    private final Tracer tracer;
    private final TextMapPropagator propagator;
    private final String lockOwnerId;
    private final TextMapGetter<Properties> getter = new TextMapGetter<Properties>() {
        @Override
        public String get(Properties carrier, String key) {
            return carrier.getProperty(key);
        }

        @Override
        public Iterable<String> keys(Properties carrier) {
            return carrier.stringPropertyNames();
        }
    };

    InstrumentedProcessor() {
        this(null, null, null, null);
    }

    @Inject
    InstrumentedProcessor(@Delegate @Any ContextualProcessor delegate, Tracer tracer, TextMapPropagator propagator,
            @Named("lockOwner.id") String lockOwnerId) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.propagator = propagator;
        this.lockOwnerId = lockOwnerId;
    }

    @Override
    public void process(String id, String type, Properties context, String payload, Handler handler) {
        Context extractedContext = propagator.extract(Context.current(), context, getter);
        Span span = tracer.spanBuilder(type + " process") //
                .setSpanKind(CONSUMER) //
                .setAttribute("messaging.system", "transactional-event") //
                .setAttribute("messaging.destination", type) //
                .setAttribute("messaging.message_id", id) //
                .setAttribute("messaging.message_payload_size_bytes", payload.getBytes(UTF_8).length) //
                .setAttribute("messaging.operation", "process") //
                .setAttribute("messaging.consumer_id", lockOwnerId) //
                .addLink(Span.fromContext(extractedContext).getSpanContext()) //
                .startSpan();
        try (Scope unused = span.makeCurrent()) {
            delegate.process(id, type, context, payload, handler);
        } catch (RuntimeException e) {
            span.setStatus(ERROR, "Processing failed");
            span.recordException(e, Attributes.builder().put("exception.escaped", true).build());
            throw e;
        } finally {
            span.end();
        }
    }

}
