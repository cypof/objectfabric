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

package of4gwt;

import java.util.ArrayList;


import of4gwt.TObject.Reference;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreadedOrShared;

/**
 * Contains versions of object modified by a transaction. Versions themselves are stored
 * in an external array.
 */
@SingleThreadedOrShared
final class VersionMap {

    public static final class Source {

        public final Connection.Version Connection;

        public final byte InterceptionId;

        public final boolean Propagated;

        public Source(Connection.Version connection, byte interceptionId, boolean propagated) {
            if (connection == null)
                throw new IllegalArgumentException();

            Connection = connection;
            InterceptionId = interceptionId;
            Propagated = propagated;
        }
    }

    private Transaction _transaction;

    private Interception _interception;

    public static final Source IMPORTS_SOURCE;

    private Source _source;

    // TODO add a condensed filter of reads & write to skip validation

    //

    /**
     * Maps start with a watchers count of 1 so they are not considered disposed by
     * default. By convention the watcher is the parent transaction, and current (Trunk)
     * for initial map.
     */
    static final int DEFAULT_WATCHERS = 1;

    /*
     * TODO Use an array of counters indexed by a kind of thread id, and only update this
     * when thread counter reaches 0.
     */
    private volatile int _watchers = DEFAULT_WATCHERS;

    

    //

    private static final int MERGE_DEFAULT = 0;

    private static final int MERGE_A = 1;

    private static final int MERGE_B = 2;

    private static final int MERGE_DONE = 3;

    /**
     * This field prevents two threads to merge the same map with another one at the same
     * time.
     */
    private volatile int _mergeInfo;

    

