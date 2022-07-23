package com.github.jonasrutishauser.transactional.event.api.context;

import java.util.Properties;

import com.github.jonasrutishauser.transactional.event.api.handler.Handler;

public interface ContextualProcessor {
    void process(String id, String type, Properties context, String payload, Handler handler);
}
