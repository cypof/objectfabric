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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Debug purposes. Allows objects to be associated to threads, and asserts that only this
 * thread access them. When a context switches thread, a common object needs to be given
 * to ensure that the switch is expected on both sides.
 */
final class ThreadAssert {

    /**
     * Notifies AspectJ checks that this object should be used in an single threaded way.
     */
    @interface SingleThreaded {
    }

    /**
     * Notifies AspectJ checks that this object should be used in an single threaded way,
     * and can be shared at some point between threads. Only reads are allowed on fields
     * once instance has been shared.
     */
    @interface SingleThreadedThenShared {
    }

    @interface AllowSharedRead {
    }

    @interface AllowSharedWrite {
    }

    private static final PlatformThreadLocal<ThreadAssert> _current;

    private static final PlatformConcurrentMap<RefEqual, ThreadAssert> _map;

    private static final PlatformConcurrentMap<RefEqual, Object> _shared;

    private static final PlatformMap<RefEqual, List<Object>> _exchangedObjects;

    private static final PlatformConcurrentMap<RefEqual, ThreadAssert> _suspendedContexts;

    private static final PlatformThreadLocal<Object> _ownerKey;

    private static final PlatformMap<Object, Object> _sharedDefinitively;

    private final AtomicReference<Object> _owner = new AtomicReference<Object>();

    private final PlatformConcurrentMap<RefEqual, Object> _objects = new PlatformConcurrentMap<RefEqual, Object>();

    // Various debug helpers

    private final ArrayList<Boolean> _overrideAssertBools = new ArrayList<Boolean>();

    private final ArrayList<Object> _overrideAssertKeys = new ArrayList<Object>();

    private final HashMap<Object, Long> _readersDebugCounters = new HashMap<Object, Long>();

    private final HashMap<Object, Long> _writersDebugCounters = new HashMap<Object, Long>();

    static {
        if (Debug.ENABLED) {
            _current = new PlatformThreadLocal<ThreadAssert>();
            _map = new PlatformConcurrentMap<RefEqual, ThreadAssert>();
            _shared = new PlatformConcurrentMap<RefEqual, Object>();
            _sharedDefinitively = new PlatformMap<Object, Object>();
            _exchangedObjects = new PlatformMap<RefEqual, List<Object>>();
            _suspendedContexts = new PlatformConcurrentMap<RefEqual, ThreadAssert>();
            _ownerKey = new PlatformThreadLocal<Object>();
        } else {
            _current = null;
            _map = null;
            _shared = null;
            _sharedDefinitively = null;
            _exchangedObjects = null;
            _suspendedContexts = null;
            _ownerKey = null;
        }
    }

