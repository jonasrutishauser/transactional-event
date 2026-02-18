package com.github.jonasrutishauser.transactional.event.quarkus.micrometer;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@ConcurrentGauge
@Interceptor
@Priority(LIBRARY_BEFORE + 10)
public class ConcurrentGaugeInterceptor {

    private final MetricsRegistry metricsRegistry;

    @Inject
    ConcurrentGaugeInterceptor(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @AroundInvoke
    Object countedMethod(InvocationContext context) throws Exception {
        AtomicLong gauge = metricsRegistry.getConcurrentGauge(context.getMethod().getDeclaringClass(),
                context.getMethod().getName());
        if (gauge != null) {
            gauge.incrementAndGet();
        }
        try {
            return context.proceed();
        } finally {
            if (gauge != null) {
                gauge.decrementAndGet();
            }
        }
    }

}
