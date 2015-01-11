package org.ali.gigaspaces.monitoring;

/**
 * Expose classloader structure
 */
public interface ClassloaderRegistrarMBean {
    // formatted classloader tree within VM
    String showTree();
    // formatted classloader tree within VM, remote GS classloaders are pinged for liveness which may slow down operation
    String showTreeWithRemotePing();
    // get url's from classloader of choice
    // cl is searched using hashcode
    String showClassloaderDetails(int classLoaderHashCode);
    // instantiate class and get classloader details
    String getClassDetails(String name, int classLoaderHashCode);
}
