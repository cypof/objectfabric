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

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectfabric.Continuation.IntBox;
import org.objectfabric.Memory.DefaultBackend;
import org.objectfabric.TObject.Transaction;
import org.objectfabric.TObject.Version;

/**
 * Utility functions and debug helper.
 */
@SuppressWarnings("rawtypes")
final class Helper {

    private static final Helper _instance;

    String ProcessName = "";

    boolean ConflictRandom, ConflictAlways;

    boolean TestingReset, FailReset, LastResetFailed;

    boolean AssertNoConflict;

    final PlatformConcurrentMap<Resource, Resource> Resources = new PlatformConcurrentMap<Resource, Resource>();

    final PlatformConcurrentMap<Memory, DefaultBackend> Memories = new PlatformConcurrentMap<Memory, DefaultBackend>();

    private final PlatformMap<VersionMap, List<Object>> _watchers;

    private final PlatformConcurrentMap<RefEqual, PlatformConcurrentMap<VersionMap, VersionMap>> _validated;

    private final PlatformConcurrentMap<Object, Object> _callbacks;

    private final PlatformConcurrentMap<RefEqual, RefEqual> _toRecycle = new PlatformConcurrentMap<RefEqual, RefEqual>();

    private final PlatformThreadLocal<Integer> _retryCount;

    private final PlatformThreadLocal<Integer> _expectedClass;

    private final PlatformThreadLocal<Boolean> _disableEqualsOrHashCheck;

    private final PlatformThreadLocal<StringBuilder> _sb;

    private final PlatformConcurrentMap<Transaction, Transaction> _allowExistingReadsOrWrites;

    private final PlatformConcurrentMap<Buff, IntBox> _lock = new PlatformConcurrentMap<Buff, IntBox>();

    private final PlatformConcurrentMap<Connection, AtomicInteger> _readers = new PlatformConcurrentMap<Connection, AtomicInteger>();

    static final Snapshot TO_MERGE_LATER_IS_NULL = new Snapshot();

    /*
     * TODO: replace by additional CAS in VersionMap to hand merge to thread currently
     * merging to map.
     */
    private final PlatformConcurrentMap<VersionMap, Snapshot> _toMergeLater;

    // TODO move all debug variables in classes to thread locals here

    Transaction _empty;

