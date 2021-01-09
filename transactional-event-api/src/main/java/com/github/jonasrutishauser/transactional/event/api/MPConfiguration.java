package com.github.jonasrutishauser.transactional.event.api;

import java.util.Optional;

import javax.enterprise.inject.Specializes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Specializes
public class MPConfiguration extends Configuration {

    @Inject
    @ConfigProperty(name = "transactional.event.maxDispatchInterval")
    private Optional<Integer> maxDispatchInterval = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.initialDispatchInterval")
    private Optional<Integer> initialDispatchInterval = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.table")
    private Optional<String> tableName = Optional.empty();

    @Inject
    @ConfigProperty(name = "transactional.event.maxAquire")
    private Optional<Integer> maxAquire = Optional.empty();

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

}
