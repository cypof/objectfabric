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

import java.util.Arrays;
import java.util.concurrent.Future;

import com.objectfabric.Interception.MultiMapInterception;
import com.objectfabric.Interception.SingleMapInterception;
import com.objectfabric.Snapshot.SlowChanging;
import com.objectfabric.TObject.Version;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.Transaction.ConflictDetection;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.VersionMap.Source;
import com.objectfabric.misc.CompletedFuture;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.WritableFuture;

final class TransactionManager {

    private static final CompletedFuture<CommitStatus> FUTURE_SUCCESS = PlatformAdapter.createCompletedCommitStatusFuture(CommitStatus.SUCCESS);

    private static final CompletedFuture<CommitStatus> FUTURE_CONFLICT = PlatformAdapter.createCompletedCommitStatusFuture(CommitStatus.CONFLICT);

    private static final CompletedFuture<CommitStatus> FUTURE_ABORT = PlatformAdapter.createCompletedCommitStatusFuture(CommitStatus.ABORT);

    public static final int OBJECTS_VERSIONS_INDEX = 0;

    public static final TObject.Version[] OBJECTS_VERSIONS = new TObject.Version[0];

    private TransactionManager() {
        throw new IllegalStateException();
    }

    public static final Future<CommitStatus> commit(Transaction transaction, WritableFuture<CommitStatus> callback) {
        if (Debug.ENABLED)
            transaction.checkInvariants();

        Transaction parent = transaction.getParent();
        Future<CommitStatus> result;

        if (parent.isPublic()) {
            Version[] imports = transaction.getImports();

            if (imports != null)
                propagate(parent, imports, VersionMap.IMPORTS_SOURCE);

            Snapshot snapshot = transaction.getSnapshot();

            if (transaction.getWrites() != null) {
                if (Debug.ENABLED) {
                    Debug.assertion(!transaction.noWrites());
                    Helper.getInstance().checkVersionsHaveWrites(transaction.getWrites());

                    if (snapshot.getAcknowledgedIndex() != snapshot.getLastIndex())
                        Helper.getInstance().removeWatcher(snapshot.getAcknowledged(), transaction, "TransactionManager.commit");
                }

                if (snapshot.getAcknowledgedIndex() != snapshot.getLastIndex())
                    snapshot.getAcknowledged().removeWatchers(parent, 1, false, snapshot);

                result = validateAndUpdateSnapshot(transaction, callback);
            } else {
                if (callback != null) {
                    callback.set(CommitStatus.SUCCESS);
                    result = callback;
                } else
                    result = FUTURE_SUCCESS;

                if (Debug.ENABLED)
                    parent.releaseSnapshotDebug(snapshot, transaction, "TransactionManager.commit (No write)");

                parent.releaseSnapshot(snapshot);
                transaction.reset();

                if (Debug.THREADS)
                    ThreadAssert.removePrivate(transaction);

                if (!transaction.isRemote())
                    parent.recycleToPublic(transaction);
            }
        } else {
            parent.mergePrivate(transaction);
            transaction.reset();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            if (!transaction.isRemote())
                parent.recycleToPrivate(transaction);

            if (callback != null) {
                callback.set(CommitStatus.SUCCESS);
                result = callback;
            } else
                result = FUTURE_SUCCESS;
        }

        if (Stats.ENABLED) {
            try {
                if (result.isDone() && result.get() == CommitStatus.SUCCESS)
                    Stats.getInstance().Committed.incrementAndGet();
            } catch (Exception e) {
                // Ignore
            }
        }

        return result;
    }

