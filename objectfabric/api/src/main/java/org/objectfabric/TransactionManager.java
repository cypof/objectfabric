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

import java.util.Arrays;

import org.objectfabric.Resource.Block;
import org.objectfabric.TObject.Transaction;
import org.objectfabric.TObject.Version;
import org.objectfabric.Workspace.Granularity;

final class TransactionManager {

    /*
     * TODO extend AtomicReferenceArray and move all CAS here with indexes far from each
     * other to avoid same cache line conflicts.
     */

    public static final int OBJECTS_VERSIONS_INDEX = 0;

    private static final int MAP_QUEUE_SIZE_THRESHOLD = 80;

    private static final int MAP_QUEUE_SIZE_MAXIMUM = 100;

    public static final TObject.Version[] OBJECTS_VERSIONS = new TObject.Version[0];

    private TransactionManager() {
        throw new IllegalStateException();
    }

    public static boolean commit(Transaction transaction) {
        if (Debug.ENABLED) {
            transaction.checkInvariants();
            transaction.assertUpdates();
        }

        Transaction parent = transaction.parent();
        boolean result;

        if (parent == null) {
            Workspace workspace = transaction.workspace();

            if (transaction.getWrites() != null) {
                if (Debug.ENABLED) {
                    Debug.assertion(!transaction.noWrites());
                    Helper.instance().checkVersionsHaveWrites(transaction.getWrites());
                }

                result = validateAndUpdateSnapshot(transaction);
            } else {
                Snapshot snapshot = transaction.getSnapshot();

                if (Debug.ENABLED)
                    workspace.releaseSnapshotDebug(snapshot, transaction, "TransactionManager.commit (No write)");

                workspace.releaseSnapshot(snapshot);
                transaction.reset();

                if (Debug.THREADS)
                    ThreadAssert.removePrivate(transaction);

                workspace.recycle(transaction);
                result = true;
            }

            if (Stats.ENABLED) {
                if (result)
                    Stats.Instance.Committed.incrementAndGet();
            }
        } else {
            parent.mergePrivate(transaction);

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            result = true;
        }

        return result;
    }

