package io.quarkus.deployment.pkg.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.ProcessInheritIODisabledBuildItem;
import io.quarkus.deployment.util.ProcessUtil;

public class CracBuildRunner {
    private static final Logger log = Logger.getLogger(CracBuildRunner.class);

    // todo dup NativeImageBuildStep
    private static final String JAVA_HOME_SYS = "java.home";

    // todo dup NativeImageBuildStep
    private static final String JAVA_HOME_ENV = "JAVA_HOME";

    void build(Path checkpointPath, JarBuildItem jarBuildItem,
            Optional<ProcessInheritIODisabledBuildItem> processInheritIODisabledBuildItem) {
        // final String checkpointMain = "io.quarkus.bootstrap.runner.CracCheckpoint"; // CracCheckpoint loaded but ApplicationImpl not found
        // final String checkpointMain = "io.quarkus.runtime.CracCheckpoint"; // CrackCheckpoint not found

        final boolean debug = Boolean.getBoolean("quarkus.crac.checkpoint.debug");

        final List<String> command = new ArrayList<>();
        command.add(findJavaCmd());
        if (debug) {
            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005");
        }
        command.add("-jar");
        command.add("-XX:CRaCCheckpointTo=" + checkpointPath);
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+CRTraceStartupTime");
        command.add("-Djdk.crac.trace-startup-time=true");
        command.add("-Dquarkus.crac.checkpoint=true");
        command.add(jarBuildItem.getPath().toString());

        // log.info("-Dquarkus.crac.checkpoint.debug=" + debug);
        log.info(String.join(" ", command).replace("$", "\\$"));

        final ProcessBuilder pb = new ProcessBuilder(command).directory(jarBuildItem.getPath().getParent().toFile());
        try {
            final Process process = ProcessUtil.launchProcessStreamStdOut(pb, processInheritIODisabledBuildItem.isPresent());
            int exitCode = process.waitFor();
            if (exitCode != 137) {
                throw new RuntimeException("CRaC checkpoint failed");
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to call CRaC checkpoint", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for CRaC checkpoint to complete");
        }
    }

    // todo dup NativeImageBuildStep
    private static String findJavaCmd() {
        // try system property first - it will be the JAVA_HOME used by the current JVM
        String home = System.getProperty(JAVA_HOME_SYS);
        if (home == null) {
            // No luck, somewhat an odd JVM not enforcing this property
            // try with the JAVA_HOME environment variable
            home = System.getenv(JAVA_HOME_ENV);
        }

        if (home != null) {
            File javaHome = new File(home);
            File file = new File(javaHome, "bin/java");
            if (file.exists()) {
                return file.getPath();
            }
        }

        return "java";
    }
}
