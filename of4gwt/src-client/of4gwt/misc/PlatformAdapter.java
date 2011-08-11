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

package of4gwt.misc;

import java.io.IOException;

import of4gwt.CompileTimeSettings;
import of4gwt.GWTConfig;
import of4gwt.OF;
import of4gwt.ObjectModel;
import of4gwt.OverloadHandler;
import of4gwt.Privileged;
import of4gwt.Site;
import of4gwt.Store;
import of4gwt.TObject;
import of4gwt.TType;
import of4gwt.Transaction;
import of4gwt.Transaction.CommitStatus;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Random;

public final class PlatformAdapter extends Privileged {

    public static final int PLATFORM = CompileTimeSettings.PLATFORM_GWT;

    public static final boolean THREADS_CAN_BLOCK = false;

    public static final byte SUPPORTED_SERIALIZATION_FLAGS = CompileTimeSettings.SERIALIZATION_NONE;

    private PlatformAdapter() {
    }

    public static void init() {
        /*
         * Throws if loaded in a JVM. This notifies developers they imported of4gwt
         * classes in their server code, which can create silent bugs.
         */
        if (!GWT.isClient())
            throw new RuntimeException("Server code has client imports (of4gwt.* must be used only client side).");

        if (OF.getConfig() == null)
            OF.setConfig(new GWTConfig());
    }

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
        return Random.nextBoolean();
    }

    public static int getRandomInt(int limit) {
        return Random.nextInt(limit);
    }

    public static double getRandomDouble() {
        return Random.nextDouble();
    }

    static String getLineSeparator() {
        return "\n";
    }

    public static String[] split(String value, char... chars) {
        throw new UnsupportedOperationException();
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    public static Object copyWithTypedResize(Object source, int size, Object target) {
        Object[] a = (Object[]) source;
        Object[] b = (Object[]) target;

        if (b.length < a.length)
            b = new Object[a.length];

        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    public static Object[] clone(Object[] array) {
        Object[] clone = new Object[array.length];
        PlatformAdapter.arraycopy(array, 0, clone, 0, clone.length);
        return clone;
    }

    public static IOException createIOException(Throwable throwable) {
        return new IOException(getStackAsString(throwable));
    }

    //

    public static final int UID_BYTES_COUNT = 16;

    private static byte[] _uid;

    private static int _uidXORCount;

    public static void initializeUIDGenerator(byte[] uid) {
        if (Debug.ENABLED)
            Debug.assertion(uid.length == UID_BYTES_COUNT);

        _uid = uid;
    }

    public static byte[] createUID() {
        if (_uidXORCount == -1)
            throw new IllegalStateException();

        byte[] bytes = new byte[UID_BYTES_COUNT];
        System.arraycopy(_uid, 0, bytes, 0, bytes.length);

        // Change the first ones, they can be used as hash or index
        bytes[0] ^= _uidXORCount;
        bytes[1] ^= _uidXORCount >>> 8;
        bytes[2] ^= _uidXORCount >>> 16;
        bytes[3] ^= _uidXORCount >>> 24;

        _uidXORCount++;

        return bytes;
    }

    //

    public static int floatToInt(float value) {
        throw new UnsupportedOperationException();
    }

    public static float intToFloat(int value) {
        throw new UnsupportedOperationException();
    }

    public static String floatToString(float value) {
        return Float.toString(value);
    }

    public static float stringToFloat(String value) {
        return Float.parseFloat(value);
    }

    //

    public static long doubleToLong(double value) {
        throw new UnsupportedOperationException();
    }

    public static double longToDouble(long value) {
        throw new UnsupportedOperationException();
    }

    public static String doubleToString(double value) {
        return Double.toString(value);
    }

    public static double stringToDouble(String value) {
        return Double.parseDouble(value);
    }

    //

    public static String getStackAsString(Throwable value) {
        value.printStackTrace();
        return value.toString();
    }

    public static void logListenerException(Throwable value) {
        value.printStackTrace();
        Log.write("A listener raised an exception: " + value.toString());
    }

    public static boolean shallowEquals(Object a, Object b, Class c, String... exceptions) {
        if (c.isArray()) {
            Object[] x = (Object[]) a;
            Object[] y = (Object[]) b;

            if (x.length != y.length)
                return false;

            for (int i = 0; i < x.length; i++)
                if (!referenceLevelEquals(c.getComponentType(), x[i], y[i]))
                    return false;
        } else {
            // Cannot clone arrays in GWT, so their type might be Object[]
            if (a.getClass() != b.getClass())
                return false;
        }

        return true;
    }

    private static boolean referenceLevelEquals(Class c, Object a, Object b) {
        // Primitives will be boxed so equals
        if (c.isPrimitive())
            return a.equals(b);

        // Otherwise use ==
        return a == b;
    }

    // Debug

    public static final void reset() {
        if (!Debug.TESTING)
            throw new RuntimeException();

        resetOF();
        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
        Debug.ProcessName = "";
        assertIdleAndCleanup();
    }

    public static final Object getCurrentStack() {
        throw new UnsupportedOperationException();
    }

    public static final void assertCurrentStack(Object previous) {
        throw new UnsupportedOperationException();
    }

    public static Object getPrivateField(Object object, String name, Class c) {
        throw new UnsupportedOperationException();
    }

    public static void assertEqualsAndHashCodeAreDefault(TObject object) {
    }

    public static void writeAndResetAtomicLongs(Object object, boolean write) {
    }

    public static void assertHasNoUserTObjects(Object object) {
    }
}
