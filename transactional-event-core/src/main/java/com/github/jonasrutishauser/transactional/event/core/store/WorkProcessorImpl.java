package com.github.jonasrutishauser.transactional.event.core.store;

import java.util.concurrent.Callable;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class WorkProcessorImpl implements WorkProcessor {

    private final Worker worker;
    
    public WorkProcessorImpl() {
        this(null);
    }

    @Inject
    public WorkProcessorImpl(Worker worker) {
        this.worker = worker;
    }

    @Override
    public Callable<Boolean> get(String eventId) {
        return () -> worker.process(eventId);
    }

}
