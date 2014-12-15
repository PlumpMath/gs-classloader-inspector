package org.ali.gigaspaces.monitoring;

import javax.management.ObjectName;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;

/**
 * Install monitoring functions
 */
public class Agent {
    public static void premain(String args, Instrumentation inst) {
        ClassloaderRegistrar registrar = new ClassloaderRegistrar();
        inst.addTransformer(registrar);
        try {
            ObjectName on = new ObjectName("system", "type", "ClassloaderRegistrar");
            ManagementFactory.getPlatformMBeanServer().registerMBean(new ClassloaderRegistrarStandardBean(registrar), on);
        } catch (Throwable th) {
            System.err.println("Failed to register classloader tracing agent bean.");
        }
    }
}