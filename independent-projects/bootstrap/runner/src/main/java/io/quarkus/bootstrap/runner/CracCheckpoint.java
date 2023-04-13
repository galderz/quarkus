package io.quarkus.bootstrap.runner;

import java.lang.reflect.InvocationTargetException;

public class CracCheckpoint {

    public static void doCheckpoint(ClassLoader loader) throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {
        System.out.println("CracCheckpoint.doCheckpoint: try to load ApplicationImpl...");
        Class<?> appClass = Class.forName("io.quarkus.runner.ApplicationImpl", true, loader);
        // todo do I need to create a new instance
        appClass.getDeclaredConstructor().newInstance();
        System.out.println("TODO application impl initialized, do a checkpoint");
    }
}
