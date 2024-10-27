package com.github.jonasrutishauser.transactional.event.quarkus.deployment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.jandex.DotName;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.api.MPConfiguration;
import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;
import com.github.jonasrutishauser.transactional.event.core.concurrent.DefaultEventExecutor;
import com.github.jonasrutishauser.transactional.event.core.defaults.DefaultConcurrencyProvider;
import com.github.jonasrutishauser.transactional.event.core.metrics.MetricsEventObserver;
import com.github.jonasrutishauser.transactional.event.core.serialization.JaxbSerialization;
import com.github.jonasrutishauser.transactional.event.core.serialization.JsonbSerialization;
import com.github.jonasrutishauser.transactional.event.quarkus.DbSchemaRecorder;
import com.github.jonasrutishauser.transactional.event.quarkus.TransactionalEventBuildTimeConfiguration;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.common.runtime.DatabaseKind;
import io.quarkus.datasource.deployment.spi.DefaultDataSourceDbKindBuildItem;
import io.quarkus.datasource.runtime.DataSourcesBuildTimeConfig;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;

public class TransactionalEventExtensionProcessor {

    private static final String FEATURE = "transactional-event";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void excludedTypes(BuildProducer<ExcludedTypeBuildItem> excludeProducer) {
        excludeProducer.produce(new ExcludedTypeBuildItem(DefaultConcurrencyProvider.class.getName()));
        excludeProducer.produce(new ExcludedTypeBuildItem(DefaultEventExecutor.class.getName()));
        excludeProducer.produce(new ExcludedTypeBuildItem(Configuration.class.getName()));
        excludeProducer.produce(new ExcludedTypeBuildItem(MPConfiguration.class.getName()));
    }

    @BuildStep
    void excludeFromBeansXml(Capabilities capabilities, Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<ExcludedTypeBuildItem> excludeProducer) {
        if (capabilities.isMissing(Capability.JAXB)) {
            excludeProducer.produce(new ExcludedTypeBuildItem(JaxbSerialization.class.getName()));
        }
        if (capabilities.isMissing(Capability.JSONB)) {
            excludeProducer.produce(new ExcludedTypeBuildItem(JsonbSerialization.class.getName()));
        }
        if (metricsCapability.isEmpty()) {
            excludeProducer.produce(new ExcludedTypeBuildItem(MetricsEventObserver.class.getName()));
        }
        if (capabilities.isMissing(Capability.OPENTELEMETRY_TRACER)) {
            excludeProducer.produce(
                    new ExcludedTypeBuildItem("com.github.jonasrutishauser.transactional.event.core.opentelemetry.*"));
        }
    }

    @BuildStep
    UnremovableBeanBuildItem ensureEventHandlersAreNotRemoved() {
        return new UnremovableBeanBuildItem(beanInfo -> beanInfo.hasType(DotName.createSimple(Handler.class))
                && beanInfo.getQualifier(DotName.createSimple(EventHandler.class)).isPresent());
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(BeanContainerBuildItem.class)
    @BuildStep(onlyIfNot = IsNormal.class)
    ServiceStartBuildItem initDb(TransactionalEventBuildTimeConfiguration configuration,
            DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            List<DefaultDataSourceDbKindBuildItem> installedDrivers, DbSchemaRecorder recorder) {
        List<String> statements = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getClass().getResourceAsStream("/transactional-event-tables"
                        + getDbKindSuffix(dataSourcesBuildTimeConfig, installedDrivers) + ".sql")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ';') {
                    statements.add(builder.substring(0, builder.length() - 1).trim()
                            .replace(Configuration.DEFAULT_TABLE_NAME, configuration.tableName()));
                    builder.setLength(0);
                } else {
                    builder.append('\n');
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        recorder.reset(statements);
        return new ServiceStartBuildItem(FEATURE);
    }

    private String getDbKindSuffix(DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            List<DefaultDataSourceDbKindBuildItem> installedDrivers) {
        Optional<String> dbKind = dataSourcesBuildTimeConfig.dataSources().get(DataSourceUtil.DEFAULT_DATASOURCE_NAME)
                .dbKind().or(installedDrivers.stream().map(DefaultDataSourceDbKindBuildItem::getDbKind)::findFirst);
        if (dbKind.isPresent()) {
            if (DatabaseKind.isMySQL(dbKind.get()) || DatabaseKind.isMariaDB(dbKind.get())) {
                return "-mysql";
            }
            if (DatabaseKind.isOracle(dbKind.get())) {
                return "-oracle";
            }
        }
        return "";
    }

}