    private ThreadAssert() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();
    }

    static ThreadAssert getOrCreateCurrent() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        ThreadAssert current = _current.get();

        if (current == null) {
            current = new ThreadAssert();
            current._owner.set(getOwnerKey());
            _current.set(current);
        }

        return current;
    }

    /**
     * Replaces Thread.currentThread, which is not part of PlatformThread.
     */
    private static Object getOwnerKey() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Object key = _ownerKey.get();

        if (key == null)
            _ownerKey.set(key = new Object());

        return key;
    }

    static boolean isCurrentEmpty() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert current = _current.get();
        return current == null || current._objects.size() == 0;
    }

    @SuppressWarnings("null")
    static void assertCurrentIsEmpty() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert current = _current.get();
        int size = current != null ? current._objects.size() : 0;

        if (size > 0)
            for (Object object : current._objects.values())
                Debug.fail(object.toString());
    }

    static void assertIdle(List<Object> exceptions) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        assertCurrentIsEmpty();

        for (RefEqual key : _map.keySet()) {
            boolean ok = false;

            for (int i = 0; i < exceptions.size(); i++)
                if (key.getObject() == exceptions.get(i))
                    ok = true;

            if (!ok)
                Debug.fail("not empty: " + key.getObject());
        }

        for (RefEqual key : _shared.keySet()) {
            boolean ok = false;

            synchronized (_sharedDefinitively) {
                if (_sharedDefinitively.containsKey(key))
                    ok = true;
            }

            for (int i = 0; i < exceptions.size(); i++)
                if (key.getObject() == exceptions.get(i))
                    ok = true;

            if (!ok)
                Debug.fail(_shared.toString() + Platform.get().lineSeparator() + key.getObject().toString());
        }

        Debug.assertion(_exchangedObjects.size() == 0);
    }

    static void addPrivate(Object object) {
        Debug.assertion(!(object instanceof List));
        Debug.assertion(add(object));
    }

    static void addPrivateList(List<Object> list) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        for (int i = 0; i < list.size(); i++)
            Debug.assertion(add(list.get(i)));
    }

    static boolean addPrivateIfNot(Object object) {
        return add(object);
    }

    private static boolean add(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("add(" + Platform.get().defaultToString(object) + ")");

        ThreadAssert current = getOrCreateCurrent();
        RefEqual wrapper = new RefEqual(object);
        ThreadAssert previous = _map.putIfAbsent(wrapper, current);
        Debug.assertion(previous == null || previous == current);

        if (previous == null) {
            Debug.assertion(current._objects.putIfAbsent(wrapper, object) == null);
            return true;
        }

        return false;
    }

    static void assertPrivate(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("assertPrivate(" + Platform.get().defaultToString(object) + ")");

        Debug.assertion(isPrivate(object));
    }

    static void assertPrivateOrShared(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("assertPrivateOrShared(" + Platform.get().defaultToString(object) + ")");

        if (!isPrivate(object))
            assertShared(object);
    }

    static boolean isPrivate(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert current = _current.get();

        if (current != null) {
            Debug.assertion(current._owner.get() == getOwnerKey());
            RefEqual wrapper = new RefEqual(object);

            if (_map.get(wrapper) == current) {
                Debug.assertion(current._objects.get(wrapper) == object);
                return true;
            }
        }

        return false;
    }

    static void removePrivate(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("remove(" + Platform.get().defaultToString(object) + ")");

        Debug.assertion(!(object instanceof List));
        ThreadAssert current = _current.get();
        Debug.assertion(current._owner.get() == getOwnerKey());
        RefEqual wrapper = new RefEqual(object);
        ThreadAssert previous = _map.remove(wrapper);
        Debug.assertion(previous == current);
        Debug.assertion(current._objects.remove(wrapper) == object);
    }

    static void removePrivateList(List<Object> list) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        for (int i = 0; i < list.size(); i++)
            removePrivate(list.get(i));
    }

    //

    static void share(Object object) {
        removePrivate(object);
        RefEqual wrapper = new RefEqual(object);
        Debug.assertion(_shared.putIfAbsent(wrapper, object) == null);
    }

    static void assertShared(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        RefEqual wrapper = new RefEqual(object);

        if (!_shared.containsKey(wrapper)) {
            synchronized (_sharedDefinitively) {
                Debug.assertion(_sharedDefinitively.containsKey(object));
            }
        }
    }

    static void removeShared(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        RefEqual wrapper = new RefEqual(object);
        Debug.assertion(_shared.remove(wrapper) == object);
    }

    static void addSharedDefinitively(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        synchronized (_sharedDefinitively) {
            Object previous = _sharedDefinitively.put(object, null);
            Debug.assertion(previous == null);
        }
    }

    //

    static void assertCleaned(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("assertCleaned(" + Platform.get().defaultToString(object) + ")");

        RefEqual wrapper = new RefEqual(object);
        Debug.assertion(_map.get(wrapper) == null);

        ThreadAssert current = _current.get();

        if (current != null) {
            Debug.assertion(current._owner.get() == getOwnerKey());
            Debug.assertion(current._objects.get(wrapper) == null);
        }

        Debug.assertion(_shared.get(wrapper) == null);
    }

    //

    static void exchangeGive(Object key, Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        Debug.assertion(!(object instanceof List));
        removePrivate(object);

        synchronized (_exchangedObjects) {
            RefEqual wrapper = new RefEqual(key);
            List<Object> queue = _exchangedObjects.get(wrapper);

            if (queue == null)
                _exchangedObjects.put(wrapper, queue = new List<Object>());

            queue.add(object);
        }
    }

    static void exchangeGiveList(Object key, List<Object> list) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        for (int i = 0; i < list.size(); i++)
            exchangeGive(key, list.get(i));
    }

    static List<Object> exchangeTake(Object key) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        List<Object> queue = null;

        synchronized (_exchangedObjects) {
            RefEqual wrapper = new RefEqual(key);
            queue = _exchangedObjects.remove(wrapper);
        }

        if (queue != null)
            for (int i = 0; i < queue.size(); i++)
                addPrivate(queue.get(i));

        return queue;
    }

    /*
     * Whole context operations, used also to debug non thread related stuff.
     */

    static void suspend(Object key) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("suspend(" + key + ")");

        ThreadAssert context = getOrCreateCurrent();
        Debug.assertion(context._owner.compareAndSet(getOwnerKey(), null));
        RefEqual wrapper = new RefEqual(key);
        ThreadAssert previous = _suspendedContexts.put(wrapper, context);
        Debug.assertion(previous == null);
        _current.set(null);
    }

    static void resume(Object key) {
        resume(key, true);
    }

    static void resume(Object key, boolean assertExists) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("resume(" + key + ")");

        if (Debug.THREADS)
            assertCurrentIsEmpty();

        RefEqual wrapper = new RefEqual(key);
        ThreadAssert context = _suspendedContexts.remove(wrapper);

        if (Debug.ENABLED && assertExists)
            Debug.assertion(context != null);

        if (context != null) {
            Debug.assertion(context._owner.compareAndSet(null, getOwnerKey()));
            _current.set(context);
        }
    }

    //

    ArrayList<Boolean> getOverrideAssertBools() {
        return _overrideAssertBools;
    }

    ArrayList<Object> getOverrideAssertKeys() {
        return _overrideAssertKeys;
    }

    //

    long getReaderDebugCounter(Object reader) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Long value = _readersDebugCounters.get(reader);
        return value != null ? value : 0;
    }

    long getAndIncrementReaderDebugCounter(Object reader) {
        long value = getReaderDebugCounter(reader);
        _readersDebugCounters.put(reader, value + 1);
        return value;
    }

    void resetReaderDebugCounter(Object reader) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        _readersDebugCounters.remove(reader);
    }

    //

    long getWriterDebugCounter(Object writer) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Long value = _writersDebugCounters.get(writer);
        return value != null ? value : 0;
    }

    long getAndIncrementWriterDebugCounter(Object writer) {
        long value = getWriterDebugCounter(writer);
        _writersDebugCounters.put(writer, value + 1);
        return value;
    }

    void resetWriterDebugCounter(Object writer) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        _writersDebugCounters.remove(writer);
    }

    //

    void resetCounters() {
        _readersDebugCounters.clear();
        _writersDebugCounters.clear();
    }
}