    private static boolean validateAndUpdateSnapshot(Transaction transaction) {
        final Workspace workspace = transaction.workspace();
        final Version[] reads = transaction.getReads();
        final Version[] writes = transaction.getWrites();
        final VersionMap map = transaction.getOrCreateVersionMap();

        Snapshot snapshot;
        Snapshot lastValidated = transaction.getSnapshot();
        int lastValidatedAddedWatchers = 1;
        List<Object> lastValidatedAddedWatchersList = null;

        if (Debug.ENABLED) {
            Debug.assertion(transaction.parent() == null);
            lastValidatedAddedWatchersList = new List<Object>();
            lastValidatedAddedWatchersList.add(transaction);
        }

        final Snapshot newSnapshot = new Snapshot();
        int retryCount = 0;

        for (;;) {
            int addedWatchers = 0;
            int temporaryWatchers = 0;
            List<Object> watchersList = null;
            List<Object> temporaryWatchersList = null;
            boolean preventsMerge;

            for (;;) {
                for (;;) {
                    snapshot = workspace.snapshot();

                    if (snapshot.last() == VersionMap.CLOSING)
                        throw new ClosedException();

                    if (Stats.ENABLED) {
                        for (;;) {
                            long max = Stats.Instance.MaxMapCount.get();

                            if (snapshot.getVersionMaps().length <= max)
                                break;

                            if (Stats.Instance.MaxMapCount.compareAndSet(max, snapshot.getVersionMaps().length))
                                break;
                        }
                    }

                    /*
                     * TODO also measure size of each map, do not allow merging of maps if
                     * over size of a commit using same stuff as for source splitting.
                     */

                    if (snapshot.getVersionMaps().length >= MAP_QUEUE_SIZE_MAXIMUM)
                        workspace.onOverloaded();
                    else {
                        if (snapshot.getVersionMaps().length >= MAP_QUEUE_SIZE_THRESHOLD)
                            workspace.onOverloading();

                        break;
                    }
                }

                if (Debug.ENABLED) {
                    watchersList = new List<Object>();
                    temporaryWatchersList = new List<Object>();
                }

                Extension[] sourceSplitters = null;
                Extension[] noMerge = null;

                if (snapshot.slowChanging() != null) {
                    sourceSplitters = snapshot.slowChanging().Splitters;

                    if (workspace.granularity() == Granularity.ALL)
                        noMerge = snapshot.slowChanging().Extensions;
                }

                preventsMerge = noMerge != null;

                if (sourceSplitters != null) {
                    if (snapshot.last().isRemote() != map.isRemote()) {
                        addedWatchers += sourceSplitters.length;

                        if (Debug.ENABLED)
                            for (Extension sourceSplitter : sourceSplitters)
                                watchersList.add(sourceSplitter);
                    }
                }

                if (noMerge != null) {
                    addedWatchers += noMerge.length;

                    if (Debug.ENABLED)
                        for (Extension extension : noMerge)
                            watchersList.add(extension);
                }

                /*
                 * If snapshot has changed since transaction's start or last validation,
                 * add at least one watcher to prevent last map from merging with future
                 * ones during validation.
                 */
                if (snapshot != lastValidated && addedWatchers == 0) {
                    addedWatchers++;
                    temporaryWatchers++;

                    if (Debug.ENABLED) {
                        watchersList.add(transaction);
                        temporaryWatchersList.add(transaction);
                    }
                }

                if (addedWatchers == 0 || snapshot.last().tryToAddWatchers(addedWatchers)) {
                    if (Debug.ENABLED)
                        for (int i = 0; i < watchersList.size(); i++)
                            Helper.instance().addWatcher(snapshot.last(), watchersList.get(i), snapshot, "validateAndUpdateSnapshot");

                    /*
                     * If we use transaction's snapshot, keep it until after commit
                     * instead of after validation only.
                     */
                    if (snapshot == transaction.getSnapshot()) {
                        addedWatchers++;
                        temporaryWatchers++;
                        lastValidatedAddedWatchers--;

                        if (Debug.ENABLED) {
                            watchersList.add(transaction);
                            temporaryWatchersList.add(transaction);

                            if (lastValidatedAddedWatchersList == null)
                                throw new AssertionError();

                            boolean r = lastValidatedAddedWatchersList.remove(transaction);
                            Debug.assertion(r);
                        }
                    }

                    break;
                }
            }

            Runnable delayedMerge = null;

            {
                boolean conflict = false;

                if (snapshot.last() != lastValidated.last()) {
                    if (reads != null) {
                        int start = Helper.getIndex(snapshot, lastValidated.last()) + 1;
                        int stop = snapshot.writes().length;

                        if (!Helper.validateCheckOnce(map, reads, snapshot, start, stop)) {
                            if (Debug.ENABLED)
                                Debug.assertion(!Helper.instance().AssertNoConflict);

                            conflict = true;
                        }
                    }
                }

                if (lastValidatedAddedWatchers != 0) {
                    if (Debug.ENABLED) {
                        if (lastValidatedAddedWatchersList == null)
                            throw new AssertionError();

                        for (int i = 0; i < lastValidatedAddedWatchersList.size(); i++)
                            Helper.instance().removeWatcher(lastValidated.last(), lastValidatedAddedWatchersList.get(i), snapshot, "Validation done");
                    }

                    delayedMerge = lastValidated.last().removeWatchers(workspace, lastValidatedAddedWatchers, true, lastValidated);
                }

                if (Debug.ENABLED) {
                    if (Helper.instance().ConflictAlways)
                        conflict = true;

                    if (Helper.instance().ConflictRandom && Platform.get().randomBoolean())
                        conflict = true;
                }

                if (conflict) {
                    if (delayedMerge != null)
                        delayedMerge.run();

                    dispose(transaction, addedWatchers, watchersList, snapshot);
                    return false;
                }
            }

            //

            newSnapshot.setVersionMaps(Helper.addVersionMap(snapshot.getVersionMaps(), map));

            if (reads != null && snapshot.getReads() == null) {
                Version[][] newReads = new Version[newSnapshot.getVersionMaps().length][];
                newReads[newReads.length - 1] = reads;
                newSnapshot.setReads(newReads);
            } else if (reads != null || snapshot.getReads() != null)
                newSnapshot.setReads(Helper.addVersions(snapshot.getReads(), reads));
            else
                newSnapshot.setReads(null);

            newSnapshot.writes(Helper.addVersions(snapshot.writes(), writes));
            newSnapshot.slowChanging(snapshot.slowChanging());

            if (preventsMerge) {
                transaction.setSnapshot(newSnapshot);
                transaction.setPublicSnapshotVersions(newSnapshot.writes());
                transaction.setWrites(null);

                if (Debug.ENABLED) {
                    Helper.instance().disableEqualsOrHashCheck();
                    Helper.instance().getAllowExistingReadsOrWrites().put(transaction, transaction);
                    Helper.instance().enableEqualsOrHashCheck();
                }

                transaction.addFlags(TransactionBase.FLAG_IGNORE_READS | TransactionBase.FLAG_NO_WRITES | TransactionBase.FLAG_COMMITTED);
                map.setTransaction(transaction);
            } else if (!map.isRemote())
                map.setTransaction(null);

            for (int i = writes.length - 1; i >= 0; i--)
                if (writes[i] != null)
                    writes[i].onPublishing(newSnapshot, snapshot.writes().length);

            if (Debug.THREADS) {
                ThreadAssert.share(map);

                if (preventsMerge)
                    ThreadAssert.share(transaction);
            }

            /*
             * Try to publish the new snapshot. TODO: do merge on same CAS, only if fails
             * use delayedMerge.
             */
            if (!workspace.casSnapshot(snapshot, newSnapshot)) {
                if (Debug.THREADS) {
                    if (preventsMerge) {
                        ThreadAssert.removeShared(transaction);
                        ThreadAssert.addPrivate(transaction);
                    }

                    ThreadAssert.removeShared(map);
                    ThreadAssert.addPrivate(map);
                }

                if (delayedMerge != null)
                    delayedMerge.run();

                if (Debug.ENABLED) {
                    retryCount++;

                    if (Stats.ENABLED) {
                        Stats.Instance.ValidationRetries.incrementAndGet();

                        for (;;) {
                            long max = Stats.Instance.ValidationRetriesMax.get();

                            if (retryCount <= max)
                                break;

                            if (Stats.Instance.ValidationRetriesMax.compareAndSet(max, retryCount))
                                break;
                        }
                    }

                    Helper.instance().setRetryCount(retryCount);
                }

                /*
                 * Store added watchers so that they are removed after next validation.
                 */
                lastValidated = snapshot;
                lastValidatedAddedWatchers = addedWatchers;

                if (Debug.ENABLED)
                    lastValidatedAddedWatchersList = watchersList;
            } else {
                /*
                 * Success!
                 */
                if (Debug.ENABLED) {
                    /*
                     * Assert all watched maps are still there.
                     */
                    for (int i = 0; i < snapshot.getVersionMaps().length; i++) {
                        VersionMap current = snapshot.getVersionMaps()[i];

                        if (!Arrays.asList(newSnapshot.getVersionMaps()).contains(current))
                            Debug.assertion(current.getWatchers() == 0);
                    }

                    if (Debug.THREADS)
                        for (int i = 0; i < newSnapshot.getVersionMaps().length; i++)
                            Debug.assertion(!ThreadAssert.isPrivate(newSnapshot.getVersionMaps()[i]));

                    Helper.instance().removeValidated(map);
                }

                if (delayedMerge != null)
                    delayedMerge.run();

                if (Debug.ENABLED) {
                    if (temporaryWatchersList == null)
                        throw new AssertionError();

                    for (int i = 0; i < temporaryWatchersList.size(); i++)
                        Helper.instance().removeWatcher(snapshot.last(), temporaryWatchersList.get(i), snapshot, "Success!");

                    Helper.instance().removeWatcher(snapshot.last(), workspace, snapshot, "No more last");
                }

                snapshot.last().removeWatchers(workspace, temporaryWatchers + 1, false, snapshot);

                if (snapshot.slowChanging() != null) {
                    Actor[] actors = snapshot.slowChanging().Actors;

                    if (actors != null)
                        for (int i = actors.length - 1; i >= 0; i--)
                            actors[i].requestRun();
                }

                if (!preventsMerge) {
                    transaction.reset();

                    if (Debug.THREADS)
                        ThreadAssert.removePrivate(transaction);

                    workspace.recycle(transaction);
                }

                return true;
            }
        }
    }