    private static final Future<CommitStatus> validateAndUpdateSnapshot(Transaction transaction, WritableFuture<CommitStatus> callback) {
        final Transaction branch = transaction.getParent();
        final Version[] reads = transaction.getReads();
        final Version[] writes = transaction.getWrites();
        final VersionMap map = transaction.getOrCreateVersionMap();
        final int flags = transaction.getFlags();

        Snapshot snapshot;
        Interception interception = null;
        Snapshot lastValidated = transaction.getSnapshot();
        int lastValidatedAddedWatchers = 1;
        List<Object> lastValidatedAddedWatchersList = null;

        if (Debug.ENABLED) {
            Debug.assertion(branch == transaction.getBranch());
            lastValidatedAddedWatchersList = new List<Object>();
            lastValidatedAddedWatchersList.add(transaction);
        }

        Snapshot newSnapshot = new Snapshot();
        int retryCount = 0;

        for (;;) {
            int addedWatchers = 0;
            int temporaryWatchers = 0;
            List<Object> watchersList = null;
            List<Object> temporaryWatchersList = null;

            for (;;) {
                boolean firstNotification = true;

                for (;;) {
                    snapshot = branch.getSharedSnapshot();

                    if (Stats.ENABLED) {
                        for (;;) {
                            long max = Stats.getInstance().MaxMapCount.get();

                            if (snapshot.getVersionMaps().length <= max)
                                break;

                            if (Stats.getInstance().MaxMapCount.compareAndSet(max, snapshot.getVersionMaps().length))
                                break;
                        }
                    }

                    if (snapshot.getVersionMaps().length >= OverloadHandler.MAP_QUEUE_SIZE_MAXIMUM) {
                        OverloadHandler.getInstance().onMapQueueSizeMaximumReached(branch, firstNotification);
                        firstNotification = false;
                    } else {
                        if (snapshot.getVersionMaps().length >= OverloadHandler.MAP_QUEUE_SIZE_THRESHOLD)
                            OverloadHandler.getInstance().onMapQueueSizeThresholdReached(branch);

                        break;
                    }
                }

                if (Debug.ENABLED) {
                    watchersList = new List<Object>();
                    temporaryWatchersList = new List<Object>();
                }

                SourceSplitter[] sourceSplitters;
                Walker[] noMerge;

                if (snapshot.getSlowChanging() != null) {
                    sourceSplitters = snapshot.getSlowChanging().getSourceSplitters();
                    noMerge = snapshot.getSlowChanging().getNoMergeWalkers();
                } else {
                    sourceSplitters = null;
                    noMerge = null;
                }

                if (sourceSplitters != null) {
                    Connection.Version a = snapshot.getLast().getSource() != null ? snapshot.getLast().getSource().Connection : null;
                    Connection.Version b = map.getSource() != null ? map.getSource().Connection : null;

                    if (a != b) {
                        addedWatchers += sourceSplitters.length;

                        if (Debug.ENABLED)
                            for (SourceSplitter sourceSplitter : sourceSplitters)
                                watchersList.add(sourceSplitter);
                    }
                }

                if (noMerge != null) {
                    addedWatchers += noMerge.length;

                    if (Debug.ENABLED)
                        for (Walker walker : noMerge)
                            watchersList.add(walker);
                }

                if (snapshot.getInterception() instanceof SingleMapInterception) {
                    if (!(interception instanceof SingleMapInterception)) {
                        WritableFuture<CommitStatus> async = callback;

                        if (async == null)
                            async = PlatformAdapter.createCommitStatusFuture();

                        interception = new SingleMapInterception(async);
                    }

                    interception.setId((byte) (snapshot.getInterception().getId() + 1));
                    addedWatchers++;

                    if (Debug.ENABLED)
                        watchersList.add(interception);
                } else
                    interception = snapshot.getInterception();

                map.setInterception(interception);

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

                if (addedWatchers == 0 || snapshot.getLast().tryToAddWatchers(addedWatchers)) {
                    if (Debug.ENABLED)
                        for (int i = 0; i < watchersList.size(); i++)
                            Helper.getInstance().addWatcher(snapshot.getLast(), watchersList.get(i), "validateAndUpdateSnapshot");

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
                CompletedFuture<CommitStatus> result = null;

                if (snapshot.getLast() != lastValidated.getLast()) {
                    int lastValidatedIndex = Helper.getIndex(snapshot, lastValidated.getLast());

                    if (lastValidatedIndex < 0) {
                        /*
                         * Can only happen if we were dependent on a speculative
                         * transaction which has been aborted.
                         */
                        if (Debug.ENABLED) {
                            Snapshot s = transaction.getSnapshot();
                            Debug.assertion(Helper.getIndex(s, lastValidated.getLast()) > s.getAcknowledgedIndex());
                        }

                        result = FUTURE_CONFLICT;
                    } else if (branch.getConflictDetection() == ConflictDetection.READ_WRITE_CONFLICTS) {
                        int start = lastValidatedIndex + 1;
                        int stop = snapshot.getWrites().length;

                        if (!Helper.validateCheckOnce(map, reads, snapshot, start, stop)) {
                            if (Debug.ENABLED)
                                Debug.assertion(!Debug.AssertNoConflict);

                            result = FUTURE_CONFLICT;
                        }
                    } else if (branch.getConflictDetection() == ConflictDetection.WRITE_WRITE_CONFLICTS) {
                        // TODO: search for conflicts
                    }
                }

                if (snapshot.getSlowChanging() != null && snapshot.getSlowChanging().getBlocked() != null) {
                    if (snapshot.getSlowChanging().getBlocked() == SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED)
                        result = FUTURE_ABORT;
                    else if (map.getSource() != null)
                        if (snapshot.getSlowChanging().isBlocked(map.getSource().Connection))
                            result = FUTURE_CONFLICT;
                }

                //

                if (lastValidatedAddedWatchers != 0) {
                    if (Debug.ENABLED) {
                        if (lastValidatedAddedWatchersList == null)
                            throw new AssertionError();

                        for (int i = 0; i < lastValidatedAddedWatchersList.size(); i++)
                            Helper.getInstance().removeWatcher(lastValidated.getLast(), lastValidatedAddedWatchersList.get(i), "Validation done");
                    }

                    delayedMerge = lastValidated.getLast().removeWatchers(branch, lastValidatedAddedWatchers, true, lastValidated);
                }

                if (Debug.ENABLED) {
                    if (Helper.getInstance().ConflictAlways)
                        result = FUTURE_CONFLICT;

                    if (Helper.getInstance().ConflictRandom && PlatformAdapter.getRandomBoolean())
                        result = FUTURE_CONFLICT;
                }

                if (result != null) {
                    if (delayedMerge != null)
                        delayedMerge.run();

                    dispose(transaction, addedWatchers, watchersList, snapshot);

                    if (callback != null)
                        callback.set(result.getDirect());

                    return result;
                }
            }

            //

            newSnapshot.setVersionMaps(Helper.addVersionMap(snapshot.getVersionMaps(), map));

            if (interception != null) {
                if (reads != null && snapshot.getReads() == null) {
                    Version[][] newReads = new Version[newSnapshot.getVersionMaps().length][];
                    newReads[newReads.length - 1] = reads;
                    newSnapshot.setReads(newReads);
                } else if (reads != null || snapshot.getReads() != null)
                    newSnapshot.setReads(Helper.addReads(snapshot.getReads(), reads));
                else
                    newSnapshot.setReads(null);

                newSnapshot.setAcknowledgedIndex(snapshot.getAcknowledgedIndex());
            } else {
                newSnapshot.setReads(null);
                newSnapshot.setAcknowledgedIndex(snapshot.getAcknowledgedIndex() + 1);
            }

            newSnapshot.setWrites(Helper.addVersions(snapshot.getWrites(), writes));
            newSnapshot.setInterception(interception);
            newSnapshot.setSlowChanging(snapshot.getSlowChanging());

            if (newSnapshot.getSlowChanging() != null && branch.getGranularity() == Granularity.ALL) {
                map.setTransaction(transaction);
                transaction.setSnapshot(newSnapshot);
                transaction.setPublicSnapshotVersions(newSnapshot.getWrites());
                transaction.setWrites(null);

                if (Debug.ENABLED) {
                    Helper.getInstance().disableEqualsOrHashCheck();
                    Helper.getInstance().getAllowExistingReadsOrWrites().put(transaction, transaction);
                    Helper.getInstance().enableEqualsOrHashCheck();
                }

                transaction.addFlags(Transaction.FLAG_NO_READS | Transaction.FLAG_NO_WRITES | Transaction.FLAG_COMMITTED);
            } else
                map.setTransaction(null);

            for (int i = writes.length - 1; i >= 0; i--)
                if (writes[i] != null)
                    writes[i].onPublishing(newSnapshot, snapshot.getWrites().length);

            if (Debug.ENABLED)
                TKeyedVersion.assertUpdates(newSnapshot.getWrites(), newSnapshot.getWrites().length - 1);

            if (Debug.THREADS) {
                ThreadAssert.removePrivate(map);
                ThreadAssert.addShared(map);

                if (snapshot.getSlowChanging() != null && branch.getGranularity() == Granularity.ALL) {
                    ThreadAssert.removePrivate(transaction);
                    ThreadAssert.addShared(transaction);
                }
            }

            /*
             * Try to publish the new snapshot. TODO: do merge on same CAS, only if fails
             * use delayedMerge.
             */
            if (!branch.casSharedSnapshot(snapshot, newSnapshot)) {
                if (Debug.THREADS) {
                    if (snapshot.getSlowChanging() != null && branch.getGranularity() == Granularity.ALL) {
                        ThreadAssert.removeShared(transaction);
                        ThreadAssert.addPrivate(transaction);
                    }

                    ThreadAssert.removeShared(map);
                    ThreadAssert.addPrivate(map);
                }

                if (delayedMerge != null)
                    delayedMerge.run();

                /*
                 * Cannot retry if reads have not been logged, assume conflict.
                 */
                if ((flags & Transaction.FLAG_NO_READS) != 0 && (flags & Transaction.FLAG_AUTO) == 0) {
                    dispose(transaction, addedWatchers, watchersList, snapshot);

                    if (callback != null)
                        callback.set(CommitStatus.CONFLICT);

                    return FUTURE_CONFLICT;
                }

                if (Debug.ENABLED) {
                    retryCount++;

                    if (Stats.ENABLED) {
                        Stats.getInstance().Retried.incrementAndGet();

                        for (;;) {
                            long max = Stats.getInstance().MaxRetried.get();

                            if (retryCount <= max)
                                break;

                            if (Stats.getInstance().MaxRetried.compareAndSet(max, retryCount))
                                break;
                        }
                    }

                    Helper.getInstance().setRetryCount(retryCount);
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

                    if (interception == null)
                        Debug.assertion(newSnapshot.getAcknowledgedIndex() == newSnapshot.getLastIndex());

                    if (Debug.THREADS)
                        for (int i = 0; i < newSnapshot.getVersionMaps().length; i++)
                            Debug.assertion(!ThreadAssert.isPrivate(newSnapshot.getVersionMaps()[i]));

                    Helper.getInstance().removeValidated(map);
                }

                if (delayedMerge != null)
                    delayedMerge.run();

                if (Debug.ENABLED) {
                    if (temporaryWatchersList == null)
                        throw new AssertionError();

                    for (int i = 0; i < temporaryWatchersList.size(); i++)
                        Helper.getInstance().removeWatcher(snapshot.getLast(), temporaryWatchersList.get(i), "Success!");

                    Helper.getInstance().removeWatcher(snapshot.getLast(), branch, "No more last");
                }

                snapshot.getLast().removeWatchers(branch, temporaryWatchers + 1, false, snapshot);

                if (snapshot.getSlowChanging() != null) {
                    if (interception != null) {
                        Acknowledger[] acknowledgers = snapshot.getSlowChanging().getAcknowledgers();

                        if (acknowledgers != null)
                            for (int i = acknowledgers.length - 1; i >= 0; i--)
                                acknowledgers[i].requestRun();
                    } else {
                        Walker[] walkers = snapshot.getSlowChanging().getWalkers();

                        if (walkers != null)
                            for (int i = walkers.length - 1; i >= 0; i--)
                                walkers[i].requestRun();
                    }
                }

                if (callback != null) {
                    if (interception == null)
                        callback.set(CommitStatus.SUCCESS);
                    else if (interception instanceof MultiMapInterception) {
                        MultiMapInterception multi = (MultiMapInterception) interception;
                        CommitStatus ack = multi.tryToAddCallback(callback);

                        /*
                         * If interception was already acknowledged, raise callback
                         * directly with result.
                         */
                        if (ack != null)
                            callback.set(ack);
                    }
                }

                if (snapshot.getSlowChanging() == null || branch.getGranularity() != Granularity.ALL) {
                    transaction.reset();

                    if (Debug.THREADS)
                        ThreadAssert.removePrivate(transaction);

                    if (!transaction.isRemote())
                        branch.recycleToPublic(transaction);
                }

                Future<CommitStatus> result;

                if (callback != null)
                    result = callback;
                else if (interception != null)
                    result = interception.getAsync();
                else
                    result = FUTURE_SUCCESS;

                return result;
            }
        }
    }

    private static void dispose(Transaction transaction, int addedWatchers, List<Object> watchersList, Snapshot snapshot) {
        Transaction parent = transaction.getParent();

        if (addedWatchers != 0) {
            snapshot.getLast().removeWatchers(parent, addedWatchers, false, snapshot);

            if (Debug.ENABLED)
                for (int i = 0; i < watchersList.size(); i++)
                    Helper.getInstance().removeWatcher(snapshot.getLast(), watchersList.get(i), "Validation failed");
        }

        if (Debug.ENABLED) {
            if (transaction.getVersionMap() != null)
                Helper.getInstance().removeWatcher(transaction.getVersionMap(), parent, "dispose uncommitted map");

            Helper.getInstance().removeValidated(transaction.getVersionMap());
        }

        if (transaction.getVersionMap().getSource() != null) {
            Connection.Version connection = transaction.getVersionMap().getSource().Connection;

            if (snapshot.getSlowChanging() == null || !snapshot.getSlowChanging().isBlocked(connection))
                block(parent, snapshot, connection);
        }

        if (Stats.ENABLED)
            Stats.getInstance().LocallyAborted.incrementAndGet();

        if (Debug.THREADS)
            if (transaction.getVersionMap() != null)
                ThreadAssert.removePrivate(transaction.getVersionMap());

        boolean remote = transaction.isRemote();
        transaction.reset();

        if (Debug.THREADS)
            ThreadAssert.removePrivate(transaction);

        if (!remote)
            parent.recycleToPublic(transaction);
    }

    /**
     * Commits a transaction without validation, for imports or transactions already
     * validated on another site. Map is inserted between two others, so watcher counts
     * must be adjusted for both sides.
     */
    public static void propagate(Transaction branch, Version[] versions, Source source) {
        if (Debug.ENABLED) {
            Debug.assertion(versions.length > 0);
            Debug.assertion(source.Propagated);
        }

        VersionMap map = new VersionMap();
        map.setSource(source);

        Snapshot newSnapshot = new Snapshot();
        int retryCount = 0;

        for (;;) {
            Snapshot snapshot;
            int index;
            int lastAckWatchers;
            List<Object> lastAckWatchersList;

            SourceSplitter[] sourceSplitters = null;
            Walker[] noMerge = null;

            for (;;) {
                snapshot = branch.getSharedSnapshot();
                index = snapshot.getAcknowledgedIndex() + 1;

                if (snapshot.getSlowChanging() != null) {
                    sourceSplitters = snapshot.getSlowChanging().getSourceSplitters();
                    noMerge = snapshot.getSlowChanging().getNoMergeWalkers();
                }

                lastAckWatchers = 0;
                lastAckWatchersList = Debug.ENABLED ? new List<Object>() : null;

                if (index == snapshot.getVersionMaps().length) {
                    if (sourceSplitters != null) {
                        VersionMap ack = snapshot.getAcknowledged();
                        Connection.Version a = ack.getSource() != null ? ack.getSource().Connection : null;
                        Connection.Version b = source != null ? source.Connection : null;

                        if (a != b) {
                            lastAckWatchers += sourceSplitters.length;

                            if (Debug.ENABLED)
                                for (SourceSplitter sourceSplitter : sourceSplitters)
                                    lastAckWatchersList.add(sourceSplitter);
                        }
                    }

                    if (noMerge != null) {
                        lastAckWatchers += noMerge.length;

                        if (Debug.ENABLED)
                            for (Walker walker : noMerge)
                                lastAckWatchersList.add(walker);
                    }
                }

                if (lastAckWatchers == 0 || snapshot.getAcknowledged().tryToAddWatchers(lastAckWatchers)) {
                    if (Debug.ENABLED)
                        for (int i = 0; i < lastAckWatchersList.size(); i++)
                            Helper.getInstance().addWatcher(snapshot.getAcknowledged(), lastAckWatchersList.get(i), "propagate ackWatchersList");

                    break;
                }
            }

            final Interception interception;

            if (index < snapshot.getVersionMaps().length)
                interception = snapshot.getVersionMaps()[index].getInterception();
            else
                interception = snapshot.getInterception();

            int mapWatchers = 0;
            List<Object> mapWatchersList = Debug.ENABLED ? new List<Object>() : null;

            if (index < snapshot.getVersionMaps().length) {
                mapWatchers++;

                if (Debug.ENABLED) {
                    Debug.assertion(interception != null);
                    mapWatchersList.add(interception);
                }

                if (sourceSplitters != null) {
                    VersionMap next = snapshot.getVersionMaps()[index];
                    Connection.Version a = source != null ? source.Connection : null;
                    Connection.Version b = next.getSource() != null ? next.getSource().Connection : null;

                    if (a != b) {
                        mapWatchers += sourceSplitters.length;

                        if (Debug.ENABLED)
                            for (SourceSplitter sourceSplitter : sourceSplitters)
                                mapWatchersList.add(sourceSplitter);
                    }
                }

                if (noMerge != null) {
                    mapWatchers += noMerge.length;

                    if (Debug.ENABLED)
                        for (Extension extension : noMerge)
                            mapWatchersList.add(extension);
                }
            } else {
                if (interception instanceof MultiMapInterception) {
                    mapWatchers++;

                    if (Debug.ENABLED)
                        mapWatchersList.add(interception);
                }

                mapWatchers++;

                if (Debug.ENABLED)
                    mapWatchersList.add(branch);
            }

            if (map.getWatchers() != mapWatchers)
                map.setWatchers(mapWatchers);

            if (Debug.ENABLED)
                for (int i = 0; i < mapWatchersList.size(); i++)
                    Helper.getInstance().addWatcher(map, mapWatchersList.get(i), "propagate mapWatchersList");

            //

            newSnapshot.setVersionMaps(Helper.insertVersionMap(snapshot.getVersionMaps(), map, index));

            if (snapshot.getReads() != null)
                newSnapshot.setReads(Helper.insertNullReads(snapshot.getReads(), index));
            else
                newSnapshot.setReads(null);

            newSnapshot.setWrites(Helper.insertVersions(snapshot.getWrites(), versions, index));
            newSnapshot.setSlowChanging(snapshot.getSlowChanging());
            newSnapshot.setInterception(snapshot.getInterception());
            newSnapshot.setAcknowledgedIndex(index);

            Transaction transaction = null;

            if (newSnapshot.getSlowChanging() != null && branch.getGranularity() == Granularity.ALL) {
                transaction = branch.getOrCreateChild();
                Snapshot trimmed = TransactionPublic.trim(newSnapshot, newSnapshot.getAcknowledgedIndex());
                transaction.setSnapshot(trimmed);
                transaction.setPublicSnapshotVersions(trimmed.getWrites());

                if (Debug.ENABLED) {
                    Helper.getInstance().disableEqualsOrHashCheck();
                    Helper.getInstance().getAllowExistingReadsOrWrites().put(transaction, transaction);
                    Helper.getInstance().enableEqualsOrHashCheck();
                }

                transaction.addFlags(Transaction.FLAG_NO_READS | Transaction.FLAG_NO_WRITES | Transaction.FLAG_COMMITTED);
                map.setTransaction(transaction);
                transaction.setVersionMap(map);
            }

            // TODO: find out if publishing is always needed
            for (int i = versions.length - 1; i >= 0; i--) {
                if (versions[i] != null) {
                    versions[i].onDeserialized(snapshot);

                    boolean fixSubsequent = versions[i].onPublishing(newSnapshot, index);

                    if (fixSubsequent)
                        fixSubsequent(versions[i], snapshot, newSnapshot, index);
                }
            }

            if (Debug.ENABLED)
                TKeyedVersion.assertUpdates(newSnapshot.getWrites(), index);

            if (Debug.THREADS) {
                ThreadAssert.removePrivate(map);
                ThreadAssert.addShared(map);

                if (snapshot.getSlowChanging() != null && branch.getGranularity() == Granularity.ALL) {
                    ThreadAssert.removePrivate(transaction);
                    ThreadAssert.addShared(transaction);
                }
            }

            if (!branch.casSharedSnapshot(snapshot, newSnapshot)) {
                if (Debug.THREADS) {
                    if (snapshot.getSlowChanging() != null && branch.getGranularity() == Granularity.ALL) {
                        ThreadAssert.removeShared(transaction);
                        ThreadAssert.addPrivate(transaction);
                    }

                    ThreadAssert.removeShared(map);
                    ThreadAssert.addPrivate(map);
                }

                if (Debug.ENABLED) {
                    retryCount++;

                    if (Stats.ENABLED) {
                        Stats.getInstance().Retried.incrementAndGet();

                        for (;;) {
                            long max = Stats.getInstance().MaxRetried.get();

                            if (retryCount <= max)
                                break;

                            if (Stats.getInstance().MaxRetried.compareAndSet(max, retryCount))
                                break;
                        }
                    }

                    Helper.getInstance().setRetryCount(retryCount);

                    for (int i = 0; i < mapWatchersList.size(); i++)
                        Helper.getInstance().removeWatcher(map, mapWatchersList.get(i), "propagate mapWatchersList");

                    for (int i = 0; i < lastAckWatchersList.size(); i++)
                        Helper.getInstance().removeWatcher(snapshot.getAcknowledged(), lastAckWatchersList.get(i), "propagate ackWatchersList");
                }

                if (lastAckWatchers != 0)
                    snapshot.getAcknowledged().removeWatchers(branch, lastAckWatchers, false, null);

                if (Debug.ENABLED)
                    Debug.assertion((transaction != null) == (snapshot.getSlowChanging() != null && branch.getGranularity() == Granularity.ALL));

                if (transaction != null) {
                    transaction.reset();
                    branch.recycleToPublic(transaction);
                    map.setTransaction(null);
                    transaction = null;
                }
            } else {
                int oldAckWatchers = 1;
                List<Object> oldAckWatchersList = Debug.ENABLED ? new List<Object>() : null;

                if (index < snapshot.getVersionMaps().length) {
                    if (Debug.ENABLED)
                        oldAckWatchersList.add(interception);

                    if (sourceSplitters != null) {
                        VersionMap ack = snapshot.getAcknowledged();
                        VersionMap next = map;
                        Connection.Version a = ack.getSource() != null ? ack.getSource().Connection : null;
                        Connection.Version b = next.getSource() != null ? next.getSource().Connection : null;

                        if (a == b) {
                            oldAckWatchers += sourceSplitters.length;

                            if (Debug.ENABLED)
                                for (SourceSplitter sourceSplitter : sourceSplitters)
                                    oldAckWatchersList.add(sourceSplitter);
                        }
                    }

                    Interceptor.revalidateSpeculative(branch, snapshot.getAcknowledged());
                } else {
                    if (Debug.ENABLED)
                        oldAckWatchersList.add(branch);

                    if (interception instanceof MultiMapInterception) {
                        oldAckWatchers++;

                        if (Debug.ENABLED)
                            oldAckWatchersList.add(interception);
                    }
                }

                if (Debug.ENABLED)
                    for (int i = 0; i < oldAckWatchersList.size(); i++)
                        Helper.getInstance().removeWatcher(snapshot.getAcknowledged(), oldAckWatchersList.get(i), "propagate oldAckWatchersList");

                snapshot.getAcknowledged().removeWatchers(branch, oldAckWatchers, false, null);

                //

                if (snapshot.getSlowChanging() != null) {
                    Walker[] walkers = snapshot.getSlowChanging().getWalkers();

                    if (walkers != null) {
                        if (Debug.ENABLED)
                            Debug.assertion(walkers.length > 0);

                        for (int i = walkers.length - 1; i >= 0; i--)
                            walkers[i].requestRun();
                    }
                }

                break;
            }
        }
    }

    private static void fixSubsequent(Version version, Snapshot snapshot, Snapshot newSnapshot, int insertIndex) {
        for (int i = insertIndex + 1; i < newSnapshot.getWrites().length; i++) {
            int hash = System.identityHashCode(version.getUnion());
            int index = TransactionSets.getIndex(newSnapshot.getWrites()[i], version.getUnion(), hash);

            if (index >= 0) {
                Version subsequent = newSnapshot.getWrites()[i][index];
                Version fixed = subsequent.onPastChanged(newSnapshot, i);

                if (fixed != subsequent) {
                    if (newSnapshot.getWrites()[i] == snapshot.getWrites()[i - 1]) {
                        Version[] temp = new Version[newSnapshot.getWrites()[i].length];
                        PlatformAdapter.arraycopy(newSnapshot.getWrites()[i], 0, temp, 0, newSnapshot.getWrites()[i].length);
                        newSnapshot.getWrites()[i] = temp;
                    }

                    newSnapshot.getWrites()[i][index] = fixed;
                }
            }
        }
    }

    public static final void abort(Transaction transaction) {
        if (Debug.ENABLED)
            transaction.checkInvariants();

        Transaction parent = transaction.getParent();

        if (parent.isPublic()) {
            Version[] imports = transaction.getImports();

            if (imports != null)
                propagate(parent, imports, VersionMap.IMPORTS_SOURCE);

            if (transaction.getVersionMap() != null && transaction.getVersionMap().getInterception() != null)
                throw new IllegalArgumentException("Aborting a distributed transaction is currently unsupported.");

            if (Debug.ENABLED) {
                if (transaction.getVersionMap() != null)
                    Helper.getInstance().removeWatcher(transaction.getVersionMap(), parent, "No more need of this commit as last 2");

                parent.releaseSnapshotDebug(transaction.getSnapshot(), transaction, "Dispose, start");
            }

            parent.releaseSnapshot(transaction.getSnapshot());
            boolean isRemote = transaction.isRemote();
            transaction.reset();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            if (!isRemote)
                parent.recycleToPublic(transaction);
        } else {
            if (Debug.ENABLED)
                if (transaction.getVersionMap() != null)
                    Helper.getInstance().removeWatcher(transaction.getVersionMap(), parent, "No more need of this commit as last 3");

            transaction.reset();

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);

            // Nothing to do
            if (!transaction.isRemote())
                parent.recycleToPrivate(transaction);
        }
    }

