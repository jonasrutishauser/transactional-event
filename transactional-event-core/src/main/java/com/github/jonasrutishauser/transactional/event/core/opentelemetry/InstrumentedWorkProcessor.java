package com.github.jonasrutishauser.transactional.event.core.opentelemetry;

import static com.github.jonasrutishauser.transactional.event.core.opentelemetry.Instrumenter.EXCEPTION_ESCAPED;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import java.util.concurrent.Callable;

import com.github.jonasrutishauser.transactional.event.api.Events;
import com.github.jonasrutishauser.transactional.event.core.store.WorkProcessor;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Decorator
@Priority(LIBRARY_BEFORE)
public class InstrumentedWorkProcessor implements WorkProcessor {

    private final WorkProcessor delegate;

    private final Tracer tracer;
    private final String lockOwnerId;

    @Inject
    InstrumentedWorkProcessor(@Delegate @Any WorkProcessor delegate, @Events Tracer tracer,
            @Named("lockOwner.id") String lockOwnerId) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.lockOwnerId = lockOwnerId;
    }

    @Override
    public Callable<Boolean> get(String eventId) {
        Callable<Boolean> processor = delegate.get(eventId);
        return Context.current().wrap(() -> {
            Span span = tracer.spanBuilder("transactional-event process") //
                    .setSpanKind(INTERNAL) //
                    .setAttribute("messaging.system", "transactional-event") //
                    .setAttribute("messaging.message_id", eventId) //
                    .setAttribute("messaging.operation", "process") //
                    .setAttribute("messaging.consumer_id", lockOwnerId) //
                    .startSpan();
            try (Scope unused = span.makeCurrent()) {
                return processor.call();
            } catch (RuntimeException e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.recordException(e, Attributes.of(EXCEPTION_ESCAPED, true));
                throw e;
            } finally {
                span.end();
            }
        });
    }

}