    // Merge with abort
    private static void dispose(Transaction transaction, int addedWatchers, List<Object> watchersList, Snapshot snapshot) {
        if (Debug.ENABLED)
            Debug.assertion(transaction.parent() == null);

        Workspace workspace = transaction.workspace();

        if (addedWatchers != 0) {
            snapshot.last().removeWatchers(workspace, addedWatchers, false, snapshot);

            if (Debug.ENABLED)
                for (int i = 0; i < watchersList.size(); i++)
                    Helper.instance().removeWatcher(snapshot.last(), watchersList.get(i), snapshot, "Validation failed");
        }

        if (Debug.ENABLED) {
            if (transaction.getVersionMap() != null)
                Helper.instance().removeWatcher(transaction.getVersionMap(), workspace, snapshot, "dispose uncommitted map");

            Helper.instance().removeValidated(transaction.getVersionMap());
        }

        if (Stats.ENABLED)
            Stats.Instance.Aborted.incrementAndGet();

        if (Debug.THREADS)
            if (transaction.getVersionMap() != null)
                ThreadAssert.removePrivate(transaction.getVersionMap());

        transaction.reset();

        if (Debug.THREADS)
            ThreadAssert.removePrivate(transaction);

        workspace.recycle(transaction);
    }

    /**
     * Commits versions without validation.
     */
    public static boolean load(Workspace workspace, Version[] versions, VersionMap expect, Resource resource, List<Block> acks) {
        if (Debug.ENABLED)
            Debug.assertion(versions.length > 0);

        final VersionMap map = new VersionMap();
        map.setRemote();
        final Snapshot newSnapshot = new Snapshot();
        int retryCount = 0;

        for (;;) {
            Snapshot snapshot;
            int lastAckWatchers;
            List<Object> lastAckWatchersList;

            Extension[] sourceSplitters = null;
            Extension[] noMerge = null;
            boolean preventsMerge;

            for (;;) {
                snapshot = workspace.snapshot();

                if (snapshot.last() != expect) {
                    if (Debug.THREADS)
                        ThreadAssert.removePrivate(map);

                    return false;
                }

                if (snapshot.slowChanging() != null) {
                    sourceSplitters = snapshot.slowChanging().Splitters;

                    if (workspace.granularity() == Granularity.ALL)
                        noMerge = snapshot.slowChanging().Extensions;
                }

                preventsMerge = noMerge != null;
                lastAckWatchers = 0;
                lastAckWatchersList = Debug.ENABLED ? new List<Object>() : null;

                if (sourceSplitters != null) {
                    if (!snapshot.last().isRemote()) {
                        lastAckWatchers += sourceSplitters.length;

                        if (Debug.ENABLED)
                            for (Extension sourceSplitter : sourceSplitters)
                                lastAckWatchersList.add(sourceSplitter);
                    }
                }

                if (noMerge != null) {
                    lastAckWatchers += noMerge.length;

                    if (Debug.ENABLED)
                        for (Extension extension : noMerge)
                            lastAckWatchersList.add(extension);
                }

                if (lastAckWatchers == 0 || snapshot.last().tryToAddWatchers(lastAckWatchers)) {
                    if (Debug.ENABLED)
                        for (int i = 0; i < lastAckWatchersList.size(); i++)
                            Helper.instance().addWatcher(snapshot.last(), lastAckWatchersList.get(i), snapshot, "load ackWatchersList");

                    break;
                }
            }

            if (Debug.ENABLED)
                Helper.instance().addWatcher(map, workspace, snapshot, "TransactionManager::load");

            //

            newSnapshot.setVersionMaps(Helper.addVersionMap(snapshot.getVersionMaps(), map));

            if (snapshot.getReads() != null)
                newSnapshot.setReads(Helper.addVersions(snapshot.getReads(), null));
            else
                newSnapshot.setReads(null);

            newSnapshot.writes(Helper.addVersions(snapshot.writes(), versions));
            newSnapshot.slowChanging(snapshot.slowChanging());

            Transaction transaction = null;

            if (preventsMerge) {
                transaction = workspace.getOrCreateTransaction();
                transaction.setSnapshot(newSnapshot);
                transaction.setPublicSnapshotVersions(newSnapshot.writes());

                if (Debug.ENABLED)
                    Helper.instance().getAllowExistingReadsOrWrites().put(transaction, transaction);

                transaction.addFlags(TransactionBase.FLAG_IGNORE_READS | TransactionBase.FLAG_NO_WRITES | TransactionBase.FLAG_COMMITTED);
                map.setTransaction(transaction);
                transaction.setVersionMap(map);
            }

            // TODO: find out if publishing is still needed
            for (int i = versions.length - 1; i >= 0; i--) {
                if (versions[i] != null) {
                    int todo_merge_methods;
                    versions[i].onDeserialized(newSnapshot);
                    versions[i].onPublishing(newSnapshot, snapshot.getVersionMaps().length);
                }
            }

            if (Debug.THREADS) {
                ThreadAssert.share(map);

                if (preventsMerge)
                    ThreadAssert.share(transaction);
            }

            if (!workspace.casSnapshot(snapshot, newSnapshot)) {
                if (Debug.THREADS) {
                    if (preventsMerge) {
                        ThreadAssert.removeShared(transaction);
                        ThreadAssert.addPrivate(transaction);
                    }

                    ThreadAssert.removeShared(map);
                    ThreadAssert.addPrivate(map);
                }

                if (Debug.ENABLED) {
                    retryCount++;

                    if (Stats.ENABLED) {
                        Stats.Instance.ValidationRetries.incrementAndGet();
                        Stats.max(Stats.Instance.ValidationRetriesMax, retryCount);
                    }

                    Helper.instance().setRetryCount(retryCount);
                    Helper.instance().removeWatcher(map, workspace, snapshot, "TransactionManager::load");

                    for (int i = 0; i < lastAckWatchersList.size(); i++)
                        Helper.instance().removeWatcher(snapshot.last(), lastAckWatchersList.get(i), snapshot, "TransactionManager::load ackWatchersList");
                }

                if (lastAckWatchers != 0)
                    snapshot.last().removeWatchers(workspace, lastAckWatchers, false, null);

                if (Debug.ENABLED)
                    Debug.assertion((transaction != null) == preventsMerge);

                if (transaction != null) {
                    transaction.reset();
                    workspace.recycle(transaction);
                    map.setTransaction(null);
                    transaction = null;
                }
            } else {
                resource.onLoad(newSnapshot, acks);

                if (Debug.ENABLED)
                    Helper.instance().removeWatcher(snapshot.last(), workspace, snapshot, "TransactionManager::load old");

                snapshot.last().removeWatchers(workspace, 1, false, null);

                //

                if (snapshot.slowChanging() != null) {
                    Actor[] actors = snapshot.slowChanging().Actors;

                    if (actors != null)
                        for (int i = actors.length - 1; i >= 0; i--)
                            actors[i].requestRun();
                }

                return true;
            }
        }
    }

    public static void abort(Transaction transaction) {
        if (Debug.ENABLED)
            transaction.checkInvariants();

        Workspace workspace = transaction.workspace();
        Transaction parent = transaction.parent();

        if (parent == null) {
            if (Debug.ENABLED) {
                if (transaction.getVersionMap() != null)
                    Helper.instance().removeWatcher(transaction.getVersionMap(), workspace, transaction.getSnapshot(), "TransactionManager::load abort");

                workspace.releaseSnapshotDebug(transaction.getSnapshot(), transaction, "TransactionManager::load abort");
            }

            workspace.releaseSnapshot(transaction.getSnapshot());
            transaction.reset();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            workspace.recycle(transaction);

            if (Stats.ENABLED)
                Stats.Instance.Aborted.incrementAndGet();
        } else {
            if (Debug.ENABLED)
                if (transaction.getVersionMap() != null)
                    Helper.instance().removeWatcher(transaction.getVersionMap(), parent, transaction.getSnapshot(), "TransactionManager::load abort private");

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);
        }
    }
}
