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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Date;

import org.objectfabric.Workspace.Granularity;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.TimeZone;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Timer;

class GWTPlatform extends Platform {

    private static final DateTimeFormat _utcNowFormat = DateTimeFormat.getFormat("yyyy.MM.dd'-'HH:mm:ss.SSS z");

    static {
        if (!com.google.gwt.core.client.GWT.isClient())
            throw new RuntimeException("Server code has GWT imports.");

        set(new GWTPlatform());
    }

    static void loadClass() {
        if (Debug.ENABLED)
            Debug.assertion(get() instanceof GWTPlatform);
    }

    @Override
    int value() {
        return GWT;
    }

    @Override
    URI resolve(String uri, URIResolver resolver) {
        GWTURI parsed = new GWTURI(uri);
        String path = "";

        if (parsed.getPath() != null)
            path += parsed.getPath();

        if (parsed.getQuery() != null)
            path += parsed.getQuery();

        if (parsed.getFragment() != null)
            path += "#" + parsed.getFragment();

        Address address = new Address(parsed.getScheme(), parsed.getHost(), parsed.getPort());
        return resolver.resolve(address, path);
    }

    //

    @Override
    String lineSeparator() {
        return "\n";
    }

    @Override
    char fileSeparator() {
        return '/';
    }

    @Override
    String[] split(String value, char... chars) {
        return value.split("[" + new String(chars) + "]");
    }

    @Override
    Object[] copyWithTypedResize(Object[] source, int length, Object[] target) {
        if (target.length < source.length)
            target = new Object[source.length];

        System.arraycopy(source, 0, target, 0, source.length);
        return target;
    }

    @Override
    Object[] clone(Object[] array) {
        Object[] clone = new Object[array.length];
        System.arraycopy(array, 0, clone, 0, clone.length);
        return clone;
    }

    @Override
    long[] clone(long[] array) {
        long[] clone = new long[array.length];
        System.arraycopy(array, 0, clone, 0, clone.length);
        return clone;
    }

    @Override
    Buff newBuff(int capacity, boolean recycle) {
        return new GWTBuff(capacity, recycle);
    }

    @Override
    Object getReferenceQueue() {
        return null;
    }

    @Override
    Workspace newCustomWorkspace(CustomLocation store) {
        return null;
    }

    //

    @Override
    boolean randomBoolean() {
        return Random.nextBoolean();
    }

    @Override
    int randomInt(int limit) {
        return Random.nextInt(limit);
    }

    @Override
    int randomInt() {
        return Random.nextInt();
    }

    @Override
    double randomDouble() {
        return Random.nextDouble();
    }

    //

    private static byte[] _uid;

    private static long _uidXORCount;

    @Override
    public void initializeUIDGenerator(byte[] uid) {
        if (Debug.ENABLED)
            Debug.assertion(uid.length == UID.LENGTH);

        _uid = uid;
    }

    /**
     * Generates UIDs by modifying one created on the server using a SecureRandom,
     * hopefully without reducing entropy.
     */
    @Override
    byte[] newUID_() {
        byte[] bytes = new byte[UID.LENGTH];
        System.arraycopy(_uid, 0, bytes, 0, bytes.length);

        // Shuffle the first bytes, they can be used as hash

        int i = 0, rand = randomInt();
        bytes[i++] ^= rand;
        bytes[i++] ^= rand >>> 8;
        bytes[i++] ^= rand >>> 16;
        bytes[i++] ^= rand >>> 24;

        rand = randomInt();
        bytes[i++] ^= rand;
        bytes[i++] ^= rand >>> 8;
        bytes[i++] ^= rand >>> 16;
        bytes[i++] ^= rand >>> 24;

        // Increment to guarantee unicity

        bytes[i++] ^= _uidXORCount;
        bytes[i++] ^= _uidXORCount >>> 8;
        bytes[i++] ^= _uidXORCount >>> 16;
        bytes[i++] ^= _uidXORCount >>> 24;

        bytes[i++] ^= _uidXORCount >>> 32;
        bytes[i++] ^= _uidXORCount >>> 40;
        bytes[i++] ^= _uidXORCount >>> 48;
        bytes[i++] ^= _uidXORCount >>> 56;

        _uidXORCount++;
        return bytes;
    }

    //

    // TODO move to Platform
    @Override
    int floatToInt(float value) {
        return Float.floatToIntBits(value);
    }

    @Override
    float intToFloat(int value) {
        return Float.intBitsToFloat(value);
    }

    @Override
    long doubleToLong(double value) {
        return Double.doubleToLongBits(value);
    }

    @Override
    double longToDouble(long value) {
        return Double.longBitsToDouble(value);
    }

    //

    @Override
    String formatLog(String message) {
        String header = _utcNowFormat.format(new Date(), TimeZone.createTimeZone(0));
        return header + ", " + message;
    }

    @Override
    void logDefault(String message) {
        if (Debug.ENABLED)
            console(message);
    }

    native void console(String message) /*-{
		console.log(message);
    }-*/;

    @Override
    String getStackAsString(Throwable t) {
        final StringBuilder sb = new StringBuilder();

        PrintStream stream = new PrintStream((OutputStream) null) {

            @Override
            public void println(Object s) {
                sb.append(s);
            }

            @Override
            public void println(String s) {
                sb.append(s);
            }
        };

        t.printStackTrace(stream);
        return (t.getMessage() != null ? t.getMessage() : "") + sb.toString();
    }

    // Class

    @Override
    Class enclosingClass(Class c) {
        throw new UnsupportedOperationException();
    }

    @Override
    boolean isInstance(Class c, Object o) {
        throw new UnsupportedOperationException();
    }

    //

    @Override
    void sleep(long millis) {
        throw new UnsupportedOperationException();
    }

    @Override
    void assertLock(Object lock, boolean hold) {
    }

    @Override
    void execute(Runnable runnable) {
        Object key;

        if (Debug.ENABLED)
            ThreadAssert.suspend(key = new Object());

        runnable.run();

        if (Debug.ENABLED)
            ThreadAssert.resume(key);
    }

    @Override
    void schedule(final Runnable runnable, int ms) {
        Timer timer = new Timer() {

            @Override
            public void run() {
                runnable.run();
            }
        };

        timer.schedule(ms);
    }

    @Override
    long approxTimeMs() {
        return System.currentTimeMillis();
    }

    @Override
    String simpleName(Class c) {
        String name = c.getName();
        String[] parts = name.split("[.]");
        return parts.length <= 1 ? name : parts[parts.length - 1];
    }

    //

    @Override
    String toXML(Object model, String schema) {
        throw new UnsupportedOperationException();
    }

    @Override
    Object fromXMLFile(String file) {
        throw new UnsupportedOperationException();
    }

    // Debug

    @Override
    Workspace newTestWorkspace(Granularity granularity) {
        return null;
    }

    @Override
    Server newTestServer() {
        return null;
    }

    @Override
    URIHandler newTestStore(String path) {
        return null;
    }

    //

    @Override
    boolean shallowEquals(Object a, Object b, Class c, String... exceptions) {
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

    @Override
    Object getCurrentStack() {
        throw new UnsupportedOperationException();
    }

    @Override
    void assertCurrentStack(Object previous) {
        throw new UnsupportedOperationException();
    }

    @Override
    Object getPrivateField(Object object, String name, Class c) {
        throw new UnsupportedOperationException();
    }

    @Override
    TType getTypeField(Class c) {
        throw new UnsupportedOperationException();
    }

    @Override
    void writeAndResetAtomicLongs(Object object, boolean write) {
        throw new UnsupportedOperationException();
    }
}
