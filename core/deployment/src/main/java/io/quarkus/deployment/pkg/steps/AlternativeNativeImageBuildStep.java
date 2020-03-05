package io.quarkus.deployment.pkg.steps;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSystemPropertyBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageSourceJarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AlternativeNativeImageBuildStep {

    private static final String GRAALVM_HOME = "GRAALVM_HOME";

    /**
     * Name of the <em>system</em> property to retrieve JAVA_HOME
     */
    private static final String JAVA_HOME_SYS = "java.home";

    /**
     * Name of the <em>environment</em> variable to retrieve JAVA_HOME
     */
    private static final String JAVA_HOME_ENV = "JAVA_HOME";

    private static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");

    private static final Logger log = Logger.getLogger(AlternativeNativeImageBuildStep.class);

    @BuildStep(onlyIf = NativeBuild.class)
    ArtifactResultBuildItem result(NativeImageBuildItem image) {
        return new ArtifactResultBuildItem(image.getPath(), PackageConfig.NATIVE, Collections.emptyMap());
    }

    @BuildStep
    public NativeImageBuildItem build(
        NativeConfig nativeConfig,
        NativeImageSourceJarBuildItem nativeImageSourceJarBuildItem,
        OutputTargetBuildItem outputTargetBuildItem,
        PackageConfig packageConfig,
        List<NativeImageSystemPropertyBuildItem> nativeImageProperties) {
        Path runnerJar = nativeImageSourceJarBuildItem.getPath();
        log.info("Building native image from " + runnerJar);
        Path outputDir = nativeImageSourceJarBuildItem.getPath().getParent();

        final String runnerJarName = runnerJar.getFileName().toString();

        final Path java = Java.bin(nativeConfig);
        final String graalVMVersion = Graal.version(java);

        if (!graalVMVersion.isEmpty()) {
            checkGraalVMVersion(graalVMVersion);
        } else {
            log.error("Unable to get GraalVM version from the native-image binary.");
        }

        return null;
    }

    private void checkGraalVMVersion(String version) {
        log.info("Running Quarkus native-image plugin on " + version);
        final List<String> obsoleteGraalVmVersions = Arrays.asList("1.0.0", "19.0.", "19.1.", "19.2.", "19.3.0");
        final boolean vmVersionIsObsolete = obsoleteGraalVmVersions.stream().anyMatch(v -> version.contains(" " + v));
        if (vmVersionIsObsolete) {
            throw new IllegalStateException(
                "Out of date version of GraalVM detected: " + version + ". Please upgrade to GraalVM 19.3.1.");
        }
    }

    static class Java {

        private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

        /**
         * The name of the environment variable containing the system path.
         */
        private static final String PATH = "PATH";

        static Path bin(NativeConfig nativeConfig) {
            if (nativeConfig.containerRuntime.isPresent() || nativeConfig.containerBuild)
                return Paths.get("Not yet implemented");

            Optional<String> graalHome = nativeConfig.graalvmHome;
            Path javaHome = null;

            if (nativeConfig.javaHome == null) {
                // try system property first - it will be the JAVA_HOME used by the current JVM
                String home = System.getProperty(JAVA_HOME_SYS);
                if (home == null) {
                    // No luck, somewhat a odd JVM not enforcing this property
                    // try with the JAVA_HOME environment variable
                    home = System.getenv().get(JAVA_HOME_ENV);
                }

                if (home != null) {
                    javaHome = Paths.get(home);
                }
            } else {
                javaHome = nativeConfig.javaHome.toPath();
            }

            String javaName = IS_WINDOWS ? "java.bat" : "java";
            if (graalHome.isPresent()) {
                final Path java = Paths.get(graalHome.get(), "bin", javaName);
                File file = java.toFile();
                if (file.exists()) {
                    return java;
                }
            }

            if (javaHome != null) {
                final Path java = javaHome.resolve("bin/" + javaName);
                File file = java.toFile();
                if (file.exists()) {
                    return java;
                }
            }

            // System path
            String systemPath = System.getenv().get(PATH);
            if (systemPath != null) {
                String[] pathDirs = systemPath.split(File.pathSeparator);
                for (String pathDir : pathDirs) {
                    File dir = new File(pathDir);
                    if (dir.isDirectory()) {
                        File file = new File(dir, javaName);
                        if (file.exists()) {
                            return file.toPath();
                        }
                    }
                }
            }

            throw new RuntimeException("Cannot find the `" + javaName + "` in the GRAALVM_HOME, JAVA_HOME and System " +
                "PATH. Install it using `gu install native-image`");
        }

        private Java() {
        }
    }

    static class Graal {

        private static final String GRAAL_VERSION = "19.3.1";

        private static final Function<String, String> MAVEN_PATH =
            relativeTo(String.format("%s/.m2/repository", System.getProperty("user.home")))
                .compose(mavenVersioned(GRAAL_VERSION));

        private static final String JAR_SVM = MAVEN_PATH.apply(
            "org/graalvm/nativeimage/svm/%1$s/svm-%1$s.jar"
        );
        private static final String JAR_OBJECT_FILE = MAVEN_PATH.apply(
            "org/graalvm/nativeimage/objectfile/%1$s/objectfile-%1$s.jar"
        );
        private static final String JAR_POINTS_TO = MAVEN_PATH.apply(
            "org/graalvm/nativeimage/pointsto/%1$s/pointsto-%1$s.jar"
        );
        private static final String JAR_TRUFFLE_API = MAVEN_PATH.apply(
            "org/graalvm/truffle/truffle-api/%1$s/truffle-api-%1$s.jar"
        );
        private static final String JAR_GRAAL_SDK = MAVEN_PATH.apply(
            "org/graalvm/sdk/graal-sdk/%1$s/graal-sdk-%1$s.jar"
        );
        private static final String JAR_COMPILER = MAVEN_PATH.apply(
            "org/graalvm/compiler/compiler/%1$s/compiler-%1$s.jar"
        );
        private static final String JAR_LIBRARY_SUPPORT = MAVEN_PATH.apply(
            "org/graalvm/nativeimage/library-support/%1$s/library-support-%1$s.jar"
        );

        private static final String CLASS_PATH =
            classPath().collect(Collectors.joining(File.pathSeparator));

        private static final String MODULE_PATH =
            modulePath().collect(Collectors.joining(File.pathSeparator));

        private static final String UPGRADE_MODULE_PATH =
            upgradeModulePath().collect(Collectors.joining(File.pathSeparator));

        private static Stream<String> classPath() {
            return Stream.of(
                JAR_OBJECT_FILE
                , JAR_POINTS_TO
                , JAR_SVM
            );
        }

        private static Stream<String> modulePath() {
            return Stream.of(
                JAR_TRUFFLE_API
                , JAR_GRAAL_SDK
                , JAR_COMPILER
            );
        }

        private static Stream<String> upgradeModulePath() {
            return Stream.of(
                JAR_COMPILER
            );
        }

        private static Stream<String> addOpensAllUnnamed() {
            return Stream.of(
                "jdk.internal.vm.compiler/org.graalvm.compiler.debug"
                , "jdk.internal.vm.compiler/org.graalvm.compiler.nodes"
                , "jdk.unsupported/sun.reflect"
                , "java.base/jdk.internal.module"
                , "java.base/jdk.internal.ref"
                , "java.base/jdk.internal.reflect"
                , "java.base/java.io"
                , "java.base/java.lang"
                , "java.base/java.lang.reflect"
                , "java.base/java.lang.invoke"
                , "java.base/java.lang.ref"
                , "java.base/java.net"
                , "java.base/java.nio"
                , "java.base/java.nio.file"
                , "java.base/java.security"
                , "java.base/javax.crypto"
                , "java.base/java.util"
                , "java.base/java.util.concurrent.atomic"
                , "java.base/sun.security.x509"
                , "java.base/jdk.internal.logger"
                , "org.graalvm.sdk/org.graalvm.nativeimage.impl"
                , "org.graalvm.sdk/org.graalvm.polyglot"
                , "org.graalvm.truffle/com.oracle.truffle.polyglot"
                , "org.graalvm.truffle/com.oracle.truffle.api.impl"
            ).map(Graal::addUnnamed)
                .flatMap(Graal::addOpens);
        }

        private static Stream<String> xxPlus() {
            return Stream.of(
                "UnlockExperimentalVMOptions"
                , "EnableJVMCI"
            ).map(Graal::xxPlus);
        }

        static String xxPlus(String parameter) {
            return String.format(
                "-XX:+%s"
                , parameter
            );
        }

        private static Stream<String> xxMinus() {
            return Stream.of(
                "UseJVMCICompiler"
            ).map(Graal::xxMinus);
        }

        static String xxMinus(String parameter) {
            return String.format(
                "-XX:-%s"
                , parameter
            );
        }

        private static String addUnnamed(String modulePackage) {
            return String.format(
                "%s=ALL-UNNAMED"
                , modulePackage
            );
        }

        private static Stream<String> addOpens(String modulePackage) {
            return Stream.of(
                "--add-opens"
                , modulePackage
            );
        }

        private static Stream<String> systemProperties() {
            return Stream.of(new String[][]{
                {"truffle.TrustAllTruffleRuntimeProviders", "true"}
                , {"truffle.TruffleRuntime", "com.oracle.truffle.api.impl.DefaultTruffleRuntime"}
                , {"graalvm.ForcePolyglotInvalid", "true"}
                , {"graalvm.locatorDisabled", "true"}
                , {"substratevm.IgnoreGraalVersionCheck", "true"}
                , {"java.lang.invoke.stringConcat", "BC_SB"}
                , {"user.country", "US"}
                , {"user.language", "en"}
                , {"org.graalvm.version", "dev"}
                , {"org.graalvm.config", ""}
                , {"com.oracle.graalvm.isaot", "true"} // TODO could it be set to false? what's the impact?
                , {"jdk.internal.lambda.disableEagerInitialization", "true"}
                , {"jdk.internal.lambda.eagerlyInitialize", "false"}
                , {"java.lang.invoke.InnerClassLambdaMetafactory.initializeLambdas", "false"}
            }).map(entry -> systemProperty(entry[0], entry[1]));
        }

        private static String systemProperty(String key, String value)
        {
            return String.format(
                "-D%s=%s"
                , key
                , value
            );
        }

        static String imageCp() {
            return String.join(File.pathSeparator,
                JAR_LIBRARY_SUPPORT
                , JAR_OBJECT_FILE
                , JAR_POINTS_TO
                , JAR_SVM

                // With GraalVM home based setup, there doesn't seem to be a need for these jars here,
                // because there native-image-modules.list seems to trigger early class loading,
                // and annotaton processing.
                // Without that list file, we just force the jars through as part of the imagecp
                , JAR_COMPILER
                , JAR_GRAAL_SDK
            );
        }

        static String version(Path java) {
            return OperatingSystem.call()
                .compose(Graal::graalVersion)
                .apply(java);
        }

        private static OperatingSystem.Command graalVersion(Path java) {
            Function<Stream<String>, String> extractor = lines -> lines
                .filter((l) -> l.startsWith("GraalVM Version"))
                .findFirst()
                .orElse("");

            Stream<Stream<String>> params = Stream.of(
                Stream.of(java.toString())
                , xxPlus()
                , xxMinus()
                , systemProperties()
                , addOpensAllUnnamed()
                , Stream.of(
                    "--add-modules"
                    , "org.graalvm.truffle,org.graalvm.sdk"
                    , "--module-path"
                    , MODULE_PATH
                    , "--upgrade-module-path"
                    , UPGRADE_MODULE_PATH
                    , "-cp"
                    , CLASS_PATH
                    , "com.oracle.svm.hosted.NativeImageGeneratorRunner$JDK9Plus"
                    , "-imagecp"
                    , imageCp()
                    , "--version"
                )
            );

            return new OperatingSystem.Command(
                params.flatMap(s -> s)
                , null
                , Stream.empty()
                , extractor);
        }

        private static Function<String, String> relativeTo(String relativeTo) {
            return path ->
                String.format(
                    "%s/%s"
                    , relativeTo
                    , path
                );
        }

        private static Function<String, String> mavenVersioned(String version) {
            return mavenPathFormat ->
                String.format(
                    mavenPathFormat
                    , version
                );
        }

        public static void main(String[] args) {
            final NativeConfig nativeConfig = new NativeConfig();
            nativeConfig.containerRuntime = Optional.empty();
            nativeConfig.graalvmHome = Optional.empty();

            final Path java = Java.bin(nativeConfig);
            final String graalVersion = Graal.version(java);
            System.out.println(graalVersion);
        }
    }

    static class OperatingSystem {

        private static final Logger log = Logger.getLogger(OperatingSystem.class);

        static Function<Command, String> call() {
            return OperatingSystem::call;
        }

        private static String call(Command command) {
            final List<String> commandList = command.command
                .filter(not(String::isEmpty))
                .collect(Collectors.toList());

            log.debugf("Call %s in %s", commandList, command.directory);
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(commandList)
                    .directory(command.directory != null ? command.directory.toFile() : null)
                    .redirectErrorStream(true);

                command.envVars.forEach(
                    envVar -> processBuilder.environment()
                        .put(envVar.name, envVar.value)
                );

                Process process = processBuilder.start();

                List<String> output;
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    output = reader.lines().collect(Collectors.toList());
                }

                if (process.waitFor() != 0) {
                    log.errorf("Process call failed with output: %s", output);
                    throw new RuntimeException(
                        "Failed, exit code: " + process.exitValue()
                    );
                } else {
                    command.extractor.apply(output.stream());
                }

                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static Function<Command, Void> exec() {
            return OperatingSystem::exec;
        }

        private static Void exec(Command command) {
            final List<String> commandList = command.command
                .filter(not(String::isEmpty))
                .collect(Collectors.toList());

            log.debugf("Execute %s in %s", commandList, command.directory);
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(commandList)
                    .directory(command.directory.toFile())
                    .inheritIO();

                command.envVars.forEach(
                    envVar -> processBuilder.environment()
                        .put(envVar.name, envVar.value)
                );

                Process process = processBuilder.start();

                if (process.waitFor() != 0) {
                    throw new RuntimeException(
                        "Failed, exit code: " + process.exitValue()
                    );
                }

                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unused")
        static <T> Predicate<T> not(Predicate<? super T> target) {
            Objects.requireNonNull(target);
            return (Predicate<T>) target.negate();
        }

        static class Command {

            final Stream<String> command;
            final Path directory;
            final Stream<EnvVar> envVars;
            final Function<Stream<String>, String> extractor;

            Command(Stream<String> command, Path directory, Stream<EnvVar> envVars, Function<Stream<String>, String> extractor) {
                this.command = command;
                this.directory = directory;
                this.envVars = envVars;
                this.extractor = extractor;
            }
        }

        static class EnvVar {

            final String name;
            final String value;

            EnvVar(String name, String value) {
                this.name = name;
                this.value = value;
            }
        }
    }

    private static String detectNoPIE() {
        String argument = testGCCArgument("-no-pie");

        return argument.length() == 0 ? testGCCArgument("-nopie") : argument;
    }

    private static String testGCCArgument(String argument) {
        try {
            Process gcc = new ProcessBuilder("cc", "-v", "-E", argument, "-").start();
            gcc.getOutputStream().close();
            if (gcc.waitFor() == 0) {
                return argument;
            }

        } catch (IOException | InterruptedException e) {
            // eat
        }

        return "";
    }

}
