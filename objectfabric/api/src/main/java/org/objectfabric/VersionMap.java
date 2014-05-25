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

import java.util.concurrent.atomic.AtomicInteger;

import org.objectfabric.TObject.Transaction;
import org.objectfabric.TObject.Version;
import org.objectfabric.ThreadAssert.SingleThreadedThenShared;

/**
 * Contains versions of object modified by a transaction. Versions themselves are stored
 * in an external array.
 */
@SuppressWarnings("serial")
@SingleThreadedThenShared
final class VersionMap extends AtomicInteger {

    static final VersionMap CLOSING = new VersionMap(-1);

    private Transaction _transaction;

    private boolean _remote;

    // TODO add a condensed filter of reads & write to skip validation

    //

    /**
     * Maps start with a watchers count of 1 so they are not considered disposed by
     * default. By convention the watcher is the parent transaction, and the View for the
     * initial map.
     */
    static final int DEFAULT_WATCHERS = 1;

    //

    private static final int MERGE_DEFAULT = 0;

    private static final int MERGE_A = 1;

    private static final int MERGE_B = 2;

    private static final int MERGE_DONE = 3;

    /**
     * This field prevents two threads to merge the same map with another one at the same
     * time.
     */
    private final AtomicInteger _mergeInfo = new AtomicInteger();

    static {
        if (Debug.THREADS) {
            ThreadAssert.removePrivate(CLOSING);
            ThreadAssert.addSharedDefinitively(CLOSING);
        }
    }

    /*
     * TODO Use an array of counters indexed by a kind of thread id, and only update this
     * when thread counter reaches 0.
     */
    VersionMap() {
        this(DEFAULT_WATCHERS);
    }

    private VersionMap(int value) {
        super(value);
    }

    final Transaction getTransaction() {
        return _transaction;
    }

    final void setTransaction(Transaction value) {
        if (Debug.ENABLED) {
            Debug.assertion(value == null || value.isCommitted());
            Debug.assertion(_transaction == null || _transaction == value);
        }

        _transaction = value;
    }

    final boolean isRemote() {
        return _remote;
    }

    final void setRemote() {
        if (Debug.ENABLED)
            Debug.assertion(!_remote);

        _remote = true;
    }

    //

    final boolean isNotMerging() {
        return _mergeInfo.get() == MERGE_DEFAULT;
    }

    final boolean isMergedToAnotherMap() {
        return _mergeInfo.get() == MERGE_DONE;
    }

    //

    final int getWatchers() {
        return get();
    }

    final boolean tryToAddWatchers(int count) {
        if (Debug.ENABLED)
            Debug.assertion(count > 0);

        for (;;) {
            int watchers = get();

            if (watchers == 0)
                return false;

            if (Debug.ENABLED)
                Debug.assertion(watchers > 0);

            int newWatchers = watchers + count;

            if (compareAndSet(watchers, newWatchers))
                return true;
        }
    }

    final Runnable removeWatchers(final Workspace workspace, int count, boolean delayed, final Snapshot mapSnapshot) {
        if (Debug.ENABLED)
            Debug.assertion(count > 0);

        for (;;) {
            int watchers = get();

            if (Debug.ENABLED)
                Debug.assertion(watchers > 0);

            int newWatchers = watchers - count;

            if (compareAndSet(watchers, newWatchers)) {
                if (newWatchers == 0) {
                    if (!delayed)
                        merge(workspace, mapSnapshot);
                    else {
                        return new Runnable() {

                            @Override
                            public void run() {
                                merge(workspace, mapSnapshot);
                            }
                        };
                    }
                }

                return null;
            }
        }
    }

