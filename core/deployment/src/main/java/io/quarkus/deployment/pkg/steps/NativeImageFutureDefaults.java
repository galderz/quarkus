package io.quarkus.deployment.pkg.steps;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import io.quarkus.deployment.pkg.NativeConfig;

public abstract class NativeImageFutureDefaults {
    private static final String FUTURE_DEFAULTS_MARKER = "--future-defaults=";

    protected final NativeConfig nativeConfig;

    public NativeImageFutureDefaults(final NativeConfig nativeConfig) {
        this.nativeConfig = nativeConfig;
    }

    static boolean isFutureDefault(FutureDefault futureDefault, NativeConfig nativeConfig) {
        Optional<List<String>>[] additionalBuildArgs = new Optional[] { nativeConfig.additionalBuildArgs(),
                nativeConfig.additionalBuildArgsAppend() };

        for (Optional<List<String>> args : additionalBuildArgs) {
            if (args.isEmpty()) {
                continue;
            }
            List<String> strings = args.get();
            for (String buildArg : strings) {
                String trimmedBuildArg = buildArg.trim();
                if (trimmedBuildArg.contains(FUTURE_DEFAULTS_MARKER)) {
                    int index = trimmedBuildArg.indexOf('=');
                    String[] futureDefaultStringArgs = trimmedBuildArg.substring(index + 1).split(",");
                    for (String futureDefaultString : futureDefaultStringArgs) {
                        if ("run-time-initialize-jdk".equals(futureDefaultString)) {
                            switch (futureDefault) {
                                case RUN_TIME_INITIALIZE_SECURITY_PROVIDERS:
                                case RUN_TIME_INITIALIZE_FILE_SYSTEM_PROVIDERS:
                                    return true;
                            }
                        }
                        final FutureDefault futureDefaultArg = FutureDefault
                                .valueOf(futureDefaultString.toUpperCase(Locale.ROOT).replace('-', '_'));
                        return futureDefaultArg == futureDefault;
                    }
                }
            }
        }

        return false;
    }

    enum FutureDefault {
        COMPLETE_REFLECTION_TYPES,
        RUN_TIME_INITIALIZE_FILE_SYSTEM_PROVIDERS,
        RUN_TIME_INITIALIZE_SECURITY_PROVIDERS,
    }

    public static final class RunTimeInitializeFileSystemProvider extends NativeImageFutureDefaults implements BooleanSupplier {
        public RunTimeInitializeFileSystemProvider(NativeConfig nativeConfig) {
            super(nativeConfig);
        }

        @Override
        public boolean getAsBoolean() {
            return isFutureDefault(FutureDefault.RUN_TIME_INITIALIZE_FILE_SYSTEM_PROVIDERS, nativeConfig);
        }
    }
}
