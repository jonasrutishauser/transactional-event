package com.github.jonasrutishauser.transactional.event.api;

public interface EventPublisher {
    
    void publish(Object event);

}
