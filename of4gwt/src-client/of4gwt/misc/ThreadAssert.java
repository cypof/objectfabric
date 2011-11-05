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

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Debug purposes. Allows objects to be associated to threads, and asserts that only this
 * thread access them. When a context switches thread, a common object needs to be given
 * to ensure that the switch is expected on both sides.
 */
public final class ThreadAssert {

    /**
     * Notifies AspectJ checks that this object should be used in an single threaded way.
     */
    public @interface SingleThreaded {
    }

    /**
     * Notifies AspectJ checks that this object should be used in an single threaded way,
     * and can be shared at some point between threads. Only reads are allowed on fields
     * once instance has been shared.
     */
    public @interface SingleThreadedOrShared {
    }

    public @interface AllowSharedRead {
    }

    public @interface AllowSharedWrite {
    }

    private static final ThreadAssert _current = new ThreadAssert();

    private final ArrayList<Boolean> _overrideAssertBools = new ArrayList<Boolean>();

    private final ArrayList<Object> _overrideAssertKeys = new ArrayList<Object>();

    private final HashMap<Object, Long> _readersDebugCounters = new HashMap<Object, Long>();

    private final HashMap<Object, Long> _writersDebugCounters = new HashMap<Object, Long>();

    private ThreadAssert() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();
    }

    public static ThreadAssert getOrCreateCurrent() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        return _current;
    }

    public static void assertCurrentIsEmpty() {
        throw new UnsupportedOperationException();
    }

    /**
     * @param exceptions
     */
    public static void assertIdle(Object... exceptions) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static void addPrivate(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static boolean addPrivateIfNot(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static void assertPrivate(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static void assertPrivateOrShared(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static boolean isPrivate(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static void removePrivate(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param objects
     */
    public static void removePrivateList(List<Object> objects) {
        throw new UnsupportedOperationException();
    }

    //

    /**
     * @param object
     */
    public static void addShared(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static void assertShared(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static void removeShared(Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param object
     */
    public static void addSharedDefinitively(Object object) {
        throw new UnsupportedOperationException();
    }

    //

    /**
     * @param object
     */
    public static void assertCleaned(Object object) {
        throw new UnsupportedOperationException();
    }

    //

    /**
     * @param key
     * @param object
     */
    public static void exchangeGive(Object key, Object object) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param key
     * @param objects
     */
    public static void exchangeGiveList(Object key, List<Object> objects) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param key
     */
    public static ArrayList<Object> exchangeTake(Object key) {
        throw new UnsupportedOperationException();
    }

    /*
     * Whole context operations, used also to debug non thread related stuff.
     */

    /**
     * @param key
     */
    public static void suspend(Object key) {
    }

    /**
     * @param key
     */
    public static void resume(Object key) {
    }

    /**
     * @param key
     * @param assertExists
     */
    public static void resume(Object key, boolean assertExists) {
    }

    //

    public ArrayList<Boolean> getOverrideAssertBools() {
        return _overrideAssertBools;
    }

    public ArrayList<Object> getOverrideAssertKeys() {
        return _overrideAssertKeys;
    }

    //

    public long getReaderDebugCounter(Object reader) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Long value = _readersDebugCounters.get(reader);
        return value != null ? value : 0;
    }

    public long getAndIncrementReaderDebugCounter(Object reader) {
        long value = getReaderDebugCounter(reader);
        _readersDebugCounters.put(reader, value + 1);
        return value;
    }

    public void resetReaderDebugCounter(Object reader) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        _readersDebugCounters.remove(reader);
    }

    //

    public long getWriterDebugCounter(Object writer) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Long value = _writersDebugCounters.get(writer);
        return value != null ? value : 0;
    }

    public long getAndIncrementWriterDebugCounter(Object writer) {
        long value = getWriterDebugCounter(writer);
        _writersDebugCounters.put(writer, value + 1);
        return value;
    }

    public void resetWriterDebugCounter(Object writer) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        _writersDebugCounters.remove(writer);
    }

    //

    public void resetCounters() {
        _readersDebugCounters.clear();
        _writersDebugCounters.clear();
    }
}
