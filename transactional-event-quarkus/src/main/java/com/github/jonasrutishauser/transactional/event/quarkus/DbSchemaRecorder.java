package com.github.jonasrutishauser.transactional.event.quarkus;

import java.util.List;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class DbSchemaRecorder {

    public void reset(List<String> statements) {
        InstanceHandle<DbSchema> schemaHandle = Arc.container().instance(DbSchema.class);
        if (!schemaHandle.isAvailable()) {
            return;
        }
        DbSchema dbSchema = schemaHandle.get();
        dbSchema.setStatements(statements);
        dbSchema.reset();
    }

}
