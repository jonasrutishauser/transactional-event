package com.github.jonasrutishauser.transactional.event.quarkus.micrometer;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Counted
@Interceptor
@Priority(LIBRARY_BEFORE + 10)
public class CountedInterceptor {

    private final MetricsRegistry metricsRegistry;

    @Inject
    CountedInterceptor(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @AroundInvoke
    Object countedMethod(InvocationContext context) throws Exception {
        metricsRegistry.incrementCounter(context.getMethod().getDeclaringClass(), context.getMethod().getName());
        return context.proceed();
    }

}
