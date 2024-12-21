package com.github.jonasrutishauser.transactional.event.api;

import java.time.temporal.TemporalAmount;

public interface EventPublisher {

    void publish(Object event);

    void publishDelayed(Object event, TemporalAmount delay);

}
