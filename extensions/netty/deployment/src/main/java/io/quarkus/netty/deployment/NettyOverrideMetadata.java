package io.quarkus.netty.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ExcludeConfigBuildItem;

public class NettyOverrideMetadata {

    static final String NETTY_CODEC_JAR_MATCH_REGEX = "io\\.netty\\.netty-codec";
    static final String NETTY_CODEC_REFLECT_CONFIG_MATCH_REGEX = "/META-INF/native-image/io\\.netty/netty-codec/generated/handlers/reflect-config\\.json";
    static final String NETTY_HANDLER_JAR_MATCH_REGEX = "io\\.netty\\.netty-handler";
    static final String NETTY_HANDLER_REFLECT_CONFIG_MATCH_REGEX = "/META-INF/native-image/io\\.netty/netty-handler/generated/handlers/reflect-config\\.json";
    static final String NETTY_CODEC_HTTP_JAR_MATCH_REGEX = "io\\.netty\\.netty-codec-http";
    static final String NETTY_CODEC_HTTP_RESOURCE_MATCH_REGEX = "/META-INF/native-image/io\\.netty/netty-codec-http/native-image\\.properties";
    static final String NETTY_CODEC_HTTP2_JAR_MATCH_REGEX = "io\\.netty\\.netty-codec-http2";
    static final String NETTY_CODEC_HTTP2_RESOURCE_MATCH_REGEX = "/META-INF/native-image/io\\.netty/netty-codec-http2/native-image\\.properties";

    @BuildStep
    void excludeNettyDirectives(BuildProducer<ExcludeConfigBuildItem> nativeImageExclusions) {
        nativeImageExclusions
                .produce(new ExcludeConfigBuildItem(NETTY_CODEC_JAR_MATCH_REGEX, NETTY_CODEC_REFLECT_CONFIG_MATCH_REGEX));
        nativeImageExclusions
                .produce(new ExcludeConfigBuildItem(NETTY_HANDLER_JAR_MATCH_REGEX, NETTY_HANDLER_REFLECT_CONFIG_MATCH_REGEX));
        nativeImageExclusions
                .produce(new ExcludeConfigBuildItem(NETTY_CODEC_HTTP_JAR_MATCH_REGEX, NETTY_CODEC_HTTP_RESOURCE_MATCH_REGEX));
        nativeImageExclusions
                .produce(new ExcludeConfigBuildItem(NETTY_CODEC_HTTP2_JAR_MATCH_REGEX, NETTY_CODEC_HTTP2_RESOURCE_MATCH_REGEX));
    }
}
