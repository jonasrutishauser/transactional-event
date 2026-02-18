package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it.metrics;

import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class MpMetricsIT extends AbstractMetricsIT {
    @RegisterExtension
    static final QuarkusUnitTest config = config().setForcedDependencies(List.of(Dependency.of("io.quarkus",
            "quarkus-smallrye-metrics", QuarkusUnitTest.class.getPackage().getImplementationVersion())));
}