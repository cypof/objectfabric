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

import of4gwt.Transaction.Granularity;
import of4gwt.misc.Debug;
import of4gwt.misc.OverrideAssert;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.AllowSharedRead;
import of4gwt.misc.ThreadAssert.SingleThreaded;

/**
 * Uses two transactions to have two stable memory snapshots. It uses those transactions
 * as boundaries and iterates over commits that occurred between them. The visitor is
 * invoked on each commit. When the the last commit is reached, the walker cancels the
 * oldest transaction and start a new one as the boundary of a new iteration. This way it
 * can process commits in order without need for synchronization with writer threads.
 */
@SingleThreaded
public abstract class Walker extends Extension<Snapshot> {

    @AllowSharedRead
    private final Granularity _forcedGranularity;

    private VersionMap _lastSnapshot, _currentSnapshot;

    public Walker(Granularity forcedGranularity) {
        _forcedGranularity = forcedGranularity;
    }

    final Snapshot getSnapshot(Transaction branch) {
        return get(branch);
    }

    protected final Granularity getGranularity(Transaction branch) {
        if (_forcedGranularity != null)
            return _forcedGranularity;

        return branch.getGranularity();
    }

    /**
     * Called when registering, adds this as watcher on required maps to take snapshots of
     * the branch.
     */
    @Override
    boolean casSnapshotWithThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot) {
        Granularity granularity = getGranularity(branch);

        if (granularity == Granularity.COALESCE) {
            if (snapshot.getAcknowledged().tryToAddWatchers(1)) {
                if (Debug.ENABLED)
                    Helper.getInstance().addWatcher(snapshot.getAcknowledged(), this, "casSnapshotWithThis");
            } else
                return false;
        } else {
            for (int i = snapshot.getVersionMaps().length - 2; i >= snapshot.getAcknowledgedIndex(); i--) {
                if (snapshot.getVersionMaps()[i].tryToAddWatchers(1)) {
                    if (Debug.ENABLED)
                        Helper.getInstance().addWatcher(snapshot.getVersionMaps()[i], this, "casSnapshotWithThis" + i);
                } else {
                    // TODO: write register/unregister multi-threaded loop to test this
                    for (int t = snapshot.getVersionMaps().length - 2; t > i; t--) {
                        if (Debug.ENABLED)
                            Helper.getInstance().removeWatcher(snapshot.getVersionMaps()[t], this, "casSnapshotWithThis" + i);

                        snapshot.getVersionMaps()[t].removeWatchers(branch, 1, false, null);
                    }

                    return false;
                }
            }
        }

        if (super.casSnapshotWithThis(branch, snapshot, newSnapshot)) {
            put(branch, snapshot);
            return true;
        }

        // If failed, remove watchers and return false to retry

        if (granularity == Granularity.COALESCE) {
            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(snapshot.getAcknowledged(), this, "casSnapshotWithThis failed");

            snapshot.getAcknowledged().removeWatchers(branch, 1, false, null);
        } else {
            for (int i = snapshot.getVersionMaps().length - 2; i >= snapshot.getAcknowledgedIndex(); i--) {
                if (Debug.ENABLED)
                    Helper.getInstance().removeWatcher(snapshot.getVersionMaps()[i], this, "casSnapshotWithThis failed" + i);

                snapshot.getVersionMaps()[i].removeWatchers(branch, 1, false, null);
            }
        }

        return false;
    }

    /**
     * Called when unregistering, removes this from watchers on every map where it has
     * been added.
     */
    @Override
    boolean casSnapshotWithoutThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot, Exception exception) {
        if (super.casSnapshotWithoutThis(branch, snapshot, newSnapshot, exception)) {
            Snapshot previous = get(branch);

            if (getGranularity(branch) == Granularity.COALESCE) {
                if (_currentSnapshot != null) {
                    if (Debug.ENABLED)
                        Helper.getInstance().removeWatcher(_currentSnapshot, this, "unregister current");

                    _currentSnapshot.removeWatchers(branch, 1, false, null);
                }

                if (Debug.ENABLED)
                    Helper.getInstance().removeWatcher(previous.getAcknowledged(), this, "unregister previous");

                previous.getAcknowledged().removeWatchers(branch, 1, false, null);
            } else {
                int index = Helper.getIndex(snapshot, _lastSnapshot);

                if (index < 0)
                    index = Helper.getIndex(snapshot, previous.getAcknowledged());

                if (Debug.ENABLED)
                    Debug.assertion(index >= 0);

                for (int i = snapshot.getVersionMaps().length - 2; i >= index; i--) {
                    if (Debug.ENABLED)
                        Helper.getInstance().removeWatcher(snapshot.getVersionMaps()[i], this, "unregister " + i);

                    snapshot.getVersionMaps()[i].removeWatchers(branch, 1, false, null);
                }
            }

            return true;
        }

        return false;
    }

    //

    protected final void walk(Visitor visitor) {
        for (;;) {
            Transaction branch;
            Snapshot snapshot;
            Snapshot previous;

            if (visitor.interrupted()) {
                branch = (Transaction) visitor.resume();
                snapshot = (Snapshot) visitor.resume();
                previous = (Snapshot) visitor.resume();
            } else {
                branch = getNextBranch();

                if (branch == null)
                    return;

                for (;;) {
                    /*
                     * TODO Create snapshot only if a maximum number is not reached, e.g.
                     * 8 for all walkers, so that queue is not too large. Otherwise reuse
                     * the latest snapshot created.
                     */
                    snapshot = branch.getSharedSnapshot();

                    if (getGranularity(branch) == Granularity.ALL)
                        break;

                    VersionMap map = snapshot.getAcknowledged();

                    if (map.tryToAddWatchers(1)) {
                        if (Debug.ENABLED)
                            Helper.getInstance().addWatcher(map, this, "run");

                        _currentSnapshot = map;
                        break;
                    }
                }

                previous = get(getBranchIndex());

                if (Debug.ENABLED)
                    Debug.assertion(snapshot.getAcknowledged() != previous.getAcknowledged());

                int index = Helper.getIndex(snapshot, previous.getAcknowledged());

                if (Debug.ENABLED)
                    Debug.assertion(index != -1);

                visitor.setBranch(branch);
                visitor.setSnapshot(snapshot);
                visitor.setMapIndex1(index + 1);
                visitor.setMapIndex2(snapshot.getAcknowledgedIndex() + 1);
            }

            visitor.visitBranch();

            if (visitor.interrupted()) {
                visitor.interrupt(previous);
                visitor.interrupt(snapshot);
                visitor.interrupt(branch);
                return;
            }

            if (getGranularity(branch) == Granularity.COALESCE) {
                releaseSnapshot(branch, previous, previous.getAcknowledgedIndex());
                _currentSnapshot = null;
            }

            // Do not reuse entry, it might have moved if resized (TODO:?)
            update(branch, snapshot);

            OverrideAssert.add(this);
            onUpToDate(branch);
            OverrideAssert.end(this);

            if (Debug.ENABLED)
                Debug.assertion(!visitor.interrupted());
        }
    }

    protected void releaseSnapshot(Transaction branch, Snapshot snapshot, int mapIndex) {
        if (Debug.ENABLED)
            Helper.getInstance().removeWatcher(snapshot.getVersionMaps()[mapIndex], this, "dispose previous snapshot");

        snapshot.getVersionMaps()[mapIndex].removeWatchers(branch, 1, false, snapshot);
    }

    @Override
    protected boolean isUpToDate(Transaction branch, Snapshot stored) {
        VersionMap previous = stored.getAcknowledged();
        Snapshot snapshot = branch.getSharedSnapshot();
        VersionMap current = snapshot.getAcknowledged();
        return previous == current;
    }

    @Override
    protected Action onVisitingMap(Visitor visitor, int mapIndex) {
        Action action = super.onVisitingMap(visitor, mapIndex);

        if (mapIndex > visitor.getSnapshot().getAcknowledgedIndex()) {
            if (Debug.ENABLED)
                Debug.assertion(mapIndex == visitor.getSnapshot().getAcknowledgedIndex() + 1);

            action = Action.TERMINATE;
        } else {
            if (Debug.ENABLED) {
                if (getGranularity(visitor.getBranch()) == Granularity.ALL) {
                    VersionMap map = visitor.getSnapshot().getVersionMaps()[mapIndex];

                    Debug.assertion(map.isNotMerging());
                    Debug.assertion(map.getTransaction() != null);
                    Debug.assertion(map.getTransaction().getParent() == visitor.getBranch());

                    if (Debug.THREADS)
                        ThreadAssert.assertShared(map);
                }
            }
        }

        return action;
    }

    @Override
    protected void onVisitedMap(Visitor visitor, int mapIndex) {
        super.onVisitedMap(visitor, mapIndex);

        Granularity granularity = getGranularity(visitor.getBranch());

        if (granularity == Granularity.ALL) {
            visitor.flush();

            if (!visitor.interrupted()) {
                // Done with map, let it merge
                releaseSnapshot(visitor.getBranch(), visitor.getSnapshot(), mapIndex - 1);
                _lastSnapshot = visitor.getSnapshot().getVersionMaps()[mapIndex];
            }
        }
    }

    protected boolean flushOnSourceChangeAndReturnIfDelayMerge(Visitor visitor) {
        visitor.flush();
        return false;
    }

    @Override
    protected void onVisitedBranch(Visitor visitor) {
        super.onVisitedBranch(visitor);

        if (getGranularity(visitor.getBranch()) != Granularity.ALL)
            visitor.flush();
    }

    /**
     * @param branch
     * @param connection
     */
    void onBlocked(Transaction branch, Connection.Version connection) {
    }
}
