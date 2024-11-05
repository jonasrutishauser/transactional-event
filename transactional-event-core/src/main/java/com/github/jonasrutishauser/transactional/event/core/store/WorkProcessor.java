package com.github.jonasrutishauser.transactional.event.core.store;

import java.util.concurrent.Callable;

public interface WorkProcessor {
    Callable<Boolean> get(String eventId);
}
