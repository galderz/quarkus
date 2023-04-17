package io.quarkus.bootstrap.runner;

import java.lang.reflect.InvocationTargetException;

import org.crac.CheckpointException;
import org.crac.Core;
import org.crac.RestoreException;

public class CracCheckpoint {

    public static void doCheckpoint(ClassLoader loader) throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException, CheckpointException, RestoreException {
        System.out.println("CracCheckpoint.doCheckpoint: try to load ApplicationImpl...");
        Class<?> appClass = Class.forName("io.quarkus.runner.ApplicationImpl", true, loader);
        // todo do I need to create a new instance
        appClass.getDeclaredConstructor().newInstance();
        System.out.println("CracCheckpoint.doCheckpoint: checkpoint...");
        Core.checkpointRestore();
        System.out.println("CracCheckpoint.doCheckpoint: restored checkpoint");
    }
}
