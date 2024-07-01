package com.github.jonasrutishauser.transactional.event.quarkus;

import com.github.jonasrutishauser.transactional.event.api.Configuration;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(prefix = "transactional.event", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class TransactionalEventBuildTimeConfiguration {
    /**
     * Name of the event store table.
     */
    @ConfigItem(name = "table", defaultValue = Configuration.DEFAULT_TABLE_NAME)
    public String tableName;
}
