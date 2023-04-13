package io.quarkus.deployment.pkg.steps;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.ProcessInheritIODisabledBuildItem;
import io.quarkus.deployment.util.ProcessUtil;

public class CracBuildRunner {
    private static final Logger log = Logger.getLogger(CracBuildRunner.class);

    void build(JarBuildItem jarBuildItem, Optional<ProcessInheritIODisabledBuildItem> processInheritIODisabledBuildItem) {
        // final String checkpointMain = "io.quarkus.bootstrap.runner.CracCheckpoint"; // CracCheckpoint loaded but ApplicationImpl not found
        // final String checkpointMain = "io.quarkus.runtime.CracCheckpoint"; // CrackCheckpoint not found

        final List<String> command = List.of("java", "-jar", "-Dquarkus.crac.checkpoint=true",
                jarBuildItem.getPath().toString());
        log.info(String.join(" ", command).replace("$", "\\$"));

        final ProcessBuilder pb = new ProcessBuilder(command).directory(jarBuildItem.getPath().getParent().toFile());
        try {
            final Process process = ProcessUtil.launchProcessStreamStdOut(pb, processInheritIODisabledBuildItem.isPresent());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("CRaC checkpoint failed");
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to call CRaC checkpoint", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for CRaC checkpoint to complete");
        }
    }
}
