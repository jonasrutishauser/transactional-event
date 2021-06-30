package com.github.jonasrutishauser.transactional.event.api;

import java.util.Optional;

import javax.enterprise.inject.Specializes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Specializes
public class MPConfiguration extends Configuration {

    @Inject
    @ConfigProperty(name = "transactional.event.maxDispatchInterval")
    Optional<Integer> maxDispatchInterval = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.initialDispatchInterval")
    Optional<Integer> initialDispatchInterval = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.table")
    Optional<String> tableName = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.maxAquire")
    Optional<Integer> maxAquire = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.maxConcurrentDispatching")
    Optional<Integer> maxConcurrentDispatching = Optional.empty();

    @Override
    public int getMaxDispatchInterval() {
        return maxDispatchInterval.orElseGet(super::getMaxDispatchInterval).intValue();
    }

    @Override
    public int getInitialDispatchInterval() {
        return initialDispatchInterval.orElseGet(super::getInitialDispatchInterval).intValue();
    }

    @Override
    public String getTableName() {
        return tableName.orElseGet(super::getTableName);
    }

    @Override
    public int getMaxAquire() {
        return maxAquire.orElseGet(super::getMaxAquire).intValue();
    }

    @Override
    public int getMaxConcurrentDispatching() {
        return maxConcurrentDispatching.orElseGet(super::getMaxConcurrentDispatching).intValue();
    }

}
