package com.github.jonasrutishauser.transactional.event.core.concurrent;

import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

public interface EventExecutor extends Executor {
    Task schedule(Runnable command, long minInterval, LongSupplier intervalInMillis);

    interface Task {
        void cancel();
    }
}
