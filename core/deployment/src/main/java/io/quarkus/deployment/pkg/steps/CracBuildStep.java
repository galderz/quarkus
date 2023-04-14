package io.quarkus.deployment.pkg.steps;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.CracBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.pkg.builditem.ProcessInheritIODisabledBuildItem;

public class CracBuildStep {

    @BuildStep(onlyIf = CracBuild.class)
    ArtifactResultBuildItem result(CracBuildItem cracBuildItem) {
        // todo add crac version information?
        return new ArtifactResultBuildItem(cracBuildItem.getPath(), PackageConfig.CRAC, new HashMap<>());
    }

    @BuildStep
    public CracBuildItem build(JarBuildItem jarBuildItem,
            Optional<ProcessInheritIODisabledBuildItem> processInheritIODisabledBuildItem,
            OutputTargetBuildItem outputTargetBuildItem) {
        final String checkpointName = "checkpoint";
        final Path checkpointPath = outputTargetBuildItem.getOutputDirectory().resolve(checkpointName);
        final CracBuildRunner cracBuildRunner = new CracBuildRunner();
        cracBuildRunner.build(checkpointPath, jarBuildItem, processInheritIODisabledBuildItem);
        return new CracBuildItem(checkpointPath);
    }
}
