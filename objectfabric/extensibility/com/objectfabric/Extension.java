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

import java.util.concurrent.Executor;

import com.objectfabric.Snapshot.SlowChanging;
import com.objectfabric.TObject.Reference;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.OverrideAssert;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SparseArrayHelper;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

/**
 * Main extension point. Features like object replication or persistence are implemented
 * as particular extensions.
 * <nl>
 * TODO merge with Walker
 */
@SingleThreaded
public abstract class Extension<T> extends Visitor.Listener {

    public static final class ExtensionShutdownException extends RuntimeException {
    }

    // Stores registered branches, and last snapshot if LATEST_VALUES
    // TODO: use a weak list? (warn, notifier does get())
    // TODO: keep track of number of objects, unregister branch when 0
    @SuppressWarnings("unchecked")
    private TObjectMapEntry<T>[] _branches = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

    private int _branchCount;

    private int _branchIndex;

    protected Extension() {
    }

    /**
     * @param branch
     */
    protected void onRegistering(Transaction branch) {
        OverrideAssert.set(this);
    }

    /**
     * @param branch
     */
    protected void onUnregistered(Transaction branch) {
        OverrideAssert.set(this);
    }

    protected final boolean registered(Transaction branch) {
        return TObjectMapEntry.contains(_branches, branch);
    }

    /**
     * When registered, the extension get notifications about commits, and if its
     * granularity is ALL_CHANGES it prevents commits to be merged until the extension
     * calls done(Transaction).
     */
    protected final void register(Transaction branch) {
        if (registered(branch))
            throw new RuntimeException(Strings.ALREADY_REGISTERED);

        OverrideAssert.add(this);
        onRegistering(branch);
        OverrideAssert.end(this);

        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            Snapshot snapshot = branch.getSharedSnapshot();
            Extension[] extensions = snapshot.getSlowChanging() != null ? snapshot.getSlowChanging().getExtensions() : null;
            Connection.Version[] blocked = snapshot.getSlowChanging() != null ? snapshot.getSlowChanging().getBlocked() : null;
            extensions = add(extensions, this);
            SlowChanging slowChanging = new SlowChanging(branch, extensions, blocked);
            snapshot.copyWithNewSlowChanging(newSnapshot, slowChanging);

            if (casSnapshotWithThis(branch, snapshot, newSnapshot))
                break;
        }

        if (!registered(branch))
            throw new RuntimeException(Strings.NOT_REGISTERED);

