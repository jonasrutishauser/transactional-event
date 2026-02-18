package com.github.jonasrutishauser.transactional.event.quarkus.deployment;

import static io.quarkus.runtime.metrics.MetricsFactory.MICROMETER;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationTransformation.TransformationContext;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import com.github.jonasrutishauser.transactional.event.api.Configuration;
import com.github.jonasrutishauser.transactional.event.api.MPConfiguration;
import com.github.jonasrutishauser.transactional.event.core.concurrent.DefaultEventExecutor;
import com.github.jonasrutishauser.transactional.event.core.defaults.DefaultConcurrencyProvider;
import com.github.jonasrutishauser.transactional.event.core.serialization.JaxbSerialization;
import com.github.jonasrutishauser.transactional.event.core.serialization.JsonbSerialization;
import com.github.jonasrutishauser.transactional.event.quarkus.DbSchema;
import com.github.jonasrutishauser.transactional.event.quarkus.DbSchemaRecorder;
import com.github.jonasrutishauser.transactional.event.quarkus.TransactionalEventBuildTimeConfiguration;
import com.github.jonasrutishauser.transactional.event.quarkus.micrometer.MetricsRecorder;

import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.InvokerFactoryBuildItem;
import io.quarkus.arc.deployment.KnownCompatibleBeanArchiveBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem.ObserverConfiguratorBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.ObserverConfigurator;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.common.runtime.DatabaseKind;
import io.quarkus.datasource.deployment.spi.DefaultDataSourceDbKindBuildItem;
import io.quarkus.datasource.runtime.DataSourcesBuildTimeConfig;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.gizmo2.desc.MethodDesc;
import jakarta.enterprise.event.Startup;
import jakarta.interceptor.Interceptor.Priority;

public class TransactionalEventExtensionProcessor {