    private void merge(Workspace workspace, Snapshot mapSnapshot) {
        boolean walkQueue = mergeAndReturnIfShouldWalkDelayedQueue(workspace, mapSnapshot);

        if (walkQueue) {
            for (;;) {
                VersionMap map = workspace.pollToMergeLater();

                if (map == null)
                    break;

                if (Debug.ENABLED) {
                    mapSnapshot = Helper.instance().getToMergeLater().remove(map);
                    Debug.assertion(mapSnapshot != null);
                    mapSnapshot = mapSnapshot != Helper.TO_MERGE_LATER_IS_NULL ? mapSnapshot : null;
                }

                map.mergeAndReturnIfShouldWalkDelayedQueue(workspace, mapSnapshot);
            }
        }
    }

    private boolean mergeAndReturnIfShouldWalkDelayedQueue(Workspace workspace, Snapshot mapSnapshot) {
        // TODO bench read field before for less CAS

        /*
         * Try to switch both merge guards before merge, otherwise restore them and add to
         * queue to merge later.
         */
        if (!_mergeInfo.compareAndSet(MERGE_DEFAULT, MERGE_A)) {
            if (_mergeInfo.get() != MERGE_DONE) {
                if (Debug.ENABLED) {
                    mapSnapshot = mapSnapshot != null ? mapSnapshot : Helper.TO_MERGE_LATER_IS_NULL;
                    Debug.assertion(Helper.instance().getToMergeLater().put(this, mapSnapshot) == null);
                }

                workspace.keepToMergeLater(this);
            }

            return false;
        }

        /*
         * From there on mergeInfo has to be released on return so return true to retry
         * potential delayed merges.
         */

        Snapshot snapshot = workspace.snapshot();
        int aIndex = Helper.getIndex(snapshot, this);
        int bIndex = aIndex + 1;
        VersionMap b = snapshot.getVersionMaps()[bIndex];

        if (Debug.ENABLED) {
            /*
             * If closed, previous map is still considered last of queue, so it should
             * still have at least one watcher.
             */
            Debug.assertion(b != CLOSING);
        }

        if (!b._mergeInfo.compareAndSet(MERGE_DEFAULT, MERGE_B)) {
            if (Debug.ENABLED) {
                mapSnapshot = mapSnapshot != null ? mapSnapshot : Helper.TO_MERGE_LATER_IS_NULL;
                Debug.assertion(Helper.instance().getToMergeLater().put(this, mapSnapshot) == null);
            }

            /*
             * Other merge ongoing, add to queue to retry when finished.
             */
            workspace.keepToMergeLater(this);

            _mergeInfo.set(MERGE_DEFAULT);
            return true;
        }

        if (Debug.STM_LOG)
            Log.write("Merging " + this + " to " + b);

        Version[] mergedWrites = merge(this, snapshot.writes()[aIndex], b, snapshot.writes()[bIndex]);
        Version[] mergedReads = null;

        if (snapshot.getReads() != null && aIndex != TransactionManager.OBJECTS_VERSIONS_INDEX) {
            Version[] aReads = snapshot.getReads()[aIndex];
            Version[] bReads = snapshot.getReads()[bIndex];

            if (aReads != null && bReads != null)
                mergedReads = merge(this, aReads, b, bReads);
            else if (aReads != null)
                mergedReads = aReads;
            else if (bReads != null)
                mergedReads = bReads;
        }

        if (Debug.ENABLED)
            if (aIndex == TransactionManager.OBJECTS_VERSIONS_INDEX)
                Debug.assertion(mergedWrites == TransactionManager.OBJECTS_VERSIONS);

        /*
         * Merge is done, replace _snapshot
         */
        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            newSnapshot.setVersionMaps(Helper.removeVersionMap(snapshot.getVersionMaps(), aIndex));
            Version[][] reads = snapshot.getReads();

            if (reads != null) {
                if (Debug.ENABLED && aIndex == TransactionManager.OBJECTS_VERSIONS_INDEX)
                    Debug.assertion(mergedReads == null);

                boolean empty = mergedReads == null;

                if (empty)
                    for (int i = reads.length - 1; i >= 0; i--)
                        if (i != aIndex && i != aIndex + 1 && reads[i] != null)
                            empty = false;

                if (!empty) {
                    reads = Helper.removeVersions(reads, aIndex);
                    reads[aIndex] = mergedReads;
                } else
                    reads = null;
            }

            newSnapshot.setReads(reads);
            newSnapshot.writes(Helper.removeVersions(snapshot.writes(), aIndex));
            newSnapshot.writes()[aIndex] = mergedWrites;
            newSnapshot.slowChanging(snapshot.slowChanging());

            if (Debug.ENABLED) {
                Debug.assertion(newSnapshot.getVersionMaps()[bIndex - 1] == b);
                Debug.assertion(newSnapshot.writes()[TransactionManager.OBJECTS_VERSIONS_INDEX] == TransactionManager.OBJECTS_VERSIONS);
            }

            if (workspace.casSnapshot(snapshot, newSnapshot)) {
                if (Debug.ENABLED) {
                    Debug.assertion(_mergeInfo.get() == MERGE_A);
                    Debug.assertion(b._mergeInfo.get() == MERGE_B);
                }

                if (Stats.ENABLED)
                    Stats.Instance.Merged.incrementAndGet();

                _mergeInfo.set(MERGE_DONE);
                b._mergeInfo.set(MERGE_DEFAULT);

                if (Debug.THREADS) {
                    ThreadAssert.addSharedDefinitively(this);
                    ThreadAssert.removeShared(this);
                }

                return true;
            }

            snapshot = workspace.snapshot();
            aIndex = Helper.getIndex(snapshot, this);

            if (Debug.ENABLED) {
                // Only to check the assertion before the CAS
                bIndex = Helper.getIndex(snapshot, b);
            }
        }
    }

    //

    /**
     * The merge process is crossed between maps and versions. Functionally map a is
     * merged to map b and removed, but data is merged from versions b to versions a. C.f.
     * method Version.merge.
     */
    private static Version[] merge(VersionMap a, Version[] aVersions, VersionMap b, Version[] bVersions) {
        if (Debug.ENABLED) {
            Debug.assertion(a.getWatchers() == 0);
            Debug.assertion(a._mergeInfo.get() == MERGE_A);
            Debug.assertion(b._mergeInfo.get() == MERGE_B);
        }

        Version[] result = aVersions;

        if (aVersions == TransactionManager.OBJECTS_VERSIONS) {
            for (int i = bVersions.length - 1; i >= 0; i--) {
                if (bVersions[i] != null) {
                    Version shared = (Version) bVersions[i].object().shared_();
                    Version merged = merge(shared, bVersions[i], false);

                    if (Debug.ENABLED)
                        Debug.assertion(merged == shared);
                }
            }
        } else {
            for (int i = bVersions.length - 1; i >= 0; i--) {
                if (bVersions[i] != null) {
                    TObject object = bVersions[i].object();
                    Version aVersion = TransactionBase.getVersion(result, object);

                    if (aVersion != null) {
                        Version merged = aVersion;
                        merged = merge(aVersion, merged, bVersions[i], false);

                        /*
                         * If merged is new version, some of its content might have no
                         * visibility from other threads. Store merge in new array so
                         * aVersions is not modified.
                         */
                        if (merged != aVersion) {
                            if (result == aVersions) {
                                result = new Version[aVersions.length];

                                for (int t = result.length - 1; t >= 0; t--) {
                                    if (aVersions[t] != aVersion)
                                        result[t] = aVersions[t];
                                    else
                                        result[t] = merged;
                                }
                            } else {
                                int index = TransactionBase.getIndex(result, object);

                                if (Debug.ENABLED)
                                    Debug.assertion(result[index] == aVersion);

                                result[index] = merged;
                            }
                        }
                    } else
                        result = TransactionBase.putVersion(result, bVersions[i]);
                }
            }
        }

        if (a._transaction != null) {
            dispose(a._transaction);

            if (Debug.THREADS)
                ThreadAssert.addPrivate(a);

            a._transaction = null;

            if (Debug.THREADS)
                ThreadAssert.removePrivate(a);
        }

        if (b._transaction != null) {
            dispose(b._transaction);

            if (Debug.THREADS)
                ThreadAssert.addPrivate(b);

            b._transaction = null;

            if (Debug.THREADS)
                ThreadAssert.removePrivate(b);
        }

        if (Debug.ENABLED)
            Debug.assertion(result != null);

        return result;
    }

    final void onAborted() {
        if (_transaction != null)
            if (_mergeInfo.compareAndSet(MERGE_DEFAULT, MERGE_DONE))
                dispose(_transaction);
    }

    private static void dispose(Transaction transaction) {
        if (transaction != null) {
            if (Debug.THREADS) {
                ThreadAssert.removeShared(transaction);
                ThreadAssert.addPrivate(transaction);
                Debug.assertion(transaction.isCommitted());
            }

            Workspace workspace = transaction.workspace();

            if (Debug.ENABLED)
                Debug.assertion(workspace.transaction() == null);

            transaction.reset();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            workspace.recycle(transaction);
        }
    }

    static Version merge(Version target, Version source, boolean threadPrivate) {
        return merge(target, target, source, threadPrivate);
    }

    private static Version merge(Version target, Version merged, Version source, boolean threadPrivate) {
        List<Object> sourceWrites, sourceClones, targetWrites = null;

        if (Debug.ENABLED) {
            sourceWrites = new List<Object>();
            targetWrites = new List<Object>();
            source.getContentForDebug(sourceWrites);
            target.getContentForDebug(targetWrites);
            sourceClones = cloneArrays(sourceWrites);

            if (target == target.object().shared_())
                Debug.assertion(!threadPrivate);
        }

        Version result = merged.merge(merged, source, threadPrivate);

        if (Debug.ENABLED) {
            result.checkInvariants();

            /*
             * No need to clone if private.
             */
            if (threadPrivate)
                Debug.assertion(result == merged);

            if (target == target.object().shared_())
                Debug.assertion(result == target);

            /*
             * TODO: Test if would be better than copying content when 'merged' is fully
             * overriden by 'source'. For now it complicates merge methods and 'source' is
             * younger so it should be GC instead of 'target'.
             */
            Debug.assertion(result != source);

            List<Object> sourceWrites2 = new List<Object>();
            List<Object> targetWrites2 = new List<Object>();
            source.getContentForDebug(sourceWrites2);
            target.getContentForDebug(targetWrites2);

            /*
             * Assert source has not be modified.
             */
            assertNoChange(sourceWrites, sourceClones, sourceWrites2);

            /*
             * Assert that even if target has been modified, the objects themselves are
             * the same or from source. New arrays would not be visible to other threads.
             */
            if (!threadPrivate)
                if (!(target instanceof TKeyedSharedVersion))
                    for (int i = 0; i < targetWrites.size(); i++)
                        if (targetWrites.get(i) != null && targetWrites.get(i).getClass().isArray())
                            Debug.assertion(targetWrites2.get(i) == targetWrites.get(i) || sourceWrites.contains(targetWrites2.get(i)));
        }

        return result;
    }

    private static List<Object> cloneArrays(List<Object> array) {
        List<Object> clone = new List<Object>();

        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);

            if (element instanceof Object[])
                clone.add(Platform.get().clone((Object[]) element));
            else
                clone.add(element);
        }

        return clone;
    }

    private static void assertNoChange(List<Object> list, List<Object> clones, List<Object> list2) {
        Debug.assertion(list.size() == list2.size());

        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);

            if (element instanceof Integer)
                Debug.assertion(element.equals(list2.get(i)));
            else
                Debug.assertion(element == list2.get(i));

            if (element instanceof Object[])
                Debug.assertion(Platform.get().shallowEquals(element, clones.get(i), Platform.get().objectArrayClass()));
        }
    }

    @Override
    public String toString() {
        return Platform.get().defaultToString(this);
    }
}
