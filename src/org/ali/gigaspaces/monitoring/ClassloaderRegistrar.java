package org.ali.gigaspaces.monitoring;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.RemoteException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Lets track classloaders...
 */
public class ClassloaderRegistrar implements ClassFileTransformer, ClassloaderRegistrarMBean {

    private static final Logger logger = Logger.getLogger("ClassloaderRegistrar");

    private static final String INDENT = "  ";

    public static final String CLASSLOADER_GIGASPACES_REMOTE = "com.gigaspaces.lrmi.classloading.LRMIClassLoader";
    public static final String CLASSLOADER_JINI_COMMON = "org.jini.rio.boot.CommonClassLoader";
    public static final String CLASSLOADER_JINI_SERVICE = "org.jini.rio.boot.ServiceClassLoader";
    public static final String CLASSLOADER_REFLECTION1 = "sun.reflect.DelegatingClassLoader";
    public static final String CLASSLOADER_REFLECTION2 = "sun.reflect.misc.MethodUtil";

    private Map<ClassLoader, ClassLoader> registeredClassloaders = new WeakHashMap<ClassLoader, ClassLoader>();

    @Override
    public synchronized byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (loader==null) return null;
        if (!registeredClassloaders.containsKey(loader)) {
            registeredClassloaders.put(loader, loader);
            //logger.info("Registering new classloader " + loader);
        }
        return null;
    }

    @Override
    public String showTree() {
        return buildTree().logContent(false);
    }

    @Override
    public String showTreeWithRemotePing() {
        return buildTree().logContent(true);
    }

    @Override
    public String showClassloaderDetails(int classLoaderHashCode) {
        ClassLoader cl = buildTree().getClassloader(classLoaderHashCode);
        if (cl==null) return "ClassLoader not found.";
        StringBuilder b = new StringBuilder(cl.getClass().getName())
                .append(" : ")
                .append(System.identityHashCode(cl))
                .append("\n");
        if (cl instanceof URLClassLoader) {
            b.append("URLs:\n");
            appendUrls(((URLClassLoader) cl).getURLs(), b, INDENT);
        }
        if (cl.getClass().getName().equals(CLASSLOADER_JINI_SERVICE)) {
            b.append("Service name:").append(getPrivateFieldOrNull(cl, "name")).append("\n");
            b.append("Search path:\n");
            appendUrls((URL[]) getPrivateFieldOrNull(cl, "searchPath"), b, INDENT);
            b.append("Lib path:\n");
            appendUrls((URL[]) getPrivateFieldOrNull(cl, "libPath"), b, INDENT);
        } else if (cl.getClass().getName().equals(CLASSLOADER_GIGASPACES_REMOTE)) {
            b.append("Context classes:\n");
            appendClassListWithClassloaders(cl, b);
        } else if (cl.getClass().getName().equals(CLASSLOADER_JINI_COMMON)) {
            b.append("Codebases:\n");
            appendUrlsList((List<URL>) getPrivateFieldOrNull(cl, "codebaseComponents"), b, INDENT);
            b.append("Components:\n");
            appendComponents((Map<String, URL[]>) getPrivateFieldOrNull(cl, "components"), b);
        }
        return b.toString();
    }

    private void appendComponents(Map<String, URL[]> map, StringBuilder b) {
        for(Map.Entry<String, URL[]> entry : map.entrySet()) {
            b.append(INDENT).append(entry.getKey()).append("\n");
            appendUrls(entry.getValue(), b, INDENT + INDENT);
        }
    }

    private void appendUrlsList(List<URL> urls, StringBuilder b, String indent) {
        appendUrls(urls==null?null:urls.toArray(new URL[0]), b, indent);
    }

    private void appendUrls(URL urls[], StringBuilder b, String indent) {
        if (urls==null) {
            b.append(indent).append("null");
        } else {
            for(URL url : urls) {
                b.append(indent).append(url).append("\n");
            }
        }
    }

    private void appendClassListWithClassloaders(ClassLoader classLoader, StringBuilder b) {
        try {
            Object classloaderContext = getPrivateField(classLoader, "_serviceClassLoaderContext");
            Map<String, WeakReference<? extends ClassLoader>> classes = getPrivateField(classloaderContext, "_classLoaderByName");
            for(Map.Entry<String,WeakReference<? extends ClassLoader>> entry : classes.entrySet()) {
                if (classLoader==entry.getValue().get()) {
                    b.append(INDENT).append(entry.getKey()).append(":")
                            .append(System.identityHashCode(entry.getValue().get())).append("\n");
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to read lrmi context classes", ex);
            b.append(INDENT).append("failed to read\n");
        }
    }

    private <T> T getPrivateFieldOrNull(Object object, String name) {
        try {
            return getPrivateField(object, name);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to fetch field " + name, ex);
        }
        return null;
    }

    private static <T> T getPrivateField(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T)field.get(object);
    }

    private synchronized ClassTree buildTree() {
        ClassTree tree = new ClassTree();
        for(ClassLoader cl : registeredClassloaders.values()) {
            tree.addHierarchy(cl);
        }
        return tree;
    }

    @Override
    public String getClassDetails(String name, int classLoaderHashCode) {
        ClassLoader classLoader = buildTree().getClassloader(classLoaderHashCode);
        if (classLoader==null) return "ClassLoader not found.";
        try {
            Class classToLog = classLoader.loadClass(name);
            return describeClass(classToLog);
        } catch (ClassNotFoundException e) {
            StringWriter writer = new StringWriter();
            PrintWriter printer = new PrintWriter(writer);
            e.printStackTrace(printer);
            printer.close();
            return writer.toString();
        }
    }

    private String describeClass(Class classToDescribe) {
        String description = "";
        while (classToDescribe!=null) {
            description += getClassDesc(classToDescribe) + "\n";
            classToDescribe = classToDescribe.getSuperclass();
        }
        return description;
    }

    private String getClassDesc(Class classToDescribe) {
        return classToDescribe.getName() + "@" + System.identityHashCode(classToDescribe) +
                "\n  classloader:        " + objectDescription(classToDescribe.getClassLoader()) +
                "\n  parent classloader: " + (classToDescribe.getClassLoader()==null?
                         "":
                         objectDescription(classToDescribe.getClassLoader().getParent()));
    }

    private String objectDescription(Object object) {
        return object==null?
                "null":
                (object.getClass().getName() + "@" + System.identityHashCode(object));
    }

    private static class TreeNode {
        final ClassLoader classLoader;
        int childCount = 0;
        private TreeNode(ClassLoader classLoader) {this.classLoader = classLoader;}
        void addChild() {childCount++;}
        void removeChild() {childCount--;}
        boolean isLast() {return childCount==0;}
    }

    private static class ClassTree {
        private List<TreeNode> classLoaders = new LinkedList<TreeNode>();

        ClassTree() {
            classLoaders.add(new TreeNode(null)); // bootstrap c/l
        }

        void addHierarchy(ClassLoader classLoader) {
            // filter out reflection class loaders
            String name = classLoader.getClass().getName();
            if (name.equals(CLASSLOADER_REFLECTION1) || name.equals(CLASSLOADER_REFLECTION2)) return;
            List<ClassLoader> fromParent = new ArrayList<ClassLoader>();
            do {
                fromParent.add(classLoader);
                classLoader = classLoader.getParent();
            } while (classLoader!=null);
            for(int i=fromParent.size()-1;i>=0;i--) {
                addClassLoader(fromParent.get(i));
            }
        }

        void addClassLoader(ClassLoader classLoader) {
            ListIterator<TreeNode> reverseIterator = classLoaders.listIterator(classLoaders.size());
            while(reverseIterator.hasPrevious()) {
                TreeNode current = reverseIterator.previous();
                if (current.classLoader==classLoader) return;
                if (current.classLoader==classLoader.getParent()) {
                    current.addChild();
                    classLoaders.add(reverseIterator.nextIndex()+1, new TreeNode(classLoader));
                    return;
                }
            }
        }

        String logContent(boolean addHealthCheck) {
            List<TreeNode> stack = new ArrayList<TreeNode>(classLoaders.size()/2);

            // Initialize bootstrap classloader
            Iterator<TreeNode> cls = classLoaders.iterator();
            stack.add(cls.next());
            StringBuilder builder = new StringBuilder("\nBootstrap\n");
            String prefix = " ";

            // Iterate over tree
            while(cls.hasNext()) {
                TreeNode current = cls.next();

                // find parent in stack
                while(stack.get(stack.size()-1).classLoader!=current.classLoader.getParent()) {
                    stack.remove(stack.size() - 1);
                    prefix = prefix.substring(0, prefix.length()-2);
                }
                // descend
                TreeNode parent = stack.get(stack.size() - 1);
                parent.removeChild();
                builder.append(prefix);
                if (parent.isLast()) {
                    prefix += "  ";
                    builder.append("\\-");
                } else {
                    prefix += "| ";
                    builder.append("|-");
                }
                builder.append(System.identityHashCode(current.classLoader));
                if (addHealthCheck) {
                    appendHealthcheck(current.classLoader, builder);
                }
                builder.append(" : ")
                        .append(current.classLoader.getClass().getName())
                        .append(" : ")
                        .append(current.classLoader)
                        .append('\n');
                stack.add(current);
            }
            return builder.toString();
        }

        void appendHealthcheck(ClassLoader classLoader, StringBuilder builder) {
            if (classLoader.getClass().getName().equals(CLASSLOADER_GIGASPACES_REMOTE)) {
                try {
                    Object remoteClassProvider = getPrivateField(classLoader, "_remoteClassProvider");
                    Object remoteClassLoaderId = getPrivateField(classLoader, "_remoteClassLoaderId");
                    Method method = remoteClassProvider.getClass().getMethod("getClassDefinition", long.class, String.class);
                    method.invoke(remoteClassProvider, remoteClassLoaderId, "non.existent.class.Here");
                } catch (InvocationTargetException e) {
                    logger.info("Failed to invoke method with exception : " + e.getMessage());
                    if (e.getTargetException() instanceof RemoteException) {
                        builder.append(" : [-]");
                    } else {
                        builder.append(" : [+]");
                    }
                } catch (Exception e) {
                    logger.severe("Failed to invoke method with exception : " + e.getMessage());
                    builder.append(" : [?]");
                }
            }
        }

        ClassLoader getClassloader(int hashCode) {
            for(TreeNode node : classLoaders) {
                if (System.identityHashCode(node.classLoader)==hashCode) {
                    return node.classLoader;
                }
            }
            return null;
        }
    }
}
