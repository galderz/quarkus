package io.quarkus.deployment.pkg.steps;

import java.util.function.BooleanSupplier;

import io.quarkus.deployment.pkg.PackageConfig;

public class CracBuild implements BooleanSupplier {

    private final PackageConfig packageConfig;

    CracBuild(PackageConfig packageConfig) {
        this.packageConfig = packageConfig;
    }

    @Override
    public boolean getAsBoolean() {
        return packageConfig.type.equalsIgnoreCase(PackageConfig.CRAC);
    }
}
