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

package com.objectfabric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.objectfabric.Interception.MultiMapInterception;
import com.objectfabric.Snapshot.SlowChanging;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.IdentityEqualityWrapper;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformConcurrentMap;
import com.objectfabric.misc.PlatformThreadLocal;
import com.objectfabric.misc.ThreadAssert;

/**
 * Utility functions and debug helper.
 */
final class Helper {

    private static final Helper _instance;

    public boolean ConflictRandom, ConflictAlways;

    public boolean FailReset, LastResetFailed;

    private final HashMap<VersionMap, ArrayList<Object>> _watchers;

    private final PlatformConcurrentMap<IdentityEqualityWrapper, PlatformConcurrentMap<VersionMap, VersionMap>> _validated;

    private final PlatformConcurrentMap<Object, Object> _callbacks;

    private final PlatformConcurrentMap<IdentityEqualityWrapper, IdentityEqualityWrapper> _createdNotRecycled = new PlatformConcurrentMap<IdentityEqualityWrapper, IdentityEqualityWrapper>();

    private final PlatformThreadLocal<Integer> _retryCount;

    private final PlatformConcurrentMap<Reader, Boolean> _readers;

    private final PlatformConcurrentMap<Writer, Boolean> _writers;

    private final PlatformConcurrentMap<Reader, Reader> _allowMultipleCreations;

    private final PlatformThreadLocal<Integer> _expectedClass;

    private final PlatformThreadLocal<Boolean> _disableEqualsOrHashCheck;

    private final PlatformThreadLocal<StringBuilder> _sb, _sb2;

    private final PlatformConcurrentMap<Transaction, Transaction> _allowExistingReadsOrWrites;

    private final PlatformThreadLocal<Boolean> _noTransaction;

    public static final Snapshot TO_MERGE_LATER_IS_NULL = new Snapshot();

    /*
     * TODO: replace by additional CAS in VersionMap to hand merge to thread currently
     * merging to map.
     */
    private final PlatformConcurrentMap<VersionMap, Snapshot> _toMergeLater;

    // TODO move all debug variables in classes to thread locals here

    public Transaction _empty;