    //

    private static final void block(Transaction branch, Snapshot snapshot, Connection.Version connection) {
        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            newSnapshot.setVersionMaps(snapshot.getVersionMaps());
            newSnapshot.setReads(snapshot.getReads());
            newSnapshot.setWrites(snapshot.getWrites());
            newSnapshot.setInterception(snapshot.getInterception());
            newSnapshot.setAcknowledgedIndex(snapshot.getAcknowledgedIndex());

            newSnapshot.setSlowChanging(snapshot.getSlowChanging().block(connection));

            if (branch.casSharedSnapshot(snapshot, newSnapshot)) {
                if (snapshot.getSlowChanging().getWalkers() != null)
                    for (int i = snapshot.getSlowChanging().getWalkers().length - 1; i >= 0; i--)
                        snapshot.getSlowChanging().getWalkers()[i].onBlocked(branch, connection);

                break;
            }

            snapshot = branch.getSharedSnapshot();
        }
    }

    public static final void unblock(Transaction branch, Connection.Version connection) {
        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            Snapshot snapshot = branch.getSharedSnapshot();

            newSnapshot.setVersionMaps(snapshot.getVersionMaps());
            newSnapshot.setReads(snapshot.getReads());
            newSnapshot.setWrites(snapshot.getWrites());
            newSnapshot.setInterception(snapshot.getInterception());
            newSnapshot.setAcknowledgedIndex(snapshot.getAcknowledgedIndex());

            newSnapshot.setSlowChanging(snapshot.getSlowChanging().unblock(connection));

            if (branch.casSharedSnapshot(snapshot, newSnapshot))
                break;
        }
    }
}
