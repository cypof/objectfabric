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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final PlatformThreadLocal<ThreadAssert> _current;

    private static final PlatformConcurrentMap<IdentityEqualityWrapper, ThreadAssert> _map;

    private static final PlatformConcurrentMap<IdentityEqualityWrapper, Object> _shared;

    private static final HashMap<IdentityEqualityWrapper, ArrayList<Object>> _exchangedObjects;

    private static final PlatformConcurrentMap<IdentityEqualityWrapper, ThreadAssert> _suspendedContexts;

    private static final PlatformThreadLocal<Object> _ownerKey;

    private static final WeakHashMap<Object, Object> _sharedDefinitively;

    private final AtomicReference<Object> _owner = new AtomicReference<Object>();

    private final PlatformConcurrentMap<IdentityEqualityWrapper, Object> _objects = new PlatformConcurrentMap<IdentityEqualityWrapper, Object>();

    // Various debug helpers

    private final ArrayList<Boolean> _overrideAssertBools = new ArrayList<Boolean>();

    private final ArrayList<Object> _overrideAssertKeys = new ArrayList<Object>();

    private final HashMap<Object, Long> _readersDebugCounters = new HashMap<Object, Long>();

    private final HashMap<Object, Long> _writersDebugCounters = new HashMap<Object, Long>();

    static {
        if (Debug.ENABLED) {
            _current = new PlatformThreadLocal<ThreadAssert>();
            _map = new PlatformConcurrentMap<IdentityEqualityWrapper, ThreadAssert>();
            _shared = new PlatformConcurrentMap<IdentityEqualityWrapper, Object>();
            _sharedDefinitively = new WeakHashMap<Object, Object>();
            _exchangedObjects = new HashMap<IdentityEqualityWrapper, ArrayList<Object>>();
            _suspendedContexts = new PlatformConcurrentMap<IdentityEqualityWrapper, ThreadAssert>();
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

    public static ThreadAssert getOrCreateCurrent() {
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
    private static final Object getOwnerKey() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Object key = _ownerKey.get();

        if (key == null)
            _ownerKey.set(key = new Object());

        return key;
    }

    public static void assertCurrentIsEmpty() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert current = _current.get();

        if (current != null && current._objects.size() > 0) {
            Log.write(current._objects.toString());
            Debug.fail();
        }
    }

    public static void assertIdle(Object... exceptions) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        assertCurrentIsEmpty();

        Debug.assertion(_map.isEmpty());

        for (IdentityEqualityWrapper key : _shared.keySet()) {
            boolean ok = false;

            synchronized (_sharedDefinitively) {
                if (_sharedDefinitively.containsKey(key))
                    ok = true;
            }

            for (Object exception : exceptions)
                if (key.getObject() == exception)
                    ok = true;

            if (!ok) {
                Log.write(_shared.toString());
                Log.write(key.getObject().toString());
                Debug.fail();
            }
        }

        Debug.assertion(_exchangedObjects.isEmpty());
    }

    public static void addPrivate(Object object) {
        Debug.assertion(add(object));
    }

    public static boolean addPrivateIfNot(Object object) {
        return add(object);
    }

    private static boolean add(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("add(" + object.getClass().getSimpleName() + ")");

        ThreadAssert current = getOrCreateCurrent();
        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(object);
        ThreadAssert previous = _map.putIfAbsent(wrapper, current);
        Debug.assertion(previous == null || previous == current);

        if (previous == null) {
            Debug.assertion(current._objects.putIfAbsent(wrapper, object) == null);
            return true;
        }

        return false;
    }

    public static void assertPrivate(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("assertPrivate(" + object.getClass().getSimpleName() + ")");

        Debug.assertion(isPrivate(object));
    }

    public static void assertPrivateOrShared(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("assertPrivateOrShared(" + object.getClass().getSimpleName() + ")");

        if (!isPrivate(object))
            assertShared(object);
    }

    public static boolean isPrivate(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert current = _current.get();

        if (current != null) {
            Debug.assertion(current._owner.get() == getOwnerKey());
            IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(object);

            if (_map.get(wrapper) == current) {
                Debug.assertion(current._objects.get(wrapper) == object);
                return true;
            }
        }

        return false;
    }

    public static void removePrivate(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("remove(" + object.getClass().getSimpleName() + ")");

        ThreadAssert current = _current.get();
        Debug.assertion(current._owner.get() == getOwnerKey());
        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(object);
        ThreadAssert previous = _map.remove(wrapper);
        Debug.assertion(previous == current);
        Debug.assertion(current._objects.remove(wrapper) == object);
    }

    public static void removePrivateList(List<Object> objects) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        for (int i = 0; i < objects.size(); i++)
            removePrivate(objects.get(i));
    }

    //

    public static void addShared(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(object);
        Debug.assertion(_shared.putIfAbsent(wrapper, object) == null);
    }

    public static void assertShared(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(object);

        if (!_shared.containsKey(wrapper)) {
            synchronized (_sharedDefinitively) {
                Debug.assertion(_sharedDefinitively.containsKey(object));
            }
        }
    }

    public static void removeShared(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(object);
        Debug.assertion(_shared.remove(wrapper) == object);
    }

    public static void addSharedDefinitively(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        synchronized (_sharedDefinitively) {
            Object previous = _sharedDefinitively.put(object, null);
            Debug.assertion(previous == null);
        }
    }

    //

    public static void assertCleaned(Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("assertCleaned(" + object.getClass().getSimpleName() + ")");

        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(object);
        Debug.assertion(_map.get(wrapper) == null);

        ThreadAssert current = _current.get();

        if (current != null) {
            Debug.assertion(current._owner.get() == getOwnerKey());
            Debug.assertion(current._objects.get(wrapper) == null);
        }

        Debug.assertion(_shared.get(wrapper) == null);
    }

    //

    public static void exchangeGive(Object key, Object object) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        removePrivate(object);

        synchronized (_exchangedObjects) {
            IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(key);
            ArrayList<Object> queue = _exchangedObjects.get(wrapper);

            if (queue == null)
                _exchangedObjects.put(wrapper, queue = new ArrayList<Object>());

            queue.add(object);
        }
    }

    public static void exchangeGiveList(Object key, List<Object> objects) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        for (int i = 0; i < objects.size(); i++)
            exchangeGive(key, objects.get(i));
    }

    public static ArrayList<Object> exchangeTake(Object key) {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ArrayList<Object> queue = null;

        synchronized (_exchangedObjects) {
            IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(key);
            queue = _exchangedObjects.remove(wrapper);
        }

        if (queue != null)
            for (Object object : queue)
                addPrivate(object);

        return queue;
    }

    /*
     * Whole context operations, used also to debug non thread related stuff.
     */

    public static void suspend(Object key) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("suspend(" + key + ")");

        ThreadAssert context = getOrCreateCurrent();
        Debug.assertion(context._owner.compareAndSet(getOwnerKey(), null));
        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(key);
        Debug.assertion(_suspendedContexts.put(wrapper, context) == null);
        _current.set(null);
    }

    public static void resume(Object key) {
        resume(key, true);
    }

    public static void resume(Object key, boolean assertExists) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Debug.THREADS_LOG)
            Log.write("resume(" + key + ")");

        Debug.assertion(_current.get() == null || _current.get()._objects.size() == 0);
        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(key);
        ThreadAssert context = _suspendedContexts.remove(wrapper);

        if (Debug.ENABLED && assertExists)
            Debug.assertion(context != null);

        if (context != null) {
            Debug.assertion(context._owner.compareAndSet(null, getOwnerKey()));
            _current.set(context);
        }
    }

    // public static void merge(Object key) {
    // if (!Debug.THREADS)
    // throw new IllegalStateException();
    //
    // if (Debug.THREADS_LOG)
    // Log.write("merge(" + key + ")");
    //
    // Object temp = new Object();
    // suspend(temp);
    // resume(key);
    // ThreadContext source = _current.get();
    // ArrayList<IdentityEqualityWrapper> wrappers = new
    // ArrayList<IdentityEqualityWrapper>(source._objects.keySet());
    //
    // for (IdentityEqualityWrapper wrapper : wrappers)
    // removePrivate(wrapper.getObject());
    //
    // Debug.assertion(source._objects.size() == 0);
    // resume(temp);
    //
    // for (IdentityEqualityWrapper wrapper : wrappers)
    // addPrivate(wrapper.getObject());
    // }

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
