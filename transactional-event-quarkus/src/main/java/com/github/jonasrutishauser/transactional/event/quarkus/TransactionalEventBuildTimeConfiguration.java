package com.github.jonasrutishauser.transactional.event.quarkus;

import com.github.jonasrutishauser.transactional.event.api.Configuration;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.transactional.event")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface TransactionalEventBuildTimeConfiguration {
    /**
     * Name of the event store table.
     */
    @WithDefault(Configuration.DEFAULT_TABLE_NAME)
    @WithName("table")
    String tableName();
}