    static {
        Connection.Version imports_shared = new ConnectionBase.Version(null, ConnectionBase.FIELD_COUNT);
        imports_shared.setUnion(new Reference(null, false), true);
        IMPORTS_SOURCE = new Source(imports_shared, (byte) 0, true);

        /*
         * TODO bench using weak compare and set (might be necessary to read snapshot
         * again after second _mergeInfo CAS).
         */
        
        
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public void setTransaction(Transaction value) {
        _transaction = value;
    }

    public Interception getInterception() {
        return _interception;
    }

    public void setInterception(Interception value) {
        _interception = value;
    }

    public Source getSource() {
        return _source;
    }

    public void setSource(Source value) {
        _source = value;
    }

    //

    public boolean isNotMerging() {
        return _mergeInfo == MERGE_DEFAULT;
    }

    public boolean isMergedToAnotherMap() {
        return _mergeInfo == MERGE_DONE;
    }

    //

    public int getWatchers() {
        return _watchers;
    }

    public void setWatchers(int value) {
        _watchers = value;
    }

    public boolean tryToAddWatchers(int count) {
        if (Debug.ENABLED)
            Debug.assertion(count > 0);

        for (;;) {
            int watchers = _watchers;

            if (watchers == 0)
                return false;

            int newWatchers = watchers + count;

            if ((_watchers == watchers ? ((_watchers = newWatchers) == newWatchers) : false))
                return true;
        }
    }

    public Runnable removeWatchers(final Transaction branch, int count, boolean delayed, final Snapshot mapSnapshot) {
        if (Debug.ENABLED)
            Debug.assertion(count > 0);

        for (;;) {
            int watchers = _watchers;

            if (Debug.ENABLED)
                Debug.assertion(watchers > 0);

            int newWatchers = watchers - count;

            if ((_watchers == watchers ? ((_watchers = newWatchers) == newWatchers) : false)) {
                if (newWatchers == 0) {
                    if (!delayed)
                        merge(branch, mapSnapshot);
                    else
                        return new Runnable() {

                            public void run() {
                                merge(branch, mapSnapshot);
                            }
                        };
                }

                return null;
            }
        }
    }

    private void merge(Transaction branch, Snapshot mapSnapshot) {
        boolean walkQueue = mergeAndReturnIfShouldWalkDelayedQueue(branch, mapSnapshot);

        if (walkQueue) {
            for (;;) {
                VersionMap map = branch.pollToMergeLater();

                if (map == null)
                    break;

                if (Debug.ENABLED) {
                    mapSnapshot = Helper.getInstance().getToMergeLater().remove(map);
                    Debug.assertion(mapSnapshot != null);
                    mapSnapshot = mapSnapshot != Helper.TO_MERGE_LATER_IS_NULL ? mapSnapshot : null;
                }

                map.mergeAndReturnIfShouldWalkDelayedQueue(branch, mapSnapshot);
            }
        }
    }

    private boolean mergeAndReturnIfShouldWalkDelayedQueue(Transaction branch, Snapshot mapSnapshot) {
        // TODO bench read field before for less CAS

        /*
         * Try to switch both merge guards before merge, otherwise restore them and add to
         * branch queue to merge later.
         */
        if (!(_mergeInfo == MERGE_DEFAULT? ((_mergeInfo = MERGE_A) == MERGE_A) : false)) {
            if (_mergeInfo != MERGE_DONE) {
                if (Debug.ENABLED) {
                    mapSnapshot = mapSnapshot != null ? mapSnapshot : Helper.TO_MERGE_LATER_IS_NULL;
                    Debug.assertion(Helper.getInstance().getToMergeLater().put(this, mapSnapshot) == null);
                }

                branch.keepToMergeLater(this);
            }

            return false;
        }

        /*
         * From there on mergeInfo has to be released on return so return true to retry
         * potential delayed merges.
         */

        Snapshot snapshot = branch.getSharedSnapshot();
        int aIndex = Helper.getIndex(snapshot, this);

        /*
         * If map not present anymore, it was speculative and has been aborted.
         */
        if (aIndex < 0) {
            if (Debug.ENABLED)
                Debug.assertion(Helper.getIndex(mapSnapshot, this) > mapSnapshot.getAcknowledgedIndex());

            return true;
        }

        if (Debug.ENABLED) {
            Debug.assertion(snapshot.getAcknowledgedIndex() != aIndex);

            if (mapSnapshot == null)
                Debug.assertion(snapshot.getAcknowledgedIndex() > aIndex);
        }

        int bIndex = aIndex + 1;
        VersionMap b = snapshot.getVersionMaps()[bIndex];

        if (!(b._mergeInfo == MERGE_DEFAULT ? ((b._mergeInfo = MERGE_B) == MERGE_B) : false)) {
            if (Debug.ENABLED) {
                mapSnapshot = mapSnapshot != null ? mapSnapshot : Helper.TO_MERGE_LATER_IS_NULL;
                Debug.assertion(Helper.getInstance().getToMergeLater().put(this, mapSnapshot) == null);
            }

            /*
             * Other merge ongoing, add to queue to retry when finished.
             */
            branch.keepToMergeLater(this);

            _mergeInfo = MERGE_DEFAULT;
            return true;
        }

        if (Debug.STM_LOG)
            Log.write("Merging " + this + " to " + b);

        Version[] mergedWrites = merge(this, snapshot.getWrites()[aIndex], b, snapshot.getWrites()[bIndex], false);
        Version[] mergedReads = null;

        if (snapshot.getReads() != null && aIndex != TransactionManager.OBJECTS_VERSIONS_INDEX) {
            if (snapshot.getAcknowledgedIndex() < aIndex) {
                Version[] aReads = snapshot.getReads()[aIndex];
                Version[] bReads = snapshot.getReads()[bIndex];

                if (aReads != null && bReads != null)
                    mergedReads = merge(this, aReads, b, bReads, true);
                else if (aReads != null)
                    mergedReads = aReads;
                else if (bReads != null)
                    mergedReads = bReads;
            }
        }

        /**
         * Make sure merged map is marked as propagated if one of the source is.
         */
        if (getSource() != null && getSource().Propagated) {
            if (b.getSource() == null)
                b.setSource(getSource());
            else if (!b.getSource().Propagated)
                b.setSource(new Source(b.getSource().Connection, b.getSource().InterceptionId, true));
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
                    reads = Helper.removeReads(reads, aIndex);
                    reads[aIndex] = mergedReads;
                } else
                    reads = null;
            }

            newSnapshot.setReads(reads);
            newSnapshot.setWrites(Helper.removeVersions(snapshot.getWrites(), aIndex));
            newSnapshot.getWrites()[aIndex] = mergedWrites;
            newSnapshot.setSlowChanging(snapshot.getSlowChanging());
            newSnapshot.setInterception(snapshot.getInterception());

            if (snapshot.getAcknowledgedIndex() < aIndex)
                newSnapshot.setAcknowledgedIndex(snapshot.getAcknowledgedIndex());
            else
                newSnapshot.setAcknowledgedIndex(snapshot.getAcknowledgedIndex() - 1);

            if (Debug.ENABLED) {
                Debug.assertion(newSnapshot.getVersionMaps()[bIndex - 1] == b);
                Debug.assertion(newSnapshot.getWrites()[TransactionManager.OBJECTS_VERSIONS_INDEX] == TransactionManager.OBJECTS_VERSIONS);
            }

            if (branch.casSharedSnapshot(snapshot, newSnapshot)) {
                if (Debug.ENABLED) {
                    Debug.assertion(_mergeInfo == MERGE_A);
                    Debug.assertion(b._mergeInfo == MERGE_B);
                }

                if (Stats.ENABLED)
                    Stats.getInstance().Merged.incrementAndGet();

                _mergeInfo = MERGE_DONE;
                b._mergeInfo = MERGE_DEFAULT;

                if (Debug.THREADS) {
                    ThreadAssert.addSharedDefinitively(this);
                    ThreadAssert.removeShared(this);
                }

                return true;
            }

            snapshot = branch.getSharedSnapshot();
            aIndex = Helper.getIndex(snapshot, this);

            /*
             * If map not present anymore, it was speculative and has been aborted.
             */
            if (aIndex < 0) {
                if (Debug.ENABLED) {
                    if (mapSnapshot == null)
                        throw new AssertionError();

                    Debug.assertion(Helper.getIndex(mapSnapshot, this) > mapSnapshot.getAcknowledgedIndex());
                }

                _mergeInfo = MERGE_DONE;
                b._mergeInfo = MERGE_DEFAULT;
                return true;
            }

            if (Debug.ENABLED) {
                // Only to check the assertion before the CAS
                bIndex = Helper.getIndex(snapshot, b);
                Debug.assertion(bIndex >= 0);
            }
        }
    }

    //

    /**
     * The merge process is crossed between maps and versions. Functionally map a is
     * merged to map b and removed, but data is merged from versions b to versions a. C.f.
     * method Version.merge.
     */
    private static Version[] merge(VersionMap a, Version[] aVersions, VersionMap b, Version[] bVersions, boolean reads) {
        if (Debug.ENABLED) {
            Debug.assertion(a._watchers == 0);
            Debug.assertion(a._mergeInfo == MERGE_A);
            Debug.assertion(b._mergeInfo == MERGE_B);
        }

        Version[] result = aVersions;

        if (aVersions == TransactionManager.OBJECTS_VERSIONS) {
            for (int i = bVersions.length - 1; i >= 0; i--) {
                if (bVersions[i] != null) {
                    Version shared = (Version) bVersions[i].getUnion();
                    Version merged = merge(shared, bVersions[i], Version.MERGE_FLAG_NONE);

                    if (Debug.ENABLED)
                        Debug.assertion(merged == shared);
                }
            }
        } else {
            boolean clone = false;

            /*
             * Cannot clone only if a.Propagated != b.Propagated as a transaction can
             * start with propagated maps in its snapshot, and then have new maps
             * propagated and merged to them.
             */
            clone |= a.getSource() != null && a.getSource().Propagated;
            clone |= b.getSource() != null && b.getSource().Propagated;

            for (int i = bVersions.length - 1; i >= 0; i--) {
                if (bVersions[i] != null) {
                    Version shared = (Version) bVersions[i].getUnion();
                    int hash = System.identityHashCode(shared);
                    Version aVersion = TransactionSets.getVersion(result, shared, hash);

                    if (aVersion != null) {
                        Version merged = aVersion;
                        int flags = Version.MERGE_FLAG_NONE;

                        if (reads)
                            flags |= Version.MERGE_FLAG_READS;

                        /*
                         * If a map is propagated, transactions started before the
                         * propagation will not have it in their snapshot. When merging,
                         * all data must be copied to new version instances to make sure
                         * it does not become visible to those transactions or it would
                         * change their snapshot.
                         */
                        if (clone) {
                            merged = merged.cloneThis(reads, true);
                            flags |= Version.MERGE_FLAG_COPY_ARRAY_ELEMENTS;
                        }

                        merged = merge(aVersion, merged, bVersions[i], flags);

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
                                int index = TransactionSets.getIndex(result, shared, hash);

                                if (Debug.ENABLED)
                                    Debug.assertion(result[index] == aVersion);

                                result[index] = merged;
                            }
                        }
                    } else
                        result = TransactionSets.put(result, bVersions[i], shared, hash, false);
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

    public void onAborted() {
        if (_transaction != null)
            if ((_mergeInfo == MERGE_DEFAULT ? ((_mergeInfo = MERGE_DONE) == MERGE_DONE) : false))
                dispose(_transaction);
    }

    private static final void dispose(Transaction transaction) {
        if (transaction != null) {
            if (Debug.THREADS) {
                ThreadAssert.removeShared(transaction);
                ThreadAssert.addPrivate(transaction);
                Debug.assertion(transaction.isCommitted());
            }

            Transaction parent = transaction.getParent();
            boolean isRemote = transaction.isRemote();

            if (Debug.ENABLED)
                Debug.assertion(parent == transaction.getBranch());

            if (!Transaction.currentNull())
                Transaction.setCurrentUnsafe(null);

            transaction.reset();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            if (!isRemote)
                parent.recycleToPublic(transaction);
        }
    }

    static Version merge(Version target, Version source, int flags) {
        return merge(target, target, source, flags);
    }

    private static Version merge(Version target, Version merged, Version source, int flags) {
        List<Object> sourceWrites, sourceClones, targetWrites, targetClones = null;

        if (Debug.ENABLED) {
            sourceWrites = new List<Object>();
            targetWrites = new List<Object>();
            source.getContentForDebug(sourceWrites);
            target.getContentForDebug(targetWrites);
            sourceClones = cloneArrays(sourceWrites);

            if ((flags & Version.MERGE_FLAG_COPY_ARRAY_ELEMENTS) != 0)
                targetClones = cloneArrays(targetWrites);

            if (target.isShared())
                Debug.assertion(flags == Version.MERGE_FLAG_NONE);
        }

        Version result = merged.merge(merged, source, flags);

        if (Debug.ENABLED) {
            result.checkInvariants();

            /*
             * No need to clone if private or by copy (already a new instance).
             */
            if ((flags & (Version.MERGE_FLAG_PRIVATE | Version.MERGE_FLAG_COPY_ARRAYS | Version.MERGE_FLAG_COPY_ARRAY_ELEMENTS)) != 0)
                Debug.assertion(result == merged);

            if (target.isShared())
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
             * Assert all arrays copied.
             */
            if ((flags & Version.MERGE_FLAG_COPY_ARRAYS) != 0) {
                Debug.assertion(merged == target);
                Debug.assertion(targetWrites2.size() == sourceWrites2.size());

                for (int i = 0; i < targetWrites2.size(); i++) {
                    Object element = targetWrites2.get(i);

                    if (element instanceof Object[]) {
                        Debug.assertion(element != sourceWrites2.get(i));
                        PlatformAdapter.shallowEquals(element, sourceWrites2.get(i), Object[].class);
                    }
                }
            }

            /*
             * Assert source has not be modified.
             */
            assertNoChange(sourceWrites, sourceClones, sourceWrites2);

            /*
             * Assert target has not be modified if not in-place merge.
             */
            if ((flags & Version.MERGE_FLAG_COPY_ARRAY_ELEMENTS) != 0)
                assertNoChange(targetWrites, targetClones, targetWrites2);

            /*
             * Assert that even if target has been modified, the objects themselves are
             * the same or from source. New arrays would not be visible to other threads.
             */
            if ((flags & Version.MERGE_FLAG_PRIVATE) == 0)
                if (!(target instanceof TKeyedSharedVersion) && !(target instanceof TListSharedVersion))
                    for (int i = 0; i < targetWrites.size(); i++)
                        if (targetWrites.get(i) != null && targetWrites.get(i).getClass().isArray())
                            Debug.assertion(targetWrites2.get(i) == targetWrites.get(i) || sourceWrites.contains(targetWrites2.get(i)));

            /*
             * Assert hard references are valid.
             */
            if (result instanceof TIndexedNVersion && result.isShared()) {
                TIndexedNVersion indexed = (TIndexedNVersion) result;
                Reference reference = result.getReference();
                int count = 0;

                for (int i = 0; i < indexed.length(); i++) {
                    Object value = indexed.getAsObject(i);

                    if (value instanceof TObject) {
                        Debug.assertion(reference.containsUserReference(((TObject) value).getUserTObject_objectfabric()));
                        count++;
                    }
                }

                Debug.assertion(reference.sizeUserReferences() == count);
            }
        }

        return result;
    }

    private static List<Object> cloneArrays(List<Object> array) {
        List<Object> clone = new List<Object>();

        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);

            if (element instanceof Object[])
                clone.add(PlatformAdapter.clone((Object[]) element));
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
                Debug.assertion(PlatformAdapter.shallowEquals(element, clones.get(i), Object[].class));
        }
    }

    // Debug

    public final ArrayList<Object> getWatcherObjects() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        return Helper.getInstance().getWatchers(this);
    }
}
