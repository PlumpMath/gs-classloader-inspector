package org.ali.gigaspaces.monitoring;

/**
 * Expose classloader structure
 */
public interface ClassloaderRegistrarMBean {
    // formatted classloader tree within VM
    String showTree();
    // get url's from classloader of choice
    // cl is searched using hashcode
    String showClassloaderDetails(int classLoaderHashCode);
}
