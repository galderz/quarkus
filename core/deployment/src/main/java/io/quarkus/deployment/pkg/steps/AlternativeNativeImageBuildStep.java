package io.quarkus.deployment.pkg.steps;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import com.sun.management.OperatingSystemMXBean;

import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSystemPropertyBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageSourceJarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class AlternativeNativeImageBuildStep {

    private static final Logger log = Logger.getLogger(AlternativeNativeImageBuildStep.class);

    private static final int DEBUG_BUILD_PROCESS_PORT = 5005;
    private static final String QUARKUS_XMX_PROPERTY = "quarkus.native.native-image-xmx";

    @BuildStep(onlyIf = NativeBuild.class)
    ArtifactResultBuildItem result(NativeImageBuildItem image) {
        return new ArtifactResultBuildItem(image.getPath(), PackageConfig.NATIVE, Collections.emptyMap());
    }

    @BuildStep
    public NativeImageBuildItem build(
            NativeConfig nativeConfig, NativeImageSourceJarBuildItem nativeImageSourceJarBuildItem,
            OutputTargetBuildItem outputTargetBuildItem, PackageConfig packageConfig,
            List<NativeImageSystemPropertyBuildItem> nativeImageProperties) {

        Path runnerJar = nativeImageSourceJarBuildItem.getPath();

        // TODO check if container runtime or container build

        // TODO detect no pie (if linux)
        String noPIE = "";

        // TODO check graalvm version
        // TODO cleanup server

        try {
            final List<Pair> options = nativeImageOptions(nativeConfig, nativeImageProperties);
            final List<String> arguments = nativeImageArguments(noPIE, nativeConfig);

            String executableName = outputTargetBuildItem.getBaseName() + packageConfig.runnerSuffix;

            Optional<Integer> debugPort = nativeConfig.debugBuildProcess
                    ? Optional.of(DEBUG_BUILD_PROCESS_PORT)
                    : Optional.empty();

            Path outputDir = nativeImageSourceJarBuildItem.getPath().getParent();

            try {
                invokeNativeImage(executableName, outputDir, runnerJar, debugPort, options, nativeConfig.nativeImageXmx,
                        arguments);
            } catch (OperatingSystem.ExecException e) {
                // TODO signal when is docker
                throw imageGenerationFailed(e, false);
            }

            if (OperatingSystem.type().isWindows()) { //once image is generated it gets added .exe on windows
                executableName = executableName + ".exe";
            }
            Path generatedImage = outputDir.resolve(executableName);
            Path finalPath = outputTargetBuildItem.getOutputDirectory().resolve(executableName);
            IoUtils.copy(generatedImage, finalPath);
            Files.delete(generatedImage);
            System.setProperty("native.image.path", finalPath.toAbsolutePath().toString());

            return new NativeImageBuildItem(finalPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build native image", e);
        }
    }

    List<Pair> nativeImageOptions(NativeConfig nativeConfig,
            List<NativeImageSystemPropertyBuildItem> nativeImageProperties) {
        List<Pair> options = new ArrayList<>();
        Boolean enableSslNative = false;
        for (NativeImageSystemPropertyBuildItem prop : nativeImageProperties) {
            //todo: this should be specific build items
            if (prop.getKey().equals("quarkus.ssl.native") && prop.getValue() != null) {
                enableSslNative = Boolean.parseBoolean(prop.getValue());
            } else if (prop.getKey().equals("quarkus.jni.enable") && prop.getValue().equals("false")) {
                log.warn("Your application is setting the deprecated 'quarkus.jni.enable' configuration key to false."
                        + " Please consider removing this configuration key as it is ignored (JNI is always enabled) and it"
                        + " will be removed in a future Quarkus version.");
            } else if (prop.getKey().equals("quarkus.native.enable-all-security-services") && prop.getValue() != null) {
                nativeConfig.enableAllSecurityServices |= Boolean.parseBoolean(prop.getValue());
            } else if (prop.getKey().equals("quarkus.native.enable-all-charsets") && prop.getValue() != null) {
                nativeConfig.addAllCharsets |= Boolean.parseBoolean(prop.getValue());
            } else if (prop.getKey().equals("quarkus.native.enable-all-timezones") && prop.getValue() != null) {
                nativeConfig.includeAllTimeZones |= Boolean.parseBoolean(prop.getValue());
            } else {
                if (prop.getValue() == null) {
                    options.add(Pair.of(prop.getKey()));
                } else {
                    options.add(Pair.of(prop.getKey(), prop.getValue()));
                }
            }
        }

        if (enableSslNative) {
            nativeConfig.enableHttpsUrlHandler = true;
            nativeConfig.enableAllSecurityServices = true;
        }

        return options;
    }

    List<String> nativeImageArguments(String noPIE, NativeConfig nativeConfig) {
        List<String> arguments = new ArrayList<>();
        //        Boolean enableSslNative = false;
        //        for (NativeImageSystemPropertyBuildItem prop : nativeImageProperties) {
        //            //todo: this should be specific build items
        //            if (prop.getKey().equals("quarkus.ssl.native") && prop.getValue() != null) {
        //                enableSslNative = Boolean.parseBoolean(prop.getValue());
        //            } else if (prop.getKey().equals("quarkus.jni.enable") && prop.getValue().equals("false")) {
        //                log.warn("Your application is setting the deprecated 'quarkus.jni.enable' configuration key to false."
        //                        + " Please consider removing this configuration key as it is ignored (JNI is always enabled) and it"
        //                        + " will be removed in a future Quarkus version.");
        //            } else if (prop.getKey().equals("quarkus.native.enable-all-security-services") && prop.getValue() != null) {
        //                nativeConfig.enableAllSecurityServices |= Boolean.parseBoolean(prop.getValue());
        //            } else if (prop.getKey().equals("quarkus.native.enable-all-charsets") && prop.getValue() != null) {
        //                nativeConfig.addAllCharsets |= Boolean.parseBoolean(prop.getValue());
        //            } else if (prop.getKey().equals("quarkus.native.enable-all-timezones") && prop.getValue() != null) {
        //                nativeConfig.includeAllTimeZones |= Boolean.parseBoolean(prop.getValue());
        //            } else {
        //                // todo maybe just -D is better than -J-D in this case
        //                if (prop.getValue() == null) {
        //                    arguments.add("-J-D" + prop.getKey());
        //                } else {
        //                    arguments.add("-J-D" + prop.getKey() + "=" + prop.getValue());
        //                }
        //            }
        //        }
        //        //        command.add("-J-Duser.language=" + System.getProperty("user.language"));
        //        //        command.add("-J-Dfile.encoding=" + System.getProperty("file.encoding"));
        //
        //        if (enableSslNative) {
        //            nativeConfig.enableHttpsUrlHandler = true;
        //            nativeConfig.enableAllSecurityServices = true;
        //        }

        nativeConfig.additionalBuildArgs.ifPresent(l -> l.stream().map(String::trim).forEach(arguments::add));

        // TODO where would this go? who processes it?
        // arguments.add("--initialize-at-build-time=");

        arguments.add("-H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy$BySpaceAndTime"); //the default collection policy results in full GC's 50% of the time
        arguments.add("-H:+JNI");
        //        command.add("-jar");
        //        command.add(runnerJarName);

        if (nativeConfig.enableFallbackImages) {
            arguments.add("-H:FallbackThreshold=5");
        } else {
            //Default: be strict as those fallback images aren't very useful
            //and tend to cover up real problems.
            arguments.add("-H:FallbackThreshold=0");
        }

        if (nativeConfig.reportErrorsAtRuntime) {
            arguments.add("-H:+ReportUnsupportedElementsAtRuntime");
        }
        if (nativeConfig.reportExceptionStackTraces) {
            arguments.add("-H:+ReportExceptionStackTraces");
        }
        if (nativeConfig.debugSymbols) {
            arguments.add("-g");
        }
        //        if (nativeConfig.debugBuildProcess) {
        //            arguments.add("-J-Xrunjdwp:transport=dt_socket,address=" + DEBUG_BUILD_PROCESS_PORT + ",server=y,suspend=y");
        //        }
        if (nativeConfig.enableReports) {
            arguments.add("-H:+PrintAnalysisCallTree");
        }
        if (nativeConfig.dumpProxies) {
            arguments.add("-Dsun.misc.ProxyGenerator.saveGeneratedFiles=true");
            if (nativeConfig.enableServer) {
                log.warn(
                        "Options dumpProxies and enableServer are both enabled: this will get the proxies dumped in an unknown external working directory");
            }
        }
        //        if (nativeConfig.nativeImageXmx.isPresent()) {
        //            command.add("-J-Xmx" + nativeConfig.nativeImageXmx.get());
        //        }
        List<String> protocols = new ArrayList<>(2);
        if (nativeConfig.enableHttpUrlHandler) {
            protocols.add("http");
        }
        if (nativeConfig.enableHttpsUrlHandler) {
            protocols.add("https");
        }
        if (nativeConfig.addAllCharsets) {
            arguments.add("-H:+AddAllCharsets");
        } else {
            arguments.add("-H:-AddAllCharsets");
        }
        if (nativeConfig.includeAllTimeZones) {
            arguments.add("-H:+IncludeAllTimeZones");
        } else {
            arguments.add("-H:-IncludeAllTimeZones");
        }
        if (!protocols.isEmpty()) {
            arguments.add("-H:EnableURLProtocols=" + String.join(",", protocols));
        }
        if (nativeConfig.enableAllSecurityServices) {
            arguments.add("--enable-all-security-services");
        }
        if (!noPIE.isEmpty()) {
            arguments.add("-H:NativeLinkerOption=" + noPIE);
        }

        if (!nativeConfig.enableIsolates) {
            arguments.add("-H:-SpawnIsolates");
        }
        if (!nativeConfig.enableJni) {
            log.warn("Your application is setting the deprecated 'quarkus.native.enable-jni' configuration key to false."
                    + " Please consider removing this configuration key as it is ignored (JNI is always enabled) and it"
                    + " will be removed in a future Quarkus version.");
        }
        if (!nativeConfig.enableServer && OperatingSystem.type().notWindows()) {
            arguments.add("--no-server");
        }
        if (nativeConfig.enableVmInspection) {
            arguments.add("-H:+AllowVMInspection");
        }
        if (nativeConfig.autoServiceLoaderRegistration) {
            arguments.add("-H:+UseServiceLoaderFeature");
            //When enabling, at least print what exactly is being added:
            arguments.add("-H:+TraceServiceLoaderFeature");
        } else {
            arguments.add("-H:-UseServiceLoaderFeature");
        }
        if (nativeConfig.fullStackTraces) {
            arguments.add("-H:+StackTrace");
        } else {
            arguments.add("-H:-StackTrace");
        }

        return arguments;
    }

    private RuntimeException imageGenerationFailed(OperatingSystem.ExecException e, boolean isDocker) {
        if (e.isOOMError()) {
            if (isDocker && OperatingSystem.type().notLinux()) {
                return new RuntimeException("Image generation failed. Exit code was " + e.exitValue
                        + " which indicates an out of memory error. The most likely cause is Docker not being given enough memory. Also consider increasing the Xmx value for native image generation by setting the \""
                        + QUARKUS_XMX_PROPERTY + "\" property");
            } else {
                return new RuntimeException("Image generation failed. Exit code was " + e.exitValue
                        + " which indicates an out of memory error. Consider increasing the Xmx value for native image generation by setting the \""
                        + QUARKUS_XMX_PROPERTY + "\" property");
            }
        } else {
            return new RuntimeException("Image generation failed. Exit code: " + e.exitValue);
        }
    }

    void invokeNativeImage(String executableName, Path outputDir, Path runnerJar, Optional<Integer> debugPort,
            List<Pair> options, Optional<String> nativeImageXmx, List<String> arguments) {
        final Graal graal = Graal.of(
                "/opt/java-11-labs", "/Users/g/1/graal-19.3/graal/sdk/latest_graalvm_home");

        final OperatingSystem.JavaCommand commmand = NativeImage19x.command(
                executableName, runnerJar, options, nativeImageXmx, debugPort, arguments, outputDir, Maven.systemMaven(),
                graal);

        final Path directory = Paths.get(System.getProperty("user.dir"));

        OperatingSystem.exec()
                .compose(OperatingSystem.JavaCommand.toCommand(directory))
                .apply(commmand);
    }

    public static void main(String[] args) {
        final Graal graal = Graal.of(
                "/opt/java-11-labs", "/Users/g/1/graal-19.3/graal/sdk/latest_graalvm_home");

        final OperatingSystem.JavaCommand commmand = NativeImage19x.command(
                "helloworld", Paths.get("/Users/g/1/jawa/substratevm/helloworld/helloworld.jar"), Collections.emptyList(),
                Optional.empty(), Optional.empty(), Collections.emptyList(),
                Paths.get("/Users/g/1/jawa/substratevm/native-external/target"), Maven.systemMaven(), graal);

        final Path directory = Paths.get(System.getProperty("user.dir"));

        OperatingSystem.exec()
                .compose(OperatingSystem.JavaCommand.toCommand(directory))
                .apply(commmand);
    }

    private static class NativeImage19x {

        static OperatingSystem.JavaCommand command(
                String executableName, Path runnerJarPath, List<Pair> options, Optional<String> nativeImageXmx,
                Optional<Integer> debugPort,
                List<String> extraArguments, Path outputDir, Maven maven, Graal graal) {

            final Path javaBin = Graal.javaBin(graal);
            final Stream<String> vmOptions = vmOptions();
            final Stream<Pair> systemProperties = Stream.concat(
                    systemProperties(), options.stream());
            final Stream<String> unamedExports = unnamedExports();
            final Stream<String> unamedOpens = unnamedOpens();
            final String xss = "10m";
            final String xms = "1g";
            final String xmx = xmx(nativeImageXmx);
            final Stream<String> addModules = addModules();

            final String version = "19.3.1";
            // TODO cache up to groupId path and then reuse the function?
            final Path svmPath = Maven.resolve("svm", "org.graalvm.nativeimage", version, maven);
            final Path objectFilePath = Maven.resolve("objectfile", "org.graalvm.nativeimage", version, maven);
            final Path pointsToPath = Maven.resolve("pointsto", "org.graalvm.nativeimage", version, maven);
            final Path librarySupportToPath = Maven.resolve("library-support", "org.graalvm.nativeimage", version, maven);
            final Path truffleApiPath = Maven.resolve("truffle-api", "org.graalvm.truffle", version, maven);
            final Path graalSdkPath = Maven.resolve("graal-sdk", "org.graalvm.sdk", version, maven);
            final Path compilerPath = Maven.resolve("compiler", "org.graalvm.compiler", version, maven);

            final Stream<Path> modulePath = Stream.of(
                    truffleApiPath, graalSdkPath, compilerPath);

            final Stream<Path> upgradeModulePath = Stream.of(
                    compilerPath);

            final Path javaAgent = svmPath;

            final Stream<Path> classPath = Stream.of(
                    objectFilePath, pointsToPath, svmPath);

            final String mainClass = "com.oracle.svm.hosted.NativeImageGeneratorRunner$JDK9Plus";

            final Stream<Path> imageCp = Stream.of(
                    librarySupportToPath, objectFilePath, pointsToPath, svmPath

                    // With GraalVM home based setup, there doesn't seem to be a need for these jars here,
                    // because there native-image-modules.list seems to trigger early class loading,
                    // and annotaton processing.
                    // Without that list file, we just force the jars through as part of the imagecp
                    , compilerPath, graalSdkPath, runnerJarPath);

            final Stream<String> baseArguments = Stream.of(
                    "-imagecp", OperatingSystem.pathSeparated(imageCp), hArgument("Path", outputDir.toString()),
                    hArgument("CLibraryPath", Graal.cLibrariesPath(graal).toString()),
                    hArgument("Class", OperatingSystem.mainClass(runnerJarPath)), hArgument("Name", executableName));

            final Stream<String> arguments = Stream.concat(baseArguments, extraArguments.stream());

            final String debugAgentLib = debugAgentLib(debugPort);

            return new OperatingSystem.JavaCommand(
                    javaBin, debugAgentLib, vmOptions, systemProperties, unamedExports, unamedOpens, xss, xms, xmx, addModules,
                    modulePath, upgradeModulePath, javaAgent, classPath, mainClass, arguments);
        }

        private static String xmx(Optional<String> nativeImageXmx) {
            return nativeImageXmx.orElseGet(() -> {
                final long memorySize = OperatingSystem.physicalMemorySize();
                final long memoryMax = 16L * 1024 * 1024 * 1024;
                final long memoryFraction = Long.divideUnsigned(memorySize, 10) * 8;
                final long xmx = Math.min(memoryMax, memoryFraction);
                return Long.toString(xmx);
            });
        }

        private static String debugAgentLib(Optional<Integer> debugPort) {
            return debugPort
                    .map(Object::toString)
                    .map(Strings.prepend("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address="))
                    .orElse("");
        }

        private static String hArgument(String name, String value) {
            return String.format("-H:%s=%s", name, value);
        }

        private static Stream<String> addModules() {
            return Stream.of(
                    "org.graalvm.truffle", "org.graalvm.sdk");
        }

        private static Stream<String> unnamedOpens() {
            return Stream.of(
                    "jdk.internal.vm.compiler/org.graalvm.compiler.debug",
                    "jdk.internal.vm.compiler/org.graalvm.compiler.nodes", "jdk.unsupported/sun.reflect",
                    "java.base/jdk.internal.module", "java.base/jdk.internal.ref", "java.base/jdk.internal.reflect",
                    "java.base/java.io", "java.base/java.lang", "java.base/java.lang.reflect", "java.base/java.lang.invoke",
                    "java.base/java.lang.ref", "java.base/java.net", "java.base/java.nio", "java.base/java.nio.file",
                    "java.base/java.security", "java.base/javax.crypto", "java.base/java.util",
                    "java.base/java.util.concurrent.atomic", "java.base/sun.security.x509", "java.base/jdk.internal.logger",
                    "org.graalvm.sdk/org.graalvm.nativeimage.impl", "org.graalvm.sdk/org.graalvm.polyglot",
                    "org.graalvm.truffle/com.oracle.truffle.polyglot", "org.graalvm.truffle/com.oracle.truffle.api.impl");
        }

        private static Stream<String> unnamedExports() {
            return Stream.of(
                    "jdk.internal.vm.ci/jdk.vm.ci.runtime", "jdk.internal.vm.ci/jdk.vm.ci.code",
                    "jdk.internal.vm.ci/jdk.vm.ci.aarch64", "jdk.internal.vm.ci/jdk.vm.ci.amd64",
                    "jdk.internal.vm.ci/jdk.vm.ci.meta", "jdk.internal.vm.ci/jdk.vm.ci.hotspot",
                    "jdk.internal.vm.ci/jdk.vm.ci.services", "jdk.internal.vm.ci/jdk.vm.ci.common",
                    "jdk.internal.vm.ci/jdk.vm.ci.code.site", "jdk.internal.vm.ci/jdk.vm.ci.code.stack");
        }

        private static Stream<Pair> systemProperties() {
            return Stream.of(
                    new Pair("truffle.TrustAllTruffleRuntimeProviders", "true"),
                    new Pair("truffle.TruffleRuntime", "com.oracle.truffle.api.impl.DefaultTruffleRuntime"),
                    new Pair("graalvm.ForcePolyglotInvalid", "true"), new Pair("graalvm.locatorDisabled", "true"),
                    new Pair("substratevm.IgnoreGraalVersionCheck", "true"), new Pair("java.lang.invoke.stringConcat", "BC_SB"),
                    new Pair("user.country", "US"), new Pair("user.language", "en"), new Pair("org.graalvm.version", "dev"),
                    new Pair("org.graalvm.config", ""), new Pair("com.oracle.graalvm.isaot", "true") // TODO could it be set to false? what's the impact?
                    , new Pair("jdk.internal.lambda.disableEagerInitialization", "true"),
                    new Pair("jdk.internal.lambda.eagerlyInitialize", "false"),
                    new Pair("java.lang.invoke.InnerClassLambdaMetafactory.initializeLambdas", "false"));
        }

        private static Stream<String> vmOptions() {
            return Stream.of(
                    "+UnlockExperimentalVMOptions", "+EnableJVMCI", "-UseJVMCICompiler");
        }
    }

    private static class OperatingSystem {

        public enum Type {
            WINDOWS,
            MAC_OS,
            LINUX,
            OTHER;

            boolean isWindows() {
                return this == WINDOWS;
            }

            boolean notWindows() {
                return !isWindows();
            }

            boolean isLinux() {
                return this == LINUX;
            }

            boolean notLinux() {
                return !isLinux();
            }
        }

        static Function<Command, Void> exec() {
            return command -> {
                exec(command);
                return null;
            };
        }

        private static void exec(Command command) {
            final List<String> commandList = command.command
                    .filter(not(String::isEmpty))
                    .collect(Collectors.toList());

            log.info(String.join(" ", commandList));

            log.debugf("Execute in %s:", command.directory);
            log.debugf(String.join(" \\" + System.lineSeparator(), commandList));
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(commandList)
                        .directory(command.directory.toFile())
                        .inheritIO();

                command.envVars.forEach(
                        envVar -> processBuilder.environment()
                                .put(envVar.name, envVar.value));

                Process process = processBuilder.start();

                if (process.waitFor() != 0) {
                    throw new ExecException(process.exitValue(), "Failed execution");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static long physicalMemorySize() {
            final OperatingSystemMXBean osMxBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osMxBean.getTotalPhysicalMemorySize();
        }

        static String mainClass(Path jarPath) {
            try (final JarFile jar = new JarFile(jarPath.toFile())) {
                final Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    throw new RuntimeException(
                            String.format("Missing manifest in jar file: %s", jarPath));
                }

                final String mainClass = manifest
                        .getMainAttributes()
                        .getValue("Main-Class");

                if (mainClass == null) {
                    throw new RuntimeException(
                            String.format("Missing Main-Class in jar file: %s", jarPath));
                }

                return mainClass;
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("Unable to read jar file: %s", jarPath), e);
            }
        }

        private static String pathSeparated(Stream<Path> paths) {
            return paths
                    .map(Object::toString)
                    .collect(Collectors.joining(File.pathSeparator));
        }

        static Type type() {
            String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);

            if ((OS.contains("mac")) || (OS.contains("darwin")))
                return Type.MAC_OS;

            if (OS.contains("win"))
                return Type.WINDOWS;

            if (OS.contains("nux"))
                return Type.LINUX;

            return Type.OTHER;
        }

        private static <T> Predicate<T> not(Predicate<? super T> target) {
            Objects.requireNonNull(target);
            return (Predicate<T>) target.negate();
        }

        static class ExecException extends RuntimeException {
            final int exitValue;

            ExecException(int exitValue, String message) {
                super(String.format("%s (exit value %d)", message, exitValue));
                this.exitValue = exitValue;
            }

            boolean isOOMError() {
                return exitValue == 137;
            }
        }

        static class Command {

            final Stream<String> command;
            final Path directory;
            final Stream<Pair> envVars;

            Command(Stream<String> command, Path directory, Stream<Pair> envVars) {
                this.command = command;
                this.directory = directory;
                this.envVars = envVars;
            }
        }

        static class JavaCommand {

            final Path javaBin;
            final String agentlib;
            final Stream<String> vmOptions; // -XX:[+|-]...
            final Stream<Pair> systemProperties;
            final Stream<String> unnamedExports;
            final Stream<String> unnamedOpens;
            final String xss;
            final String xms;
            final String xmx;
            final Stream<String> addModules;
            final Stream<Path> modulePath;
            final Stream<Path> upgradeModulePath;
            final Path javaAgent;
            final Stream<Path> classPath;
            final String mainClass;
            final Stream<String> arguments;

            JavaCommand(Path javaBin, String agentlib, Stream<String> vmOptions, Stream<Pair> systemProperties,
                    Stream<String> unnamedExports, Stream<String> unnamedOpens, String xss, String xms, String xmx,
                    Stream<String> addModules, Stream<Path> modulePath, Stream<Path> upgradeModulePath, Path javaAgent,
                    Stream<Path> classPath, String mainClass, Stream<String> arguments) {
                this.javaBin = javaBin;
                this.agentlib = agentlib;
                this.vmOptions = vmOptions;
                this.systemProperties = systemProperties;
                this.unnamedExports = unnamedExports;
                this.unnamedOpens = unnamedOpens;
                this.xss = xss;
                this.xms = xms;
                this.xmx = xmx;
                this.addModules = addModules;
                this.modulePath = modulePath;
                this.upgradeModulePath = upgradeModulePath;
                this.javaAgent = javaAgent;
                this.classPath = classPath;
                this.mainClass = mainClass;
                this.arguments = arguments;
            }

            static Function<JavaCommand, Command> toCommand(Path directory) {
                return javaCommand -> {
                    // TODO could Stream.of calls be wrapped into one? Does order matter?
                    Stream<Stream<String>> full = Stream.of(
                            Stream.of(javaCommand.javaBin.toString()), Stream.of(javaCommand.agentlib),
                            javaCommand.vmOptions.map(Strings.prepend("-XX:")),
                            javaCommand.systemProperties.map(formatPair("-D%s=%s")),
                            javaCommand.unnamedExports.map(allUnnamed()).flatMap(addExports()),
                            javaCommand.unnamedOpens.map(allUnnamed()).flatMap(addOpens()),
                            Stream.of(Strings.prepend("-Xss").apply(javaCommand.xss)),
                            Stream.of(Strings.prepend("-Xms").apply(javaCommand.xms)),
                            Stream.of(Strings.prepend("-Xmx").apply(javaCommand.xmx)), Stream.of("--add-modules"),
                            Stream.of(javaCommand.addModules.collect(Collectors.joining(","))), Stream.of("--module-path"),
                            Stream.of(OperatingSystem.pathSeparated(javaCommand.modulePath)),
                            Stream.of("--upgrade-module-path"),
                            Stream.of(OperatingSystem.pathSeparated(javaCommand.upgradeModulePath)),
                            Stream.of(Strings.prepend("-javaagent:").apply(javaCommand.javaAgent.toString())),
                            Stream.of("--class-path"), Stream.of(OperatingSystem.pathSeparated(javaCommand.classPath)),
                            Stream.of(javaCommand.mainClass), javaCommand.arguments);

                    final Stream<String> flattened = full.flatMap(s -> s);
                    return new OperatingSystem.Command(
                            flattened, directory, Stream.empty());
                };
            }

            private static Function<String, String> allUnnamed() {
                return Strings.append("=ALL-UNNAMED");
            }

            private static Function<String, Stream<? extends String>> addExports() {
                return modulePackage -> Stream.of("--add-exports", modulePackage);
            }

            private static Function<String, Stream<? extends String>> addOpens() {
                return modulePackage -> Stream.of("--add-opens", modulePackage);
            }

            static Function<Pair, String> formatPair(String format) {
                return pair -> String.format(format, pair.name, pair.value);
            }

        }
    }

    private static class Strings {

        static Function<String, String> prepend(String toPrepend) {
            return text -> String.format("%s%s", toPrepend, text);
        }

        static Function<String, String> append(String toAppend) {
            return text -> String.format("%s%s", text, toAppend);
        }

    }

    private static class Pair {

        final String name;
        final String value;

        private Pair(String name, String value) {
            this.name = name;
            this.value = value;
        }

        static Pair of(String name, String value) {
            return new Pair(name, value);
        }

        static Pair of(String name) {
            return new Pair(name, "");
        }
    }

    private static class Maven {

        final Path localRepository;

        private Maven(Path localRepository) {
            this.localRepository = localRepository;
        }

        static Maven systemMaven() {
            final String userHome = System.getProperty("user.home");
            return new Maven(
                    Paths.get(userHome, ".m2", "repository"));
        }

        static Path resolve(String artifactId, String groupId, String version, Maven maven) {
            final String groupDir = groupId.replace('.', File.separatorChar);
            final Path artifactPath = Paths.get(
                    groupDir, artifactId, version, String.format("%s-%s.jar", artifactId, version));

            return maven.localRepository.resolve(artifactPath);
        }

    }

    private static class Graal {

        final Path javaHome;
        final Path graalHome;

        private Graal(Path javaHome, Path graalHome) {
            this.javaHome = javaHome;
            this.graalHome = graalHome;
        }

        static Graal of(String javaHome, String graalHome) {
            return new Graal(Paths.get(javaHome), Paths.get(graalHome));
        }

        static Path javaBin(Graal graal) {
            final Path javaBin = Paths.get("bin", "java");
            return graal.javaHome.resolve(javaBin);
        }

        static Path cLibrariesPath(Graal graal) {
            String osNameArch = OperatingSystem.type() == OperatingSystem.Type.MAC_OS
                    ? "darwin-amd64"
                    : "TODO";

            Path clibrariesPath = Paths.get(
                    "lib", "svm", "clibraries", osNameArch);

            return graal.graalHome.resolve(clibrariesPath);
        }

    }

}
