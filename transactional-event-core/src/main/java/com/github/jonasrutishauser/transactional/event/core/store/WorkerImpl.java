package com.github.jonasrutishauser.transactional.event.core.store;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Dependent
class WorkerImpl implements Worker {

    private static final Logger LOGGER = LogManager.getLogger();

    private final TransactionalWorker transactional;
    
    @Inject
    WorkerImpl(TransactionalWorker transactional) {
        this.transactional = transactional;
    }

    @ActivateRequestContext
    public boolean process(String eventId) {
        try {
            LOGGER.debug("processing event with id '{}'", eventId);
            transactional.process(eventId);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to process event with id '{}'", eventId, e);
            transactional.processFailed(eventId);
            return false;
        }
    }

}
