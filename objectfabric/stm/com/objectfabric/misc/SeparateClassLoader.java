/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.objectfabric.misc;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes code in a class loader to emulate a separate process.
 */
public class SeparateClassLoader extends Thread implements Closeable {

    private final URL[] _classpath;

    private final String _mainClassName;

    private final boolean _useProgress;

    private final AtomicInteger _progress = new AtomicInteger();

    private final Future _future = new Future();

    private ClassLoader _initialClassLoader, _classLoader;

    private Class[] _argTypes = new Class[0];

    private Object[] _args = new Object[0];

    private Class<?> _mainClass;

    public SeparateClassLoader(String mainClass) {
        this("Tester", mainClass, false);

        setArgTypes(String[].class);
        setArgs(new Object[] { new String[0] });
    }

    public SeparateClassLoader(String name, String mainClass, boolean useProgress) {
        super(name);

        _mainClassName = mainClass;
        _useProgress = useProgress;

        _classpath = getClassPath();
        setDaemon(true);
    }

    public static URL[] getClassPath() {
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

    public AtomicInteger getProgress() {
        return _progress;
    }

    public void waitForProgress(int value) {
        waitForProgress(getProgress(), value);
    }

    public static void waitForProgress(AtomicInteger progress, int value) {
        while (progress.get() < value) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }
    }

    public Class[] getArgTypes() {
        return _argTypes;
    }

    public void setArgTypes(Class... value) {
        _argTypes = value;
    }

    public Object[] getArgs() {
        return _args;
    }

    public void setArgs(Object... value) {
        _args = value;
    }

    public Object getResult() {
        try {
            return _future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        _initialClassLoader = Thread.currentThread().getContextClassLoader();
        _classLoader = new URLClassLoader(_classpath, null);
        Thread.currentThread().setContextClassLoader(_classLoader);

        try {
            _mainClass = _classLoader.loadClass(_mainClassName);
            ArrayList<Class> classes = new ArrayList<Class>(Arrays.asList(_argTypes));

            if (_useProgress)
                classes.add(0, AtomicInteger.class);

            Method method = _mainClass.getMethod("main", classes.toArray(new Class[classes.size()]));
            ArrayList<Object> args = new ArrayList<Object>(Arrays.asList(_args));

            if (_useProgress)
                args.add(0, getProgress());

            _future.setResult(method.invoke(null, args.toArray()));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            Thread.currentThread().setContextClassLoader(_initialClassLoader);
        }
    }

    public Object invoke(String name, Class[] classes, Object... args) {
        if (Debug.ENABLED)
            Debug.assertion(Thread.currentThread().getContextClassLoader() == _initialClassLoader);

        Thread.currentThread().setContextClassLoader(_classLoader);

        try {
            Method method = _mainClass.getMethod(name, classes);
            return method.invoke(null, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            Thread.currentThread().setContextClassLoader(_initialClassLoader);
        }
    }

    public static boolean isTest() {
        Thread thread = Thread.currentThread();
        return thread.getClass().getName().equals(SeparateClassLoader.class.getName());
    }

    public static void waitForInterruption(Closeable... ressources) {
        PlatformFuture<Void> future = new PlatformFuture<Void>();

        try {
            future.get();
        } catch (InterruptedException _) {
            for (Closeable closeable : ressources) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    // Ignore
                }
            }

            PlatformAdapter.shutdown();
        } catch (ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void close() {
        WeakReference<ClassLoader> weak = new WeakReference<ClassLoader>(_classLoader);
        _mainClass = null;
        _classLoader = null;
        System.gc();

        Log.write("SeparateClassLoader GC " + (weak.get() != null ? "failed." : "succeeded."));
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

    private static final class Future extends PlatformFuture<Object> {

        public void setResult(Object value) {
            super.set(value);
        }
    }
}
