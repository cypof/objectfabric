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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.objectfabric.JVMBuff.TestBuff;
import org.objectfabric.Workspace.Granularity;

class JVMPlatform extends Platform {

    static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static final ThreadLocal<Random> _random;

    private static final ThreadLocal<SimpleDateFormat> _utcNowFormat = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy.MM.dd'-'HH:mm:ss.SSS z");
        }
    };

    static {
        set(new JVMPlatform());

        _random = new ThreadLocal<Random>() {

            @Override
            protected Random initialValue() {
                return new Random();
            }
        };
    }

    static void loadClass() {
        if (Debug.ENABLED)
            Debug.assertion(get() instanceof JVMPlatform);
    }

    @Override
    int value() {
        return JVM;
    }

    //

    @Override
    URI resolve(String uri, URIResolver resolver) {
        java.net.URI parsed;

        try {
            parsed = new java.net.URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        String path = "";

        if (parsed.getPath() != null)
            path += parsed.getPath();

        if (parsed.getQuery() != null)
            path += parsed.getQuery();

        if (parsed.getFragment() != null)
            path += "#" + parsed.getFragment();

        int port = parsed.getPort() < 0 ? Address.NULL_PORT : parsed.getPort();
        Address address = new Address(parsed.getScheme(), parsed.getHost(), port);
        return resolver.resolve(address, path);
    }

    //

    @Override
    String lineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    char fileSeparator() {
        return File.separatorChar;
    }

    @Override
    String[] split(String value, char... chars) {
        return value.split("[" + new String(chars) + "]");
    }

    @Override
    Object[] copyWithTypedResize(Object[] source, int length, Object[] target) {
        return Arrays.copyOf(source, length, target.getClass());
    }

    @Override
    Object[] clone(Object[] array) {
        return array.clone();
    }

    @Override
    long[] clone(long[] array) {
        return array.clone();
    }

    //

    @Override
    Workspace newCustomWorkspace(CustomLocation store) {
        return new CustomWorkspace(store);
    }

    @Override
    Buff newBuff(int capacity, boolean recycle) {
        if (Debug.ENABLED)
            return new TestBuff(capacity, recycle);

        return new JVMBuff(capacity, recycle);
    }

    @Override
    Object getReferenceQueue() {
        return GCQueue.getInstance();
    }

    //

    @Override
    boolean randomBoolean() {
        return _random.get().nextBoolean();
    }

    @Override
    int randomInt(int limit) {
        return _random.get().nextInt(limit);
    }

    @Override
    int randomInt() {
        return _random.get().nextInt();
    }

    @Override
    double randomDouble() {
        return _random.get().nextDouble();
    }

    //

    private final SecureRandom _secureRandom = new SecureRandom();

    /**
     * See java.util.UUID.
     */
    @Override
    byte[] newUID_() {
        byte[] bytes = new byte[UID.LENGTH];
        _secureRandom.nextBytes(bytes);
        return bytes;
    }

    //

    @Override
    int floatToInt(float value) {
        return Float.floatToRawIntBits(value);
    }

    @Override
    float intToFloat(int value) {
        return Float.intBitsToFloat(value);
    }

    @Override
    long doubleToLong(double value) {
        return Double.doubleToRawLongBits(value);
    }

    @Override
    double longToDouble(long value) {
        return Double.longBitsToDouble(value);
    }

    //

    @Override
    String formatLog(String message) {
        String header = _utcNowFormat.get().format(new Date()) + ", ";
        header += Utils.padRight(Thread.currentThread().getName() + ", ", 25);
        return header + message;
    }

    @Override
    void logDefault(String message) {
        System.out.println(message);
    }

    @Override
    String getStackAsString(Throwable t) {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        t.printStackTrace(printWriter);
        return result.toString();
    }

    // Class

    @Override
    Class enclosingClass(Class c) {
        return c.getEnclosingClass();
    }

    @Override
    boolean isInstance(Class c, Object o) {
        return c.isInstance(o);
    }

    //

    @Override
    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    @Override
    void assertLock(Object lock, boolean hold) {
        Debug.assertion(Thread.holdsLock(lock) == hold);
    }

    @Override
    void execute(Runnable runnable) {
        ThreadPool.getInstance().execute(runnable);
    }

    @Override
    void schedule(Runnable runnable, int ms) {
        ThreadPool.scheduleOnce(runnable, ms);
    }

    @Override
    long approxTimeMs() {
        return System.nanoTime() >> 20;
    }

    @Override
    String simpleName(Class c) {
        return c.getSimpleName();
    }

    //

    @Override
    String toXML(Object model, String schema) {
        return XMLSerializer.toXMLString((ObjectModelDef) model, schema);
    }

    @Override
    ObjectModelDef fromXMLFile(String file) {
        return XMLSerializer.fromXMLFile(file);
    }

    // Debug

    @Override
    Workspace newTestWorkspace(Granularity granularity) {
        return new JVMWorkspace(granularity);
    }

    @Override
    Server newTestServer() {
        return new JVMServer();
    }

    @Override
    URIHandler newTestStore(String path) {
        return new FileSystem(path);
    }

    //

    @Override
    boolean shallowEquals(Object a, Object b, Class c, String... exceptions) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (a.getClass() != b.getClass())
            return false;

        if (c.isArray()) {
            Object[] x = (Object[]) a;
            Object[] y = (Object[]) b;

            if (x.length != y.length)
                return false;

            for (int i = 0; i < x.length; i++)
                if (!referenceLevelEquals(c.getComponentType(), x[i], y[i]))
                    return false;
        }

        ArrayList<Field> exceptionFields = new ArrayList<Field>();

        for (Field field : c.getDeclaredFields())
            if (Arrays.asList(exceptions).contains(field.getName()))
                exceptionFields.add(field);

        Debug.assertion(exceptionFields.size() == exceptions.length);

        try {
            for (Field field : c.getDeclaredFields()) {
                if (!exceptionFields.contains(field)) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        if (!Modifier.isFinal(field.getModifiers())) {
                            field.setAccessible(true);

                            Object x = field.get(a);
                            Object y = field.get(b);

                            if (!referenceLevelEquals(field.getType(), x, y))
                                return false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private static boolean referenceLevelEquals(Class c, Object a, Object b) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        // Primitives will be boxed so equals
        if (c.isPrimitive())
            return a.equals(b);

        // Otherwise use ==
        return a == b;
    }

    @Override
    Object getCurrentStack() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        return new Exception().getStackTrace();
    }

    @Override
    void assertCurrentStack(Object previous) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        ArrayList<StackTraceElement> a = new ArrayList<StackTraceElement>(Arrays.asList((StackTraceElement[]) previous));
        ArrayList<StackTraceElement> b = new ArrayList<StackTraceElement>(Arrays.asList(new Exception().getStackTrace()));

        removeUntestable(a);
        removeUntestable(b);

        Debug.assertion(a.size() == b.size());

        for (int i = 0; i < a.size(); i++) {
            if (i < 3) {
                Debug.assertion(!a.get(i).equals(b.get(i)));
                Debug.assertion(a.get(i).getClassName().equals(b.get(i).getClassName()));
            } else if (i == 3) {
                Debug.assertion(!a.get(i).equals(b.get(i)));
                Debug.assertion(a.get(i).getClassName().equals(b.get(i).getClassName()));
                Debug.assertion(a.get(i).getMethodName().equals(b.get(i).getMethodName()));
            } else if (i > 3) {
                boolean skip = false;

                if (a.get(i).toString().contains("org.objectfabric.vm.VM"))
                    skip = true;

                if (a.get(i).toString().contains("org.mortbay.jetty"))
                    skip = true;

                Debug.assertion(skip || a.get(i).equals(b.get(i)));
            }
        }
    }

    private static void removeUntestable(ArrayList<StackTraceElement> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            /*
             * Method invocation by reflection can sometime take different path. Native
             * invocation or dynamically generated method, so ignore this part.
             */
            if (list.get(i).getClassName().contains(".reflect."))
                list.remove(i);

            /*
             * Netty can replace pipeline elements at runtime.
             */
            if (list.get(i).getClassName().toLowerCase().contains("netty"))
                list.remove(i);
        }
    }

    @Override
    Object getPrivateField(Object object, String name, Class c) {
        return getPrivateFieldStatic(object, name, c);
    }

    static Object getPrivateFieldStatic(Object object, String name, Class c) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        try {
            Field field = c.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    TType getTypeField(Class c) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        TType value = null;

        try {
            Field field = c.getField("TYPE");

            if (field != null) {
                Debug.assertion((field.getModifiers() & Modifier.PUBLIC) != 0);
                Debug.assertion((field.getModifiers() & Modifier.STATIC) != 0);
                field.setAccessible(true); // Might be package visible class
                value = (TType) field.get(null);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return value;
    }

    @Override
    void writeAndResetAtomicLongs(Object object, boolean write) {
        StringBuilder sb = write ? new StringBuilder() : null;
        NumberFormat format = NumberFormat.getInstance();
        format.setGroupingUsed(true);

        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.getType() == AtomicLong.class) {
                if (sb != null && sb.length() > 0)
                    sb.append(", ");

                try {
                    AtomicLong value = (AtomicLong) field.get(object);

                    if (sb != null)
                        sb.append(field.getName() + ": " + format.format(value.get()));

                    value.set(0);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        if (sb != null)
            System.out.println(sb.toString());
    }
}
