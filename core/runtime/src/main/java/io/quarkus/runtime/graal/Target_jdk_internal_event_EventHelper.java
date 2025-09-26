package io.quarkus.runtime.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.event.EventHelper")
final class Target_jdk_internal_event_EventHelper {
    @Substitute
    public static boolean isLoggingSecurity() {
        return false;
    }
}
