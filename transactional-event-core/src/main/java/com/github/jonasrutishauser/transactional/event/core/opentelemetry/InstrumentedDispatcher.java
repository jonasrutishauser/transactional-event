package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import static com.github.jonasrutishauser.transactional.event.core.opentelemetry.Instrumenter.EXCEPTION_ESCAPED;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.store.Dispatcher;
import com.github.jonasrutishauser.transactional.event.core.store.EventsPublished;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Decorator
@Priority(LIBRARY_BEFORE)
public abstract class InstrumentedDispatcher implements Dispatcher {

    private final Dispatcher delegate;

    private final Tracer tracer;
    private final String lockOwnerId;

    @Inject
    InstrumentedDispatcher(@Delegate @Any Dispatcher delegate, @Events Tracer tracer,
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
