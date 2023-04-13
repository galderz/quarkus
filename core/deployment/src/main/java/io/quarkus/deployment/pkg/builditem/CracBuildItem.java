package io.quarkus.deployment.pkg.builditem;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public class CracBuildItem extends SimpleBuildItem {

    private final Path path;

    public CracBuildItem(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }
}
