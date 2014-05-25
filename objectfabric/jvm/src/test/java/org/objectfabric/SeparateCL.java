/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes code in a class loader to emulate a separate process.
 */
class SeparateCL extends Thread implements Separate {

    private final URL[] _classpath;

    private final String _mainClassName;

    private final boolean _useProgress;

    private final AtomicInteger _progress = new AtomicInteger();

    private final WritableFuture _future = new WritableFuture();

    private ClassLoader _initialClassLoader, _classLoader;

    private Class[] _argTypes = new Class[0];

    private Object[] _args = new Object[0];

    private Class<?> _mainClass;

    static {
        JVMPlatform.loadClass();
    }

    public SeparateCL(String mainClass) {
        this(mainClass, false);
    }

    public SeparateCL(String mainClass, boolean useProgress) {
        this(mainClass, mainClass, useProgress);

        setArgTypes(String[].class);
        setArgs(new Object[] { new String[0] });
    }

    SeparateCL(String name, String mainClass, boolean useProgress) {
        super(name);

        _mainClassName = mainClass;
        _useProgress = useProgress;

        _classpath = getClassPath();
        setDaemon(true);
    }

    static URL[] getClassPath() {
        String[] classpath = System.getProperty("java.class.path").split(System.getProperty("path.separator"));

        try {
            final List<URL> list = new ArrayList<URL>();

            if (classpath != null)
                for (final String element : classpath) {
                    list.addAll(getDirectoryClassPath(element));
                    list.add(new File(element).toURI().toURL());
                }

            return list.toArray(new URL[list.size()]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getProgress() {
        return _progress.get();
    }

    public void setProgress(int value) {
        _progress.set(value);
    }

    @Override
    public void waitForProgress(int value) {
        waitForProgress(_progress, value);
    }

    @Override
    public void waitForEnd() {
        try {
            join();
        } catch (InterruptedException e) {
            Log.write(e);
        }
    }

    static void waitForProgress(AtomicInteger progress, int value) {
        while (progress.get() < value) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }
    }

    Class[] getArgTypes() {
        return _argTypes;
    }

    void setArgTypes(Class... value) {
        _argTypes = value;
    }

    Object[] getArgs() {
        return _args;
    }

    void setArgs(Object... value) {
        _args = value;
    }

    Object getResult() {
        try {
            return _future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        run(true);
    }

    public void run(boolean close) {
        _initialClassLoader = Thread.currentThread().getContextClassLoader();
        _classLoader = new URLClassLoader(_classpath, null);
        Thread.currentThread().setContextClassLoader(_classLoader);
        Exception ex = null;

        try {
            _mainClass = _classLoader.loadClass(_mainClassName);
            ArrayList<Class> classes = new ArrayList<Class>(Arrays.asList(_argTypes));

            if (_useProgress)
                classes.add(0, AtomicInteger.class);

            Method method = _mainClass.getMethod("main", classes.toArray(new Class[classes.size()]));
            ArrayList<Object> args = new ArrayList<Object>(Arrays.asList(_args));

            if (_useProgress)
                args.add(0, _progress);

            _future.setResult(method.invoke(null, args.toArray()));
        } catch (Exception e) {
            Log.write(e);
            ex = e;
        }

        Thread.currentThread().setContextClassLoader(_initialClassLoader);

        if (ex != null && !(ex.getCause() instanceof InterruptedException))
            throw new RuntimeException(ex.getCause());

        if (close)
            close();
    }

    public void close() {
        invoke(SeparateCL.class.getName(), "close_", new Class[0]);
    }

    @SuppressWarnings("unused")
    public static void close_() {
        ThreadPool.shutdown();
        ThreadContext.disposeAll();

        if (Debug.ENABLED) {
//            for (URIResolver resolver : Helper.getInstance().Resolvers)
//                resolver.closeLocations();

            Helper.instance().ProcessName = "";
            Helper.instance().assertClassLoaderIdle();
        }
    }

    Object invoke(String methodName) {
        return invoke(methodName, new Class[0]);
    }

    Object invoke(String methodName, Class[] argsTypes, Object... args) {
        return invoke(_mainClassName, methodName, argsTypes, args);
    }

    Object invoke(String className, String methodName) {
        return invoke(className, methodName, new Class[0]);
    }

    Object invoke(String className, String methodName, Class[] argsTypes, Object... args) {
        if (Debug.ENABLED)
            Debug.assertion(Thread.currentThread().getContextClassLoader() == _initialClassLoader);

        Thread.currentThread().setContextClassLoader(_classLoader);

        try {
            Class<?> c = _classLoader.loadClass(className);
            Method method = c.getMethod(methodName, argsTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            Thread.currentThread().setContextClassLoader(_initialClassLoader);
        }
    }

    private static List<URL> getDirectoryClassPath(String aDir) {
        try {
            final List<URL> list = new LinkedList<URL>();
            final File dir = new File(aDir);
            final URL directoryURL = dir.toURI().toURL();
            final String[] children = dir.list();

            if (children != null) {
                for (final String element : children) {
                    if (element.endsWith(".jar")) {
                        final URL url = new URL(directoryURL, URLEncoder.encode(element, "UTF-8"));
                        list.add(url);
                    }
                }
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class WritableFuture extends FutureTask<Object> {

        public WritableFuture() {
            super(new Callable<Object>() {

                public Object call() throws Exception {
                    return null;
                }
            });
        }

        public void setResult(Object value) {
            super.set(value);
        }
    }
}