    private static final String FEATURE = "transactional-event";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void markApiAsKnownCompatibleBeanArchive(BuildProducer<KnownCompatibleBeanArchiveBuildItem> knownCompatibleBeanArchives){
        knownCompatibleBeanArchives.produce(
                KnownCompatibleBeanArchiveBuildItem.builder("io.github.jonasrutishauser", "transactional-event-api")
                        .addReason(KnownCompatibleBeanArchiveBuildItem.Reason.SPECIALIZES_ANNOTATION).build()
        );
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
            BuildProducer<ExcludedTypeBuildItem> excludeProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBean) {
        if (capabilities.isMissing(Capability.JAXB)) {
            excludeProducer.produce(new ExcludedTypeBuildItem(JaxbSerialization.class.getName()));
        }
        if (capabilities.isMissing(Capability.JSONB)) {
            excludeProducer.produce(new ExcludedTypeBuildItem(JsonbSerialization.class.getName()));
        }
        if (metricsCapability.isEmpty()) {
            excludeProducer.produce(
                    new ExcludedTypeBuildItem("com.github.jonasrutishauser.transactional.event.core.metrics.*"));
            excludeProducer.produce(
                    new ExcludedTypeBuildItem("com.github.jonasrutishauser.transactional.event.quarkus.micrometer.*"));
        } else  {
            if (!metricsCapability.get().metricsSupported(MICROMETER)) {
                excludeProducer.produce(new ExcludedTypeBuildItem(
                        "com.github.jonasrutishauser.transactional.event.quarkus.micrometer.*"));
            }
            unremovableBean.produce(UnremovableBeanBuildItem.beanClassNames(
                    "com.github.jonasrutishauser.transactional.event.core.metrics.ConfigurationMetrics"));
        }
        if (capabilities.isMissing(Capability.OPENTELEMETRY_TRACER)) {
            excludeProducer.produce(
                    new ExcludedTypeBuildItem("com.github.jonasrutishauser.transactional.event.core.opentelemetry.*"));
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    AnnotationsTransformerBuildItem transformMetricsCountedAnnotation(
            Optional<MetricsCapabilityBuildItem> metricsCapability, MetricsRecorder recorder) {
        if (metricsCapability.stream().anyMatch(capability -> capability.metricsSupported(MICROMETER))) {
            return new AnnotationsTransformerBuildItem(AnnotationTransformation.forMethods().whenMethod(
                    method -> method.declaringClass().name().packagePrefix().startsWith("com.github.jonasrutishauser.transactional.event"))
                    .whenAnyMatch(DotName.createSimple("org.eclipse.microprofile.metrics.annotation.Counted"),
                            DotName.createSimple("org.eclipse.microprofile.metrics.annotation.ConcurrentGauge"))
                    .transform(ctx -> transformMetricsAnnotation(ctx, recorder)));
        } else {
            return null;
        }
    }

    private void transformMetricsAnnotation(TransformationContext ctx, MetricsRecorder recorder) {
        MethodInfo method = ctx.declaration().asMethod();
        ctx.annotations().stream()
                .filter(annotation -> annotation.name()
                        .packagePrefix().equals("org.eclipse.microprofile.metrics.annotation"))
                .findAny().ifPresent(annotation -> {
                    String metricType = annotation.name().withoutPackagePrefix();
                    ctx.remove(annotation::equals);
                    ctx.add(AnnotationInstance
                            .builder(DotName.createSimple(
                                    "com.github.jonasrutishauser.transactional.event.quarkus.micrometer." + metricType))
                            .build());
                    Class<?> declaringClass;
                    try {
                        declaringClass = Thread.currentThread().getContextClassLoader()
                                .loadClass(method.declaringClass().name().toString());
                    } catch (ClassNotFoundException e) {
                        // should not happen
                        return;
                    }
                    if ("Counted".equals(metricType)) {
                        recorder.addCounter(declaringClass, method.name(), annotation.value("name").asString(),
                                annotation.value("description").asString());
                    } else if ("ConcurrentGauge".equals(metricType)) {
                        recorder.addConcurrentGauge(declaringClass, method.name(),
                                annotation.value("name").asString(), annotation.value("description").asString());
                    }
                });
    }

    @BuildStep
    ObserverConfiguratorBuildItem registerGauges(Optional<MetricsCapabilityBuildItem> metricsCapability,
            ObserverRegistrationPhaseBuildItem observerRegistrationPhase, InvokerFactoryBuildItem invokerFatory) {
        if (metricsCapability.stream().anyMatch(capability -> capability.metricsSupported(MICROMETER))) {
            Class<?> registratorClass;
            try {
                registratorClass = Thread.currentThread().getContextClassLoader()
                        .loadClass("com.github.jonasrutishauser.transactional.event.quarkus.micrometer.GaugeMetricsRegistrator");
            } catch (ClassNotFoundException e) {
                // should not happen
                return null;
            }
            List<ObserverConfigurator> gaugeRegistrators = new ArrayList<>();
            observerRegistrationPhase.getContext().beans().classBeans().forEach(bean -> {
                for (MethodInfo method : bean.getImplClazz().methods()) {
                    AnnotationInstance gauge = method.annotation("org.eclipse.microprofile.metrics.annotation.Gauge");
                    if (gauge != null) {
                        gaugeRegistrators.add(observerRegistrationPhase.getContext().configure() //
                                .beanClass(DotName.createSimple(registratorClass)) //
                                .observedType(Startup.class) //
                                .priority(Priority.LIBRARY_BEFORE) //
                                .id(gauge.value("name").asString()) //
                                .param("name", gauge.value("name").asString()) //
                                .param("description", gauge.value("description").asString()) //
                                .param("unit", gauge.value("unit").asString()) //
                                .param("invoker",
                                        invokerFatory.createInvoker(bean, method).withInstanceLookup().build()) //
                                .notify(generator -> {
                                    generator.notifyMethod().invokeStatic(
                                            MethodDesc.of(registratorClass, "register",
                                                    MethodType.methodType(void.class, Map.class)),
                                            generator.paramsMap());
                                    generator.notifyMethod().return_();
                                }));
                    }
                }
            });
            return new ObserverConfiguratorBuildItem(gaugeRegistrators.toArray(ObserverConfigurator[]::new));
        } else {
            return null;
        }
    }

    @BuildStep(onlyIfNot = IsProduction.class)
    UnremovableBeanBuildItem ensureDbSchemaIsNotRemoved() {
        return UnremovableBeanBuildItem.beanTypes(DbSchema.class);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(BeanContainerBuildItem.class)
    @BuildStep(onlyIfNot = IsProduction.class)
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