        requestRun();
    }

    boolean casSnapshotWithThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot) {
        return branch.casSharedSnapshot(snapshot, newSnapshot);
    }

    protected final void unregister(Transaction branch, Exception exception) {
        // NOT_REGISTERED tested in remove

        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            Snapshot snapshot = branch.getSharedSnapshot();
            Extension[] extensions = snapshot.getSlowChanging().getExtensions();
            extensions = remove(extensions, this);
            SlowChanging slowChanging = new SlowChanging(branch, extensions, snapshot.getSlowChanging().getBlocked());
            snapshot.copyWithNewSlowChanging(newSnapshot, slowChanging);

            if (casSnapshotWithoutThis(branch, snapshot, newSnapshot, exception))
                break;
        }

        OverrideAssert.add(this);
        onUnregistered(branch);
        OverrideAssert.end(this);

        TObjectMapEntry.remove(_branches, branch);
        _branchCount--;
    }

    /**
     * @param branch
     * @param snapshot
     * @param newSnapshot
     * @param exception
     * @return
     */
    boolean casSnapshotWithoutThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot, Exception exception) {
        return branch.casSharedSnapshot(snapshot, newSnapshot);
    }

    protected final void unregisterFromAllBranches(Exception exception) {
        for (int i = 0; i < _branches.length; i++) {
            if (_branches[i] != null && _branches[i] != TObjectMapEntry.REMOVED) {
                Transaction branch = (Transaction) _branches[i].getKey().getReference().get();

                if (branch != null)
                    unregister(branch, exception);
            }
        }
    }

    private static final Extension[] add(Extension[] array, Extension extension) {
        if (contains(array, extension))
            throw new RuntimeException(Strings.ALREADY_REGISTERED);

        Extension[] newArray;

        if (array != null) {
            newArray = new Extension[array.length + 1];
            PlatformAdapter.arraycopy(array, 0, newArray, 0, array.length);
            newArray[newArray.length - 1] = extension;
        } else {
            newArray = new Extension[1];
            newArray[0] = extension;
        }

        return newArray;
    }

    private static final Extension[] remove(Extension[] array, Extension extension) {
        if (!contains(array, extension))
            throw new RuntimeException(Strings.NOT_REGISTERED);

        if (array.length == 1)
            return null;

        Extension[] newArray = new Extension[array.length - 1];
        int j = 0;

        for (int i = 0; i < array.length; i++)
            if (array[i] != extension)
                newArray[j++] = array[i];

        if (Debug.ENABLED)
            Debug.assertion(j == newArray.length);

        return newArray;
    }

    private static boolean contains(Extension[] array, Extension extension) {
        if (array == null)
            return false;

        for (int i = 0; i < array.length; i++)
            if (array[i] == extension)
                return true;

        return false;
    }

    //

    protected final Transaction[] copyBranches() {
        Transaction[] branches = new Transaction[_branchCount];
        int index = 0;

        for (int i = 0; i < _branches.length; i++)
            if (_branches[i] != null && _branches[i] != TObjectMapEntry.REMOVED)
                branches[index++] = (Transaction) _branches[i].getKey().getReference().get();

        return branches;
    }

    protected final int getBranchCount() {
        return _branchCount;
    }

    protected final int getBranchIndex() {
        return _branchIndex;
    }

    protected final Transaction getNextBranch() {
        if (Debug.ENABLED)
            Debug.assertion(_branchCount == TObjectMapEntry.size(_branches));

        for (int i = 0; i < _branches.length; i++) {
            if (++_branchIndex >= _branches.length)
                _branchIndex = 0;

            if (_branches[_branchIndex] != null && _branches[_branchIndex] != TObjectMapEntry.REMOVED) {
                Transaction branch = (Transaction) _branches[_branchIndex].getKey().getReference().get();

                if (branch != null) {
                    if (!isUpToDate(branch, _branches[_branchIndex].getValue()))
                        return branch;

                    OverrideAssert.add(this);
                    onUpToDate(branch);
                    OverrideAssert.end(this);
                }
            }
        }

        return null;
    }

    protected abstract boolean isUpToDate(Transaction branch, T value);

    /**
     * @param branch
     */
    protected void onUpToDate(Transaction branch) {
        OverrideAssert.set(this);
    }

    //

    /**
     * Called when a replicated TObject is GCed.
     */
    protected void onGarbageCollected(TObject shared) {
        if (Debug.ENABLED)
            Debug.assertion(((Version) shared).getUnion().getClass() != Reference.class);

        if (TObjectMapEntry.removeIfPresent(_branches, shared))
            _branchCount--;
    }

    //

    protected static final void restoreCurrentAfterUserCode(Transaction expected) {
        Transaction current = Transaction.getCurrent();

        if (current != expected) {
            Log.write(Strings.USER_CODE_CHANGED_CURRENT_TRANSACTION);
            Transaction.setCurrent(expected);
        }
    }

    //

    protected final T get(Transaction branch) {
        return TObjectMapEntry.get(_branches, branch);
    }

    protected final T get(int index) {
        return _branches[index].getValue();
    }

    protected final TObjectMapEntry<T> getEntry(Transaction branch) {
        return TObjectMapEntry.getEntry(_branches, branch);
    }

    protected final TObjectMapEntry<T> getEntry(int index) {
        return _branches[index];
    }

    protected final void put(Transaction branch, T value) {
        TObjectMapEntry<T> entry = new TObjectMapEntry<T>(branch, value);
        _branches = TObjectMapEntry.put(_branches, entry);
        _branchCount++;
    }

    protected final void update(Transaction branch, T value) {
        TObjectMapEntry.update(_branches, branch, value);
    }

    /**
     * Many extensions need maps of TObject -> Object. This map ignores equals and
     * hashCode methods, as they can be overriden by user. Extensions need to keep state
     * per TObject instance.
     */
    protected static final class TObjectMapEntry<V> {

        public static final TObjectMapEntry REMOVED = new TObjectMapEntry();

        private final Version _key;

        private final int _hash;

        private V _value;

        public TObjectMapEntry(TObject key, V value) {
            _key = key.getSharedVersion_objectfabric();
            _hash = key.getSharedHashCode_objectfabric();
            _value = value;
        }

        private TObjectMapEntry() {
            _key = null;
            _hash = 0;
            _value = null;
        }

        public Version getKey() {
            return _key;
        }

        public V getValue() {
            return _value;
        }

        public void setValue(V value) {
            _value = value;
        }

        public static <T> boolean contains(TObjectMapEntry<T>[] map, TObject key) {
            Version shared = key.getSharedVersion_objectfabric();
            int index = key.getSharedHashCode_objectfabric() & (map.length - 1);

            for (int i = SparseArrayHelper.attemptsStart(map.length); i >= 0; i--) {
                if (map[index] == null)
                    return false;

                if (map[index].getKey() == shared)
                    return true;

                index = (index + 1) & (map.length - 1);
            }

            return false;
        }

        public static <T> TObjectMapEntry<T> getEntry(TObjectMapEntry<T>[] map, TObject key) {
            Version shared = key.getSharedVersion_objectfabric();
            int index = key.getSharedHashCode_objectfabric() & (map.length - 1);

            for (int i = SparseArrayHelper.attemptsStart(map.length); i >= 0; i--) {
                if (map[index] == null)
                    return null;

                if (map[index].getKey() == shared)
                    return map[index];

                index = (index + 1) & (map.length - 1);
            }

            return null;
        }

        public static <T> T get(TObjectMapEntry<T>[] map, TObject key) {
            TObjectMapEntry<T> entry = getEntry(map, key);

            if (entry != null)
                return entry._value;

            return null;
        }

        @SuppressWarnings("unchecked")
        public static <T> TObjectMapEntry<T>[] put(TObjectMapEntry<T>[] map, TObjectMapEntry<T> entry) {
            while (!tryToPut(map, entry)) {
                TObjectMapEntry[] previous = map;

                for (;;) {
                    map = new TObjectMapEntry[map.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                    if (rehash(previous, map))
                        break;
                }
            }

            return map;
        }

        private static <T> boolean tryToPut(TObjectMapEntry<T>[] map, TObjectMapEntry<T> entry) {
            if (Debug.ENABLED)
                Debug.assertion(!contains(map, entry.getKey()));

            int index = entry._hash & (map.length - 1);

            for (int i = SparseArrayHelper.attemptsStart(map.length); i >= 0; i--) {
                if (map[index] == null || map[index] == REMOVED) {
                    map[index] = entry;

                    if (Debug.ENABLED)
                        checkInvariants(map);

                    return true;
                }

                index = (index + 1) & (map.length - 1);
            }

            return false;
        }

        private static <T> boolean rehash(TObjectMapEntry<T>[] previous, TObjectMapEntry<T>[] map) {
            for (int i = previous.length - 1; i >= 0; i--)
                if (previous[i] != null && previous[i] != REMOVED)
                    if (!tryToPut(map, previous[i]))
                        return false;

            if (Debug.ENABLED)
                checkInvariants(map);

            return true;
        }

        public static <T> void update(TObjectMapEntry<T>[] map, TObject key, T value) {
            Version shared = key.getSharedVersion_objectfabric();
            int index = key.getSharedHashCode_objectfabric() & (map.length - 1);

            for (;;) {
                if (map[index].getKey() == shared) {
                    map[index]._value = value;
                    return;
                }

                index = (index + 1) & (map.length - 1);
            }
        }

        @SuppressWarnings("unchecked")
        public static <T> boolean removeIfPresent(TObjectMapEntry<T>[] map, TObject key) {
            Version shared = key.getSharedVersion_objectfabric();
            int index = key.getSharedHashCode_objectfabric() & (map.length - 1);

            for (int i = SparseArrayHelper.attemptsStart(map.length); i >= 0; i--) {
                if (map[index].getKey() == shared) {
                    map[index] = REMOVED;
                    return true;
                }

                index = (index + 1) & (map.length - 1);
            }

            return false;
        }

        @SuppressWarnings("unchecked")
        public static <T> TObjectMapEntry<T> remove(TObjectMapEntry<T>[] map, TObject key) {
            Version shared = key.getSharedVersion_objectfabric();
            int index = key.getSharedHashCode_objectfabric() & (map.length - 1);

            for (;;) {
                if (map[index].getKey() == shared) {
                    TObjectMapEntry<T> entry = map[index];
                    map[index] = REMOVED;
                    return entry;
                }

                index = (index + 1) & (map.length - 1);
            }
        }

        @SuppressWarnings("unchecked")
        public static <T> void remove(TObjectMapEntry<T>[] map, int index) {
            map[index] = REMOVED;
        }

        public static int size(TObjectMapEntry[] map) {
            int size = 0;

            for (int i = map.length - 1; i >= 0; i--)
                if (map[i] != null && map[i] != REMOVED)
                    size++;

            return size;
        }

        private static <T> void checkInvariants(TObjectMapEntry<T>[] map) {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            if (Debug.SLOW_CHECKS)
                for (int i = 0; i < map.length; i++)
                    for (int j = 0; j < map.length; j++)
                        if (i != j)
                            Debug.assertion(map[i] == null || map[i] == REMOVED || map[i] != map[j]);
        }
    }

    //

    protected abstract class DefaultRunnable extends Actor implements Executor {

        public final void execute(Runnable runnable) {
            add(runnable);
            Extension.this.requestRun();
        }

        @Override
        public void onException(Exception e) {
            super.onException(e);

            OF.getConfig().onException(Extension.this, e);
        }
    }

    // Debug

    @Override
    protected void assertIdle() {
        super.assertIdle();

        boolean added;

        if (Debug.THREADS)
            added = ThreadAssert.addPrivateIfNot(this);

        Debug.assertion(TObjectMapEntry.size(_branches) == 0);
        Debug.assertion(_branchCount == 0);

        if (Debug.THREADS)
            if (added)
                ThreadAssert.removePrivate(this);
    }
}
