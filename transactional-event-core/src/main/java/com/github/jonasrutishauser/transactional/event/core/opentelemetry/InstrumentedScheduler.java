package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.store.Dispatcher;
import com.github.jonasrutishauser.transactional.event.core.store.EventsPublished;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@Decorator
@Priority(LIBRARY_BEFORE)
public class InstrumentedScheduler implements Dispatcher {

    private static final AttributeKey<Boolean> EXCEPTION_ESCAPED = AttributeKey.booleanKey("exception.escaped");

    private final Dispatcher delegate;

    private final Tracer tracer;
    private final String lockOwnerId;

    InstrumentedScheduler() {
        this(null, null, null);
    }

    @Inject
    InstrumentedScheduler(@Delegate @Any Dispatcher delegate, @Events Tracer tracer,
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
            } catch (RuntimeException e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.recordException(e, Attributes.of(EXCEPTION_ESCAPED, true));
                throw e;
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
        } catch (RuntimeException e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e, Attributes.of(EXCEPTION_ESCAPED, true));
            throw e;
        } finally {
            span.end();
        }
    }

}