    private Helper() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        _watchers = new HashMap<VersionMap, ArrayList<Object>>();
        _validated = new PlatformConcurrentMap<IdentityEqualityWrapper, PlatformConcurrentMap<VersionMap, VersionMap>>();
        _callbacks = new PlatformConcurrentMap<Object, Object>();
        _retryCount = new PlatformThreadLocal<Integer>();
        _readers = new PlatformConcurrentMap<Reader, Boolean>();
        _writers = new PlatformConcurrentMap<Writer, Boolean>();
        _allowMultipleCreations = new PlatformConcurrentMap<Reader, Reader>();
        _expectedClass = new PlatformThreadLocal<Integer>();
        _disableEqualsOrHashCheck = new PlatformThreadLocal<Boolean>();
        _sb = new PlatformThreadLocal<StringBuilder>();
        _sb2 = new PlatformThreadLocal<StringBuilder>();
        _allowExistingReadsOrWrites = new PlatformConcurrentMap<Transaction, Transaction>();
        _toMergeLater = new PlatformConcurrentMap<VersionMap, Snapshot>();
        _noTransaction = new PlatformThreadLocal<Boolean>();
    }

    static {
        if (Debug.ENABLED)
            _instance = new Helper();
        else
            _instance = null;
    }

    public static Helper getInstance() {
        return _instance;
    }

    public static final VersionMap[] addVersionMap(VersionMap[] array, VersionMap map) {
        VersionMap[] newArray = new VersionMap[array.length + 1];

        // Copy maps already acknowledged

        PlatformAdapter.arraycopy(array, 0, newArray, 0, array.length);

        // Insert new map after last acknowledged one

        newArray[newArray.length - 1] = map;

        // Search nulls to assert indexes, races etc...

        if (Debug.ENABLED)
            Debug.assertion(!Arrays.asList(newArray).contains(null));

        return newArray;
    }

    public static final Version[][] addVersions(Version[][] array, Version[] versions) {
        Version[][] newArray = addReads(array, versions);

        if (Debug.ENABLED) // Search nulls to assert indexes, races etc...
            Debug.assertion(!Arrays.asList(newArray).contains(null));

        return newArray;
    }

    public static final Version[][] addReads(Version[][] array, Version[] reads) {
        Version[][] newArray = new Version[array.length + 1][];

        // Copy version arrays already acknowledged
        PlatformAdapter.arraycopy(array, 0, newArray, 0, array.length);

        // Insert new array after last acknowledged one
        newArray[newArray.length - 1] = reads;

        return newArray;
    }

    public static final VersionMap[] removeVersionMap(VersionMap[] array, int index) {
        VersionMap[] newArray = new VersionMap[array.length - 1];
        PlatformAdapter.arraycopy(array, 0, newArray, 0, index);

        int remaining = array.length - 1 - index;

        if (remaining > 0)
            PlatformAdapter.arraycopy(array, index + 1, newArray, index, remaining);

        // Search nulls to assert indexes, races etc...

        if (Debug.ENABLED)
            Debug.assertion(!Arrays.asList(newArray).contains(null));

        return newArray;
    }

    public static final Version[][] removeVersions(Version[][] array, int index) {
        Version[][] newArray = removeReads(array, index);

        if (Debug.ENABLED) // Search nulls to assert indexes, races etc...
            Debug.assertion(!Arrays.asList(newArray).contains(null));

        return newArray;
    }

    public static final Version[][] removeReads(Version[][] array, int index) {
        Version[][] newArray = new Version[array.length - 1][];
        PlatformAdapter.arraycopy(array, 0, newArray, 0, index);

        int remaining = array.length - 1 - index;

        if (remaining > 0)
            PlatformAdapter.arraycopy(array, index + 1, newArray, index, remaining);

        return newArray;
    }

    public static final VersionMap[] insertVersionMap(VersionMap[] array, VersionMap map, int index) {
        VersionMap[] newArray = new VersionMap[array.length + 1];

        // Copy already acknowledged

        PlatformAdapter.arraycopy(array, 0, newArray, 0, index);

        // Insert new commit after last acknowledged one

        newArray[index] = map;

        // Copy remaining with offset 1

        int length = array.length - index;

        if (Debug.ENABLED)
            Debug.assertion(length == newArray.length - (index + 1));

        PlatformAdapter.arraycopy(array, index, newArray, index + 1, length);

        if (Debug.ENABLED)
            Debug.assertion(newArray[newArray.length - 1] != null);

        // Search nulls to assert indexes, races etc...

        if (Debug.ENABLED)
            Debug.assertion(!Arrays.asList(newArray).contains(null));

        return newArray;
    }

    public static final Version[][] insertVersions(Version[][] array, Version[] versions, int index) {
        Version[][] newArray = new Version[array.length + 1][];
        PlatformAdapter.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = versions;
        int length = array.length - index;

        if (Debug.ENABLED)
            Debug.assertion(length == newArray.length - (index + 1));

        PlatformAdapter.arraycopy(array, index, newArray, index + 1, length);

        if (Debug.ENABLED)
            Debug.assertion(newArray[newArray.length - 1] != null);

        if (Debug.ENABLED) {
            Debug.assertion(newArray[TransactionManager.OBJECTS_VERSIONS_INDEX] == TransactionManager.OBJECTS_VERSIONS);
            Debug.assertion(!Arrays.asList(newArray).contains(null));
        }

        return newArray;
    }

    public static final Version[][] insertNullReads(Version[][] array, int index) {
        Version[][] newArray = new Version[array.length + 1][];
        PlatformAdapter.arraycopy(array, 0, newArray, 0, index);
        int length = array.length - index;

        if (Debug.ENABLED)
            Debug.assertion(length == newArray.length - (index + 1));

        PlatformAdapter.arraycopy(array, index, newArray, index + 1, length);

        if (Debug.ENABLED) {
            Debug.assertion(newArray[index] == null);
            Debug.assertion(newArray[TransactionManager.OBJECTS_VERSIONS_INDEX] == null);
        }

        return newArray;
    }

    /**
     * TODO: hash or sort?
     */
    public static final int getIndex(Snapshot snapshot, VersionMap map) {
        for (int i = snapshot.getVersionMaps().length - 1; i >= 0; i--)
            if (snapshot.getVersionMaps()[i] == map)
                return i;

        return -1;
    }

    public static final boolean validateCheckOnce(VersionMap map, Version[] reads, Snapshot snapshot, int start, int stop) {
        if (Debug.ENABLED) {
            Debug.assertion(start >= 0 && stop >= 0 && start <= stop);

            /*
             * Check we do not validate twice against the same map.
             */
            IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(map);

            for (int i = start; i < stop; i++) {
                PlatformConcurrentMap<VersionMap, VersionMap> maps = getInstance()._validated.get(wrapper);

                if (maps == null) {
                    maps = new PlatformConcurrentMap<VersionMap, VersionMap>();
                    Debug.assertion(getInstance()._validated.put(wrapper, maps) == null);
                }

                VersionMap m = snapshot.getVersionMaps()[i];
                Debug.assertion(maps.put(m, m) == null);
            }
        }

        return validate(map, reads, snapshot, start, stop);
    }

    public static final boolean validate(VersionMap map, Version[] reads, Snapshot snapshot, int start, int stop) {
        if (start < stop)
            if (reads != null)
                for (int i = reads.length - 1; i >= 0; i--)
                    if (reads[i] != null)
                        if (!reads[i].validAgainst(map, snapshot, start, stop))
                            return false;

        return true;
    }

    /*
     * Debug.
     */

    public void removeValidated(VersionMap map) {
        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(map);
        _validated.remove(wrapper);
    }

    public void checkFieldsHaveDefaultValues(final Transaction transaction) {
        if (_empty == null) {
            _empty = (Transaction) DefaultObjectModelBase.getInstance().createInstance(Transaction.getLocalTrunk(), DefaultObjectModelBase.COM_OBJECTFABRIC_TRANSACTION_CLASS_ID, null);

            if (Debug.THREADS)
                ThreadAssert.removePrivate(_empty);
        }

        boolean ok = true;

        try {
            ok &= PlatformAdapter.shallowEquals(_empty, transaction, TransactionSets.class);
            ok &= PlatformAdapter.shallowEquals(_empty, transaction, TransactionPrivate.class, "_cachedChild");
            ok &= PlatformAdapter.shallowEquals(_empty, transaction, TransactionPublic.class);
            ok &= PlatformAdapter.shallowEquals(_empty, transaction, Transaction.class, "_parent", "_isRemote");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (Helper.getInstance().FailReset) {
            LastResetFailed = true;
            Debug.assertion(!ok);
        } else {
            LastResetFailed = false;
            Debug.assertion(ok);
        }
    }

    public PlatformConcurrentMap<Reader, Boolean> getReaders() {
        return _readers;
    }

    public PlatformConcurrentMap<Writer, Boolean> getWriters() {
        return _writers;
    }

    public PlatformConcurrentMap<Reader, Reader> getAllowMultipleCreations() {
        return _allowMultipleCreations;
    }

    //

    public int getExpectedClass() {
        return _expectedClass.get();
    }

    public void setExpectedClass(int value) {
        _expectedClass.set(value);
    }

    public boolean allowEqualsOrHash(UserTObject object) {
        Boolean value = _disableEqualsOrHashCheck.get();

        if (value != null && value)
            return true;

        // if (value == null || !value) {
        // if (object instanceof Transaction || object instanceof Session ||
        // object instanceof Connection)
        // return true;
        //
        // if (object instanceof TKeyed || object instanceof TList)
        // return true;
        // }

        return false;
    }

    public void disableEqualsOrHashCheck() {
        _disableEqualsOrHashCheck.set(true);
    }

    public void enableEqualsOrHashCheck() {
        _disableEqualsOrHashCheck.set(false);
    }

    public StringBuilder getSB() {
        StringBuilder sb = _sb.get();

        if (sb == null)
            _sb.set(sb = new StringBuilder());

        return sb;
    }

    public StringBuilder getSB2() {
        StringBuilder sb = _sb2.get();

        if (sb == null)
            _sb2.set(sb = new StringBuilder());

        return sb;
    }

    public void assertIdleAndCleanup() {
        Debug.assertion(Transaction.getCurrent() == null);
        Debug.assertion(Transaction.getDefaultTrunk() == Site.getLocal().getTrunk());
        Debug.assertion(OF.getDefaultAsyncOptions().equals(new AsyncOptions()));
        Debug.assertion(_disableEqualsOrHashCheck.get() == null || !_disableEqualsOrHashCheck.get());

        List<Transaction> trunks = new List<Transaction>();

        synchronized (_watchers) {
            for (ArrayList<Object> value : _watchers.values()) {
                Transaction trunk = null;

                if (value.size() == 1)
                    trunk = (Transaction) value.get(0);
                else if (value.size() == 2) {
                    Debug.assertion(trunk != Site.getLocal().getTrunk());

                    if (value.get(0) instanceof Transaction) {
                        trunk = (Transaction) value.get(0);
                        Debug.assertion(value.get(1) instanceof MultiMapInterception);
                    } else {
                        Debug.assertion(value.get(0) instanceof MultiMapInterception);
                        trunk = (Transaction) value.get(1);
                    }
                } else
                    Debug.fail();

                if (trunk != null) {
                    Debug.assertion(trunk.getTrunk() == trunk);
                    Debug.assertion(!trunks.contains(trunk));
                    trunks.add(trunk);
                }
            }
        }

        Debug.assertion(trunks.contains(Site.getLocal().getTrunk()));

        for (int i = 0; i < trunks.size(); i++)
            assertIdle(trunks.get(i));

        if (Debug.THREADS) {
            for (int i = 0; i < trunks.size(); i++)
                if (trunks.get(i) != Site.getLocal().getTrunk())
                    ThreadAssert.removeShared(trunks.get(i).getSharedSnapshot().getVersionMaps()[TransactionManager.OBJECTS_VERSIONS_INDEX]);

            VersionMap[] maps = Site.getLocal().getTrunk().getSharedSnapshot().getVersionMaps();
            ThreadAssert.assertIdle(maps[TransactionManager.OBJECTS_VERSIONS_INDEX]);
        }

        synchronized (_watchers) {
            for (VersionMap map : _watchers.keySet().toArray(new VersionMap[0]))
                if (map != Site.getLocal().getTrunk().getSharedSnapshot().getVersionMaps()[0])
                    _watchers.remove(map);

            Debug.assertion(_watchers.size() == 1);
        }

        Site.getLocal().assertIdle();

        Debug.assertion(_validated.size() == 0);
        Debug.assertion(_callbacks.size() == 0);
        Debug.assertion(_createdNotRecycled.size() == 0);

        for (Map.Entry<Reader, Boolean> entry : _readers.entrySet()) {
            Reader reader = entry.getKey();
            boolean cleanClose = entry.getValue();

            if (Debug.THREADS)
                ThreadAssert.addPrivate(reader);

            if (cleanClose)
                reader.assertIdle();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(reader);
        }

        for (Map.Entry<Writer, Boolean> entry : _writers.entrySet()) {
            Writer writer = entry.getKey();
            boolean cleanClose = entry.getValue();

            if (Debug.THREADS)
                ThreadAssert.addPrivate(writer);

            if (cleanClose)
                writer.assertIdle();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(writer);
        }

        _readers.clear();
        _writers.clear();
        _allowMultipleCreations.clear();

        ThreadAssert.getOrCreateCurrent().resetCounters();
        Debug.assertion(Debug.ProcessName.length() == 0);
        Debug.assertion(!Debug.AssertNoConflict);
    }

    private void assertIdle(Transaction trunk) {
        Snapshot snapshot = trunk.getSharedSnapshot();
        Debug.assertion(snapshot.getVersionMaps().length == 1);
        Debug.assertion(snapshot.getReads() == null);
        Debug.assertion(snapshot.getWrites().length == 1);
        Debug.assertion(snapshot.getWrites()[0] == TransactionManager.OBJECTS_VERSIONS);
        Debug.assertion(snapshot.getVersionMaps()[0].getTransaction() == null);

        if (snapshot.getSlowChanging() != null) {
            Debug.assertion(snapshot.getSlowChanging().getExtensions() == null);
            Debug.assertion(snapshot.getSlowChanging().getAcknowledgers() == null);
            Debug.assertion(snapshot.getSlowChanging().getWalkers() == null);
            Debug.assertion(snapshot.getSlowChanging().getNoMergeWalkers() == null);
            Debug.assertion(snapshot.getSlowChanging().getSourceSplitters() == null);
            Connection.Version[] blocked = snapshot.getSlowChanging().getBlocked();
            Debug.assertion(blocked == null || blocked == SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED);
        }

        synchronized (_watchers) {
            VersionMap last = snapshot.getLast();
            Debug.assertion(_watchers.containsKey(last));
        }
    }

    public void addCreatedNotRecycled(Transaction transaction) {
        IdentityEqualityWrapper wrapper = new IdentityEqualityWrapper(transaction);
        IdentityEqualityWrapper previous = _createdNotRecycled.put(wrapper, wrapper);

        if (Debug.ENABLED)
            Debug.assertion(previous == null);
    }

    public void removeCreatedNotRecycled(Transaction transaction) {
        _createdNotRecycled.remove(new IdentityEqualityWrapper(transaction));

        if (Debug.ENABLED)
            Debug.assertion(!_createdNotRecycled.containsKey(new IdentityEqualityWrapper(transaction)));
    }

    public void addWatcher(VersionMap map, Object watcher, String reason) {
        if (Debug.ENABLED)
            Debug.assertion(watcher != null);

        Object[] watchers;

        synchronized (_watchers) {
            ArrayList<Object> list = _watchers.get(map);

            if (list == null) {
                list = new ArrayList<Object>();
                Object previous = _watchers.put(map, list);
                Debug.assertion(previous == null);
            }

            list.add(watcher);

            if (Debug.STM_LOG)
                watchers = list.toArray();
        }

        if (Debug.STM_LOG)
            Log.write("+1 " + (watchers.length) + " on map " + map + " (" + watcher + "): " + reason + " (" + Arrays.toString(watchers) + ")");
    }

    public void removeWatcher(VersionMap map, Object watcher, String reason) {
        if (Debug.ENABLED)
            Debug.assertion(watcher != null);

        Object[] watchers;

        synchronized (_watchers) {
            ArrayList<Object> list = _watchers.get(map);
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
            Log.write("-1 " + (watchers.length) + " on map " + map + " (" + watcher + "): " + reason + " (" + Arrays.toString(watchers) + ")");
    }

    @SuppressWarnings("unchecked")
    public ArrayList<Object> getWatchers(VersionMap map) {
        synchronized (_watchers) {
            ArrayList<Object> locked = _watchers.get(map);

            if (locked != null)
                return (ArrayList<Object>) locked.clone();

            return null;
        }
    }

    public int getRetryCount() {
        Integer boxed = _retryCount.get();

        if (boxed != null)
            return boxed;

        return 0;
    }

    public void setRetryCount(int value) {
        _retryCount.set(value);
    }

    public void checkVersionsHaveWrites(TObject.Version[] versions) {
        for (int i = versions.length - 1; i >= 0; i--) {
            if (versions[i] != null) {
                List<Object> list = new List<Object>();
                versions[i].getContentForDebug(list);
                Debug.assertion(list.size() > 0);
            }
        }
    }

    public PlatformConcurrentMap<Transaction, Transaction> getAllowExistingReadsOrWrites() {
        return _allowExistingReadsOrWrites;
    }

    public PlatformConcurrentMap<VersionMap, Snapshot> getToMergeLater() {
        return _toMergeLater;
    }

    public boolean getNoTransaction() {
        Boolean value = _noTransaction.get();
        return value != null && value;
    }

    public void setNoTransaction(boolean value) {
        _noTransaction.set(value);
    }

    //

    public void addCallback(Object callback) {
        Debug.assertion(_callbacks.putIfAbsent(callback, callback) == null);
    }

    public void removeCallback(Object callback) {
        Debug.assertion(_callbacks.remove(callback) == callback);
    }
}