    private Helper() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        _watchers = new PlatformMap<VersionMap, List<Object>>();
        _validated = new PlatformConcurrentMap<RefEqual, PlatformConcurrentMap<VersionMap, VersionMap>>();
        _callbacks = new PlatformConcurrentMap<Object, Object>();
        _retryCount = new PlatformThreadLocal<Integer>();
        _expectedClass = new PlatformThreadLocal<Integer>();
        _disableEqualsOrHashCheck = new PlatformThreadLocal<Boolean>();
        _sb = new PlatformThreadLocal<StringBuilder>();
        _allowExistingReadsOrWrites = new PlatformConcurrentMap<Transaction, Transaction>();
        _toMergeLater = new PlatformConcurrentMap<VersionMap, Snapshot>();
    }

    static {
        if (Debug.ENABLED)
            _instance = new Helper();
        else
            _instance = null;
    }

    static Helper instance() {
        return _instance;
    }

    static VersionMap[] addVersionMap(VersionMap[] array, VersionMap map) {
        VersionMap[] update = new VersionMap[array.length + 1];

        // Copy maps already acknowledged

        Platform.arraycopy(array, 0, update, 0, array.length);

        // Insert new map after last acknowledged one

        update[update.length - 1] = map;

        // Search nulls to assert indexes, races etc...

        if (Debug.ENABLED)
            Debug.assertion(!Arrays.asList(update).contains(null));

        return update;
    }

    static VersionMap[] removeVersionMap(VersionMap[] array, int index) {
        VersionMap[] update = new VersionMap[array.length - 1];
        Platform.arraycopy(array, 0, update, 0, index);
        Platform.arraycopy(array, index + 1, update, index, array.length - 1 - index);

        // Search nulls to assert indexes, races etc...

        if (Debug.ENABLED)
            Debug.assertion(!Arrays.asList(update).contains(null));

        return update;
    }

    static Version[][] addVersions(Version[][] array, Version[] versions) {
        Version[][] update = new Version[array.length + 1][];

        // Copy version arrays already acknowledged
        Platform.arraycopy(array, 0, update, 0, array.length);

        // Insert new array after last acknowledged one
        update[update.length - 1] = versions;

        return update;
    }

    static Version[][] removeVersions(Version[][] array, int index) {
        Version[][] update = new Version[array.length - 1][];
        Platform.arraycopy(array, 0, update, 0, index);
        Platform.arraycopy(array, index + 1, update, index, array.length - 1 - index);
        return update;
    }

    /**
     * TODO: hash or sort?
     */
    static int getIndex(Snapshot snapshot, VersionMap map) {
        for (int i = snapshot.getVersionMaps().length - 1; i >= 0; i--)
            if (snapshot.getVersionMaps()[i] == map)
                return i;

        throw new IllegalStateException();
    }

    static boolean validateCheckOnce(VersionMap map, Version[] reads, Snapshot snapshot, int start, int stop) {
        if (Debug.ENABLED) {
            Debug.assertion(start >= 0 && stop >= 0 && start <= stop);

            /*
             * Check we do not validate twice against the same map.
             */
            RefEqual wrapper = new RefEqual(map);

            for (int i = start; i < stop; i++) {
                PlatformConcurrentMap<VersionMap, VersionMap> maps = instance()._validated.get(wrapper);

                if (maps == null) {
                    maps = new PlatformConcurrentMap<VersionMap, VersionMap>();
                    Debug.assertion(instance()._validated.put(wrapper, maps) == null);
                }

                VersionMap m = snapshot.getVersionMaps()[i];
                Debug.assertion(maps.put(m, m) == null);
            }
        }

        return validate(map, reads, snapshot, start, stop);
    }

    static boolean validate(VersionMap map, Version[] reads, Snapshot snapshot, int start, int stop) {
        if (start < stop)
            if (reads != null)
                for (int i = reads.length - 1; i >= 0; i--)
                    if (reads[i] != null)
                        if (!reads[i].validAgainst(map, snapshot, start, stop))
                            return false;

        return true;
    }

    //

    static Extension[] add(Extension[] array, Extension value) {
        if (Debug.ENABLED)
            Debug.assertion(!contains(array, value));

        Extension[] update;

        if (array != null) {
            update = new Extension[array.length + 1];
            Platform.arraycopy(array, 0, update, 0, array.length);
            update[update.length - 1] = value;
        } else {
            update = new Extension[1];
            update[0] = value;
        }

        return update;
    }

    static Extension[] remove(Extension[] array, Extension value) {
        if (Debug.ENABLED)
            Debug.assertion(contains(array, value));

        if (array.length == 1)
            return null;

        Extension[] update = new Extension[array.length - 1];
        int j = 0;

        for (int i = 0; i < array.length; i++)
            if (array[i] != value)
                update[j++] = array[i];

        if (Debug.ENABLED)
            Debug.assertion(j == update.length);

        return update;
    }

    static boolean contains(Extension[] array, Extension value) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (array == null)
            return false;

        for (int i = 0; i < array.length; i++)
            if (array[i] == value)
                return true;

        return false;
    }

    static Actor[] add(Actor[] array, Actor value) {
        if (Debug.ENABLED)
            Debug.assertion(!contains(array, value));

        Actor[] update;

        if (array != null) {
            update = new Actor[array.length + 1];
            Platform.arraycopy(array, 0, update, 0, array.length);
            update[update.length - 1] = value;
        } else {
            update = new Actor[1];
            update[0] = value;
        }

        return update;
    }

    static Actor[] remove(Actor[] array, Actor value) {
        if (Debug.ENABLED)
            Debug.assertion(contains(array, value));

        if (array.length == 1)
            return null;

        Actor[] update = new Actor[array.length - 1];
        int j = 0;

        for (int i = 0; i < array.length; i++)
            if (array[i] != value)
                update[j++] = array[i];

        if (Debug.ENABLED)
            Debug.assertion(j == update.length);

        return update;
    }

    private static boolean contains(Actor[] array, Actor value) {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        if (array == null)
            return false;

        for (int i = 0; i < array.length; i++)
            if (array[i] == value)
                return true;

        return false;
    }

    static URIHandler[] add(URIHandler[] array, URIHandler value) {
        URIHandler[] update;

        if (array != null) {
            update = new URIHandler[array.length + 1];
            Platform.arraycopy(array, 0, update, 0, array.length);
            update[update.length - 1] = value;
        } else {
            update = new URIHandler[1];
            update[0] = value;
        }

        return update;
    }

    static URIHandler[] add(int index, URIHandler[] array, URIHandler value) {
        URIHandler[] update;

        if (array != null) {
            update = new URIHandler[array.length + 1];
            Platform.arraycopy(array, 0, update, 0, index);
            update[index] = value;
            Platform.arraycopy(array, index, update, index + 1, array.length - index);
        } else {
            update = new URIHandler[1];
            update[index] = value;
        }

        return update;
    }

    static Location[] add(Location[] array, Location value) {
        Location[] update;

        if (array != null) {
            update = new Location[array.length + 1];
            Platform.arraycopy(array, 0, update, 0, array.length);
            update[update.length - 1] = value;
        } else {
            update = new Location[1];
            update[0] = value;
        }

        return update;
    }

    //

    static int[] extend(int[] array) {
        int[] result = new int[array.length << OpenMap.TIMES_TWO_SHIFT];
        Platform.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    static long[] extend(long[] array) {
        long[] result = new long[array.length << OpenMap.TIMES_TWO_SHIFT];
        Platform.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    static TObject[] extend(TObject[] array) {
        TObject[] temp = new TObject[array.length << OpenMap.TIMES_TWO_SHIFT];
        Platform.arraycopy(array, 0, temp, 0, array.length);
        return temp;
    }

    static Resource[] extend(Resource[] array) {
        Resource[] temp = new Resource[array.length << OpenMap.TIMES_TWO_SHIFT];
        Platform.arraycopy(array, 0, temp, 0, array.length);
        return temp;
    }

    static TObject[][] extend(TObject[][] array) {
        TObject[][] temp = new TObject[array.length << OpenMap.TIMES_TWO_SHIFT][];
        Platform.arraycopy(array, 0, temp, 0, array.length);
        return temp;
    }

    /*
     * Debug.
     */

    void removeValidated(VersionMap map) {
        RefEqual wrapper = new RefEqual(map);
        _validated.remove(wrapper);
    }

    void checkFieldsHaveDefaultValues(final Transaction transaction) {
        if (_empty == null) {
            _empty = new Transaction(transaction.workspace(), null);

            if (Debug.THREADS)
                ThreadAssert.removePrivate(_empty);
        }

        boolean ok = true;

        try {
            ok &= Platform.get().shallowEquals(_empty, transaction, Platform.get().transactionBaseClass(), "_workspace", "_parent");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (!TestingReset)
            Debug.assertion(ok);
        else {
            if (FailReset)
                LastResetFailed = true;
            else
                LastResetFailed = false;
        }
    }

    //

    int getExpectedClass() {
        return _expectedClass.get();
    }

    void setExpectedClass(int value) {
        _expectedClass.set(value);
    }

    boolean allowEqualsOrHash() {
        Boolean value = _disableEqualsOrHashCheck.get();

        if (value != null && value)
            return true;

        return false;
    }

    void disableEqualsOrHashCheck() {
        _disableEqualsOrHashCheck.set(true);
    }

    void enableEqualsOrHashCheck() {
        _disableEqualsOrHashCheck.set(false);
    }

    StringBuilder getSB() {
        StringBuilder sb = _sb.get();

        if (sb == null)
            _sb.set(sb = new StringBuilder());

        return sb;
    }

    void assertClassLoaderIdle() {
        Debug.assertion(_disableEqualsOrHashCheck.get() == null || !_disableEqualsOrHashCheck.get());
        List<Workspace> workspaces = new List<Workspace>();

        synchronized (_watchers) {
            for (List<Object> value : _watchers.values()) {
                Workspace workspace = (Workspace) value.get(0);

                if (workspace != null) {
                    Debug.assertion(!workspaces.contains(workspace));
                    workspaces.add(workspace);
                }
            }
        }

        for (int i = 0; i < workspaces.size(); i++)
            assertWorkspaceIdle(workspaces.get(i));

        if (Debug.THREADS) {
            for (int i = 0; i < workspaces.size(); i++) {
                Snapshot snapshot = workspaces.get(i).snapshot();
                ThreadAssert.removeShared(snapshot.getVersionMaps()[TransactionManager.OBJECTS_VERSIONS_INDEX]);
            }

            List<Object> exceptions = new List<Object>();

            for (ThreadContext instance : ThreadContext.getInstances())
                exceptions.addAll(instance.getThreadContextObjects());

            ThreadAssert.assertIdle(exceptions);
        }

        synchronized (_watchers) {
            _watchers.clear();
        }

        Debug.assertAlways(InFlight.idle());

        for (Entry<Memory, DefaultBackend> entry : Memories.entrySet())
            for (Object o : entry.getValue().Map.values())
                entry.getKey().onEviction(o);

        Memories.clear();

        for (WeakReference<Remote> ref : ClientURIHandler.remotes().values()) {
            Remote remote = ref.get();

            if (remote != null)
                for (URI uri : remote.uris().values())
                    Debug.assertion(uri.resources() == null);
        }

        if (Stats.ENABLED) {
            Debug.assertion(Stats.Instance.ConnectionQueues.get() == 0);
            Debug.assertion(Stats.Instance.BlockQueues.get() == 0);
        }

        Debug.assertion(Resources.size() == 0);
        Debug.assertion(_validated.size() == 0);
        Debug.assertion(_callbacks.size() == 0);
        Debug.assertion(_toRecycle.size() == 0);
        Debug.assertion(_lock.size() == 0);

        _readers.clear();

        ThreadAssert.getOrCreateCurrent().resetCounters();
        Debug.assertion(!AssertNoConflict);
        Debug.assertion(!ExpectedExceptionThrower.isCounterDisabled());
        Debug.assertion(ProcessName.length() == 0);
    }

    void assertWorkspaceIdle(Workspace workspace) {
        workspace.assertIdle();

        Snapshot snapshot = workspace.snapshot();
        Debug.assertion(snapshot.getVersionMaps().length == 2);
        Debug.assertion(snapshot.getVersionMaps()[1] == VersionMap.CLOSING);
        Debug.assertion(snapshot.getReads() == null);
        Debug.assertion(snapshot.writes().length == 2);
        Debug.assertion(snapshot.writes()[0] == TransactionManager.OBJECTS_VERSIONS);
        Debug.assertion(snapshot.writes()[1] == null);
        Debug.assertion(snapshot.getVersionMaps()[0].getTransaction() == null);

        if (snapshot.slowChanging() != null) {
            Debug.assertion(snapshot.slowChanging().Extensions == null);
            Debug.assertion(snapshot.slowChanging().Splitters == null);
        }

        for (Origin origin : workspace.resolver().origins().keySet())
            for (URI uri : origin.uris().values())
                Debug.assertion(!uri.contains(workspace));

        for (Resource resource : Resources.keySet()) {
            if (resource.workspaceImpl() == workspace) {
                resource.assertIdle();
                Resources.remove(resource);
            }
        }

        synchronized (_watchers) {
            Debug.assertion(_watchers.containsKey(snapshot.getVersionMaps()[0]));
        }
    }

    void toRecycle(Object object) {
        RefEqual wrapper = new RefEqual(object);
        RefEqual previous = _toRecycle.put(wrapper, wrapper);

        if (Debug.ENABLED)
            Debug.assertion(previous == null);
    }

    void onRecycled(Object object) {
        RefEqual previous = _toRecycle.remove(new RefEqual(object));

        if (Debug.ENABLED) {
            Debug.assertion(previous != null);
            Debug.assertion(!_toRecycle.containsKey(new RefEqual(object)));
        }
    }

    void addWatcher(VersionMap map, Object watcher, Snapshot snapshot, String reason) {
        if (Debug.ENABLED)
            Debug.assertion(watcher != null);

        Object[] watchers;

        synchronized (_watchers) {
            List<Object> list = _watchers.get(map);

            if (list == null) {
                list = new List<Object>();
                Object previous = _watchers.get(map);
                _watchers.put(map, list);
                Debug.assertion(previous == null);
            }

            list.add(watcher);

            if (Debug.STM_LOG)
                watchers = list.toArray();
        }

        if (Debug.STM_LOG)
            Log.write("+1 " + (watchers.length) + " on map " + map + ", queue: " + snapshot.getVersionMaps().length + " (" + watcher + "): " + reason + " (" + Arrays.toString(watchers) + ")");
    }

    void removeWatcher(VersionMap map, Object watcher, Snapshot snapshot, String reason) {
        if (Debug.ENABLED)
            Debug.assertion(watcher != null);

        Object[] watchers;

        synchronized (_watchers) {
            List<Object> list = _watchers.get(map);
            int index = -1;

            for (int i = 0; i < list.size(); i++)
                if (list.get(i) == watcher)
                    index = i;

            list.remove(index);

            if (Debug.STM_LOG)
                watchers = list.toArray();

            if (list.size() == 0)
                _watchers.remove(map);
        }

        if (Debug.STM_LOG)
            Log.write("-1 " + (watchers.length) + " on map " + map + ", queue: " + snapshot.getVersionMaps().length + " (" + watcher + "): " + reason + " (" + Arrays.toString(watchers) + ")");
    }

    //

    int getRetryCount() {
        Integer boxed = _retryCount.get();

        if (boxed != null)
            return boxed;

        return 0;
    }

    void setRetryCount(int value) {
        _retryCount.set(value);
    }

    void checkVersionsHaveWrites(TObject.Version[] versions) {
        for (int i = versions.length - 1; i >= 0; i--) {
            if (versions[i] != null) {
                List<Object> list = new List<Object>();
                versions[i].getContentForDebug(list);
                Debug.assertion(list.size() > 0);
            }
        }
    }

    PlatformConcurrentMap<Transaction, Transaction> getAllowExistingReadsOrWrites() {
        return _allowExistingReadsOrWrites;
    }

    PlatformConcurrentMap<VersionMap, Snapshot> getToMergeLater() {
        return _toMergeLater;
    }

    PlatformConcurrentMap<Buff, IntBox> getLocks() {
        return _lock;
    }

    /*
     * Little state machine to assert readers closing sequence.
     */
    static final int IDLE = 0, READING = 1, CLOSE_WHEN_DONE = 2, CLOSED = 3;

    private AtomicInteger getRead(Connection connection) {
        AtomicInteger state = _readers.get(connection);

        if (state == null) {
            AtomicInteger previous = _readers.putIfAbsent(connection, state = new AtomicInteger());

            if (previous != null)
                state = previous;
        }

        return state;
    }

    boolean startRead(Connection connection) {
        return getRead(connection).compareAndSet(IDLE, READING);
    }

    boolean stopRead(Connection connection) {
        AtomicInteger state = getRead(connection);
        boolean result = state.compareAndSet(READING, IDLE);

        if (!result)
            Debug.assertion(state.get() == CLOSE_WHEN_DONE);

        return result;
    }

    void assertReadClosing(Connection connection) {
        int state = getRead(connection).get();
        Debug.assertion(state == CLOSE_WHEN_DONE || state == CLOSED, "" + state);
    }

    boolean closeRead(Connection connection) {
        AtomicInteger state = getRead(connection);

        for (;;) {
            int value = state.get();

            switch (value) {
                case IDLE: {
                    if (state.compareAndSet(value, CLOSED))
                        return true;

                    break;
                }
                case READING: {
                    if (state.compareAndSet(value, CLOSE_WHEN_DONE))
                        return false;

                    break;
                }
                case CLOSE_WHEN_DONE:
                case CLOSED:
                    return false;
            }
        }
    }

    //

    void addCallback(Object callback) {
        Debug.assertion(_callbacks.putIfAbsent(callback, callback) == null);
    }

    void removeCallback(Object callback) {
        Debug.assertion(_callbacks.remove(callback) == callback);
    }
}