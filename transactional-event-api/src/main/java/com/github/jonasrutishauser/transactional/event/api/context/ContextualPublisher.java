package com.github.jonasrutishauser.transactional.event.api.context;

import java.util.Properties;

public interface ContextualPublisher {
    void publish(String id, String type, Properties context, String payload);
}
