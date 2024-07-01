package com.github.jonasrutishauser.transactional.event.quarkus;

import io.quarkus.arc.Arc;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.runtime.DatabaseSchemaProvider;

public class TransactionalEventSchemaProvider implements DatabaseSchemaProvider {

    @Override
    public void resetDatabase(String dbName) {
        if (DataSourceUtil.DEFAULT_DATASOURCE_NAME.equals(dbName)) {
            resetAllDatabases();
        }
    }

    @Override
    public void resetAllDatabases() {
        DbSchema provider = getProvider();
        provider.reset();
    }

    private DbSchema getProvider() {
        return Arc.container().instance(DbSchema.class).get();
    }
}
