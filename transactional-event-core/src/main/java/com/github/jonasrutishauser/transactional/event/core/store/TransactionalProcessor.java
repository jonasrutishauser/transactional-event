package com.github.jonasrutishauser.transactional.event.core.store;

import static javax.transaction.Transactional.TxType.MANDATORY;

import java.util.Properties;

import javax.enterprise.context.Dependent;
import javax.transaction.Transactional;

import com.github.jonasrutishauser.transactional.event.api.context.ContextualProcessor;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;

@Dependent
class TransactionalProcessor implements ContextualProcessor {

    @Override
    @Transactional(MANDATORY)
    public void process(String id, String type, Properties context, String payload, Handler handler) {
        handler.handle(payload);
    }

}
