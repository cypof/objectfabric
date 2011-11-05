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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import com.objectfabric.CompileTimeSettings;
import com.objectfabric.OF;
import com.objectfabric.OF.Config;
import com.objectfabric.ObjectModel;
import com.objectfabric.OverloadHandler;
import com.objectfabric.Privileged;
import com.objectfabric.Site;
import com.objectfabric.Store;
import com.objectfabric.Strings;
import com.objectfabric.TList;
import com.objectfabric.TMap;
import com.objectfabric.TObject;
import com.objectfabric.TSet;
import com.objectfabric.TType;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public final class PlatformAdapter extends Privileged {

    public static final int PLATFORM = CompileTimeSettings.PLATFORM_JAVA;

    public static final boolean THREADS_CAN_BLOCK = true;

    public static final boolean USE_ARRAYS_COPY_OF;

    public static final boolean USE_IO_EXCEPTION_CTOR;

    public static final byte SUPPORTED_SERIALIZATION_FLAGS = CompileTimeSettings.SERIALIZATION_BINARY_FLOAT_AND_DOUBLE;

    private static final ThreadLocal<Random> _random = new ThreadLocal<Random>() {

        @Override
        protected Random initialValue() {
            return new Random();
        }
    };

    static {
        boolean use = true;

        try {
            Arrays.class.getMethod("copyOf", Object[].class, int.class, Class.class);
        } catch (NoSuchMethodException ex) {
            use = false;
        }

        USE_ARRAYS_COPY_OF = use;

        use = true;

        try {
            IOException.class.getConstructor(Throwable.class);
        } catch (NoSuchMethodException ex) {
            use = false;
        }

        USE_IO_EXCEPTION_CTOR = use;
    }

    private PlatformAdapter() {
    }

    public static void init() {
        if (OF.getConfig() == null)
            OF.setConfig(new Config());
    }

    /**
     * @param store
     */
    public static Transaction createTrunk(Store store) {
        throw new IllegalStateException();
    }

    protected static TransparentExecutor createTransparentExecutor() {
        return new TransparentExecutor();
    }

    public static OverloadHandler createOverloadHandler() {
        return new OverloadHandler();
    }

    public static TType createTType(ObjectModel model, int classId, TType[] genericParameters) {
        return new TType(model, classId, genericParameters);
    }

    public static CompletedFuture<CommitStatus> createCompletedCommitStatusFuture(CommitStatus value) {
        return new CompletedFuture<CommitStatus>(value);
    }

    public static WritableFuture<CommitStatus> createCommitStatusFuture() {
        return createWritableFuture();
    }

    public static Bits.Entry createBitsEntry(int intIndex, int value) {
        return new Bits.Entry(intIndex, value);
    }

    public static Bits.Entry[] createBitsArray(int length) {
        return new Bits.Entry[length];
    }

    //

    public static boolean getRandomBoolean() {
        return _random.get().nextBoolean();
    }

    public static int getRandomInt(int limit) {
        return _random.get().nextInt(limit);
    }

    public static double getRandomDouble() {
        return _random.get().nextDouble();
    }

    static String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    public static String[] split(String value, char... chars) {
        return value.split("[" + new String(chars) + "]");
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    public static Object copyWithTypedResize(Object source, int size, Object target) {
        if (USE_ARRAYS_COPY_OF)
            return NotLoadedInDalvik.copyOf((Object[]) source, size, ((Object[]) target).getClass());

        Object[] a = (Object[]) source;
        Object[] b = (Object[]) target;

        if (b.length < a.length)
            b = new Object[a.length];

        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    public static Object[] clone(Object[] array) {
        return array.clone();
    }

    public static IOException createIOException(Exception e) {
        if (USE_IO_EXCEPTION_CTOR)
            return NotLoadedInDalvik.createIOException(e);

        return new IOException(getStackAsString(e));
    }

    public static final class NotLoadedInDalvik {

        public static final <T, U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
            return Arrays.copyOf((Object[]) original, newLength, newType);
        }

        public static final IOException createIOException(Exception e) {
            return new IOException(e);
        }
    }

    //

    public static final int UID_BYTES_COUNT = 16;

    private static final SecureRandom _secureRandom = new SecureRandom();

    public static void initializeUIDGenerator(byte[] uid) {
        if (Debug.ENABLED)
            Debug.assertion(uid.length == UID_BYTES_COUNT);

        // Ignored, used on GWT
    }

    /**
     * See java.util.UUID.
     */
    public static byte[] createUID() {
        byte[] buffer = new byte[UID_BYTES_COUNT];
        createUID(buffer);
        return buffer;
    }

    public static void createUID(byte[] buffer) {
        if (Debug.ENABLED)
            Debug.assertion(buffer.length == UID_BYTES_COUNT);

        _secureRandom.nextBytes(buffer);
    }

    //

    public static int floatToInt(float value) {
        return Float.floatToRawIntBits(value);
    }

    public static float intToFloat(int value) {
        return Float.intBitsToFloat(value);
    }

    public static String floatToString(float value) {
        return Float.toString(value);
    }

    public static float stringToFloat(String value) {
        return Float.parseFloat(value);
    }

    //

    public static long doubleToLong(double value) {
        return Double.doubleToRawLongBits(value);
    }

    public static double longToDouble(long value) {
        return Double.longBitsToDouble(value);
    }

    public static String doubleToString(double value) {
        return Double.toString(value);
    }

    public static double stringToDouble(String value) {
        return Double.parseDouble(value);
    }

    //

    public static String getStackAsString(Throwable t) {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        t.printStackTrace(printWriter);
        return result.toString();
    }

    public static void logUserCodeException(Exception e) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        Log.write(Strings.USER_CODE_RAISED_AN_EXCEPTION + result.toString());
    }

    public static final void exit(int code) {
        System.exit(code);
    }

    /*
     * Debug
     */

    public static boolean shallowEquals(Object a, Object b, Class c, String... exceptions) {
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

    public static final Object getCurrentStack() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        return new Exception().getStackTrace();
    }

    public static final void assertCurrentStack(Object previous) {
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

                if (a.get(i).toString().contains("com.objectfabric.vm.VM"))
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
             * HTTP filter can remove itself between two calls.
             */
            if (list.get(i).getClassName().startsWith("com.objectfabric.transports.http"))
                list.remove(i);
        }
    }

    public static Object getPrivateField(Object object, String name, Class c) {
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

    public static TType getTypeField(Class c) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        try {
            Field field = c.getField("TYPE");
            Debug.assertion((field.getModifiers() & Modifier.PUBLIC) != 0);
            Debug.assertion((field.getModifiers() & Modifier.STATIC) != 0);
            field.setAccessible(true); // Might be package visible class
            return (TType) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("null")
    public static void assertEqualsAndHashCodeAreDefault(TObject object) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        try {
            java.lang.reflect.Method e = null, h = null;

            for (Method m : object.getClass().getMethods()) {
                if (m.getName().equals("equals"))
                    e = m;

                if (m.getName().equals("hashCode"))
                    h = m;
            }

            Class c = e.getDeclaringClass();
            Debug.assertion(c == h.getDeclaringClass());

            if (!e.isSynthetic()) {
                boolean known = c == TList.class || c == TMap.class || c == TSet.class;
                Debug.assertion(known || c.getName().equals("com.objectfabric.TObject$UserTObject"));
            }
        } catch (Exception ex) {
            throw new RuntimeException();
        }
    }

    @SuppressWarnings("null")
    public static void writeAndResetAtomicLongs(Object object, boolean write) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        StringBuilder sb = write ? new StringBuilder() : null;
        NumberFormat format = NumberFormat.getInstance();
        format.setGroupingUsed(true);

        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.getType() == AtomicLong.class) {
                if (write && sb.length() > 0)
                    sb.append(", ");

                try {
                    AtomicLong value = (AtomicLong) field.get(object);

                    if (write)
                        sb.append(field.getName() + ": " + format.format(value.get()));

                    value.set(0);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        if (write)
            Log.write(sb.toString());
    }

    public static void assertHasNoUserTObjects(Object object) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        try {
            for (Field field : object.getClass().getFields())
                Debug.assertion(!isUserTObject(field.get(object)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static final void reset() {
        if (!Debug.TESTING)
            throw new RuntimeException();

        PlatformThreadPool.flush();
        resetOF();
        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
        Debug.ProcessName = "";
        Debug.AssertNoConflict = false;
        assertIdleAndCleanup();
    }

    public static final void shutdown() {
        if (!Debug.TESTING)
            throw new RuntimeException();

        reset();
        PlatformThreadPool.shutdown();
        disposeGCQueue();
    }
}
