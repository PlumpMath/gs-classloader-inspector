package org.ali.gigaspaces.monitoring;

import javax.management.*;
import java.util.HashMap;
import java.util.Map;

/**
 * JMX Niceness
 */
public class ClassloaderRegistrarStandardBean extends StandardMBean {

    private static Map<String,String[]> ops = new HashMap<>();
    static {
        ops.put("showTree",
                new String[]{"Get tree of classloaders in JVM"});
        ops.put("showTreeWithRemotePing",
                new String[]{"Get tree of classloaders in JVM. Remote classloaders are pinged for liveness."});
        ops.put("showClassloaderDetails",
                new String[]{"Get detailed info about classloader. Depending on type classes or paths would be shown.",
                        "classloaderHashcode","Classloader object hashCode as returned by showTree"});
    }

    public ClassloaderRegistrarStandardBean(ClassloaderRegistrar registrar) throws NotCompliantMBeanException {
        super(registrar, ClassloaderRegistrarMBean.class);
    }

    protected String getClassName(MBeanInfo info) {
        return ClassloaderRegistrar.class.getName();
    }

    protected String getDescription(MBeanInfo info) {
        return "Classloader tracing agent";
    }

    protected String getDescription(MBeanOperationInfo info) {
        if (info==null) return null;
        String[] desc = ops.get(info.getName());
        return desc==null?null:desc[0];
    }

    protected String getParameterName(MBeanOperationInfo op,
                                      MBeanParameterInfo param,
                                      int sequence) {
        if (op==null) return null;
        String[] desc = ops.get(op.getName());
        return desc==null?null:desc[sequence*2+1];
    }

    protected String getDescription(MBeanOperationInfo op,
                                    MBeanParameterInfo param,
                                    int sequence) {
        if (op==null) return null;
        String[] desc = ops.get(op.getName());
        return desc==null?null:desc[sequence*2+2];
    }

    protected int getImpact(MBeanOperationInfo info) {
        return MBeanOperationInfo.INFO;
    }
}
