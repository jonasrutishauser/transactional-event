package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import javax.annotation.Priority;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.inject.Named;

import com.github.jonasrutishauser.transactional.event.core.store.Dispatcher;
import com.github.jonasrutishauser.transactional.event.core.store.EventsPublished;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@Decorator
@Priority(LIBRARY_BEFORE)
public class InstrumentedScheduler implements Dispatcher {

    private final Dispatcher delegate;

    private final Tracer tracer;
    private final String lockOwnerId;

    InstrumentedScheduler() {
        this(null, null, null);
    }

    @Inject
    InstrumentedScheduler(@Delegate @Any Dispatcher delegate, Tracer tracer,
            @Named("lockOwner.id") String lockOwnerId) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.lockOwnerId = lockOwnerId;
    }

    @Override
    public void schedule() {
        tracedReceive(delegate::schedule);
    }

    @Override
    public void processDirect(EventsPublished events) {
        tracedReceive(() -> delegate.processDirect(events));
    }

    @Override
    public Runnable processor(String eventId) {
        Runnable processor = delegate.processor(eventId);
        return Context.current().wrap(() -> {
            Span span = tracer.spanBuilder("transactional-event process") //
                    .setSpanKind(INTERNAL) //
                    .setAttribute("messaging.system", "transactional-event") //
                    .setAttribute("messaging.message_id", eventId) //
                    .setAttribute("messaging.operation", "process") //
                    .setAttribute("messaging.consumer_id", lockOwnerId) //
                    .startSpan();
            try (Scope unused = span.makeCurrent()) {
                processor.run();
            } finally {
                span.end();
            }
        });
    }

    private void tracedReceive(Runnable runnable) {
        Span span = tracer.spanBuilder("transactional-event receive") //
                .setSpanKind(CONSUMER) //
                .setAttribute("messaging.system", "transactional-event") //
                .setAttribute("messaging.operation", "receive") //
                .setAttribute("messaging.consumer_id", lockOwnerId) //
                .startSpan();
        try (Scope unused = span.makeCurrent()) {
            runnable.run();
        } finally {
            span.end();
        }
    }

}
