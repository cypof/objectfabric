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

import com.objectfabric.Interception.MultiMapInterception;
import com.objectfabric.Interception.SingleMapInterception;
import com.objectfabric.Snapshot.SlowChanging;
import com.objectfabric.TObject.Version;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.WritableFuture;

final class Interceptor {

    private Interceptor() {
    }

    public static final void intercept(Transaction branch) {
        for (;;) {
            Snapshot snapshot = branch.getSharedSnapshot();
            Interception interception = snapshot.getInterception();
            SlowChanging slow = snapshot.getSlowChanging();

            if (slow != null && slow.getBlocked() == SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED)
                slow = new SlowChanging(slow, null);

            if (interception == null) {
                if (branch.getGranularity() == Granularity.COALESCE) {
                    if (tryToAddMultiMapInterception(branch, snapshot, slow, null))
                        break;
                } else {
                    if (tryToAddSingleMapInterception(branch, snapshot, slow))
                        break;
                }
            } else if (slow != snapshot.getSlowChanging()) {
                if (tryToUnblock(branch, snapshot, slow))
                    break;
            } else
                break;
        }
    }

    public static final void reset(Transaction branch) {
        if (!Debug.TESTING)
            throw new RuntimeException();

        for (;;) {
            Snapshot snapshot = branch.getSharedSnapshot();
            SlowChanging slow = snapshot.getSlowChanging();

            if (slow != null && slow.getBlocked() == SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED)
                slow = new SlowChanging(slow, null);

            Snapshot newSnapshot = copySnapshot(snapshot);

            newSnapshot.setSlowChanging(slow);
            newSnapshot.setInterception(null);

            if (branch.casSharedSnapshot(snapshot, newSnapshot)) {
                Debug.assertAlways(snapshot.getVersionMaps().length == 1);

                if (branch.getGranularity() == Granularity.COALESCE) {
                    snapshot.getLast().removeWatchers(branch, 1, false, null);

                    if (Debug.ENABLED)
                        Helper.getInstance().removeWatcher(snapshot.getLast(), snapshot.getInterception(), "Interceptor.reset");
                }

                return;
            }
        }
    }

    private static final boolean tryToAddSingleMapInterception(Transaction branch, Snapshot snapshot, SlowChanging slow) {
        WritableFuture<CommitStatus> async = PlatformAdapter.createCommitStatusFuture();
        SingleMapInterception interception = new SingleMapInterception(async);

        Snapshot newSnapshot = copySnapshot(snapshot);

        newSnapshot.setSlowChanging(slow);
        newSnapshot.setInterception(interception);

        if (branch.casSharedSnapshot(snapshot, newSnapshot))
            return true;

        return false;
    }

    public static final boolean tryToAddMultiMapInterception(Transaction branch, Snapshot snapshot, SlowChanging slow, Visitor visitor) {
        if (snapshot.getLast().tryToAddWatchers(1)) {
            MultiMapInterception interception = new MultiMapInterception();

            if (Debug.ENABLED) {
                Helper.getInstance().addWatcher(snapshot.getLast(), interception, "tryToAddInterception");
                Debug.assertion(slow == null || slow.getBlocked() != SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED);
            }

            if (snapshot.getInterception() != null)
                interception.setId((byte) (snapshot.getInterception().getId() + 1));

            Snapshot newSnapshot = copySnapshot(snapshot);

            newSnapshot.setSlowChanging(slow);
            newSnapshot.setInterception(interception);

            if (branch.casSharedSnapshot(snapshot, newSnapshot)) {
                if (visitor != null)
                    visitor.setSnapshot(newSnapshot);

                return true;
            }

            snapshot.getLast().removeWatchers(branch, 1, false, snapshot);

            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(snapshot.getLast(), interception, "tryToAddInterception");
        }

        return false;
    }

    private static final boolean tryToUnblock(Transaction branch, Snapshot snapshot, SlowChanging slow) {
        Snapshot newSnapshot = copySnapshot(snapshot);
        newSnapshot.setInterception(snapshot.getInterception());

        newSnapshot.setSlowChanging(slow);

        if (branch.casSharedSnapshot(snapshot, newSnapshot))
            return true;

        return false;
    }

    private static Snapshot copySnapshot(Snapshot snapshot) {
        Snapshot newSnapshot = new Snapshot();
        newSnapshot.setVersionMaps(snapshot.getVersionMaps());
        newSnapshot.setReads(snapshot.getReads());
        newSnapshot.setWrites(snapshot.getWrites());
        newSnapshot.setAcknowledgedIndex(snapshot.getAcknowledgedIndex());
        // Misses Interception & SlowChanging
        return newSnapshot;
    }

    //

    public static final void ack(Transaction branch, byte interceptionId, boolean assertIsActualAck) {
        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            Snapshot snapshot;
            int newAcknowledged;
            MultiMapInterception newInterception = null;

            for (;;) {
                snapshot = branch.getSharedSnapshot();
                newAcknowledged = 0;

                for (int i = snapshot.getVersionMaps().length - 1; i > snapshot.getAcknowledgedIndex(); i--) {
                    if (snapshot.getVersionMaps()[i].getInterception().getId() == interceptionId) {
                        newAcknowledged = i;
                        break;
                    }
                }

                // Remove if allow multiple acknowledgers
                if (Debug.ENABLED && assertIsActualAck)
                    Debug.assertion(newAcknowledged > snapshot.getAcknowledgedIndex());

                // Might already be further away if multiple acknowledgers
                if (newAcknowledged <= snapshot.getAcknowledgedIndex())
                    return;

                // Protected by future single map interruption
                if (snapshot.getInterception() instanceof SingleMapInterception) {
                    newSnapshot.setInterception(snapshot.getInterception());
                    break;
                }

                // Already protected by a new interruption
                if (newAcknowledged < snapshot.getVersionMaps().length - 1) {
                    newSnapshot.setInterception(snapshot.getInterception());
                    break;
                }

                // Last interruption has not been used my any commit, keep
                if (snapshot.getLast().getInterception() != snapshot.getInterception()) {
                    newSnapshot.setInterception(snapshot.getInterception());
                    break;
                }

                // Otherwise, change interruption
                if (snapshot.getLast().tryToAddWatchers(1)) {
                    newInterception = new MultiMapInterception();

                    if (Debug.ENABLED)
                        Helper.getInstance().addWatcher(snapshot.getLast(), newInterception, "new interception");

                    newSnapshot.setInterception(newInterception);
                    break;
                }
            }

            newSnapshot.setVersionMaps(snapshot.getVersionMaps());
            newSnapshot.setReads(snapshot.getReads());
            newSnapshot.setWrites(snapshot.getWrites());
            newSnapshot.setSlowChanging(snapshot.getSlowChanging());
            //
            newSnapshot.setAcknowledgedIndex(newAcknowledged);

            if (!branch.casSharedSnapshot(snapshot, newSnapshot)) {
                if (newInterception != null) {
                    if (Debug.ENABLED)
                        Helper.getInstance().removeWatcher(snapshot.getLast(), newInterception, "Acknowledger ack retry");

                    snapshot.getLast().removeWatchers(branch, 1, false, snapshot);
                }
            } else {
                for (int i = snapshot.getAcknowledgedIndex() + 1; i <= newSnapshot.getAcknowledgedIndex(); i++) {
                    VersionMap previous = snapshot.getVersionMaps()[i - 1];
                    VersionMap map = snapshot.getVersionMaps()[i];

                    if (Debug.ENABLED) {
                        Debug.assertion(map.getInterception() != null);

                        if (i == snapshot.getAcknowledgedIndex() + 1)
                            Debug.assertion(previous.getInterception() != map.getInterception());
                    }

                    if (previous.getInterception() != map.getInterception()) {
                        if (Debug.ENABLED)
                            Helper.getInstance().removeWatcher(previous, map.getInterception(), "Acknowledger ack");

                        previous.removeWatchers(branch, 1, false, snapshot);

                        if (Debug.ENABLED) {
                            boolean done = map.getInterception().getAsync().isDone();
                            Debug.assertion(!done);
                        }

                        map.getInterception().getAsync().set(CommitStatus.SUCCESS);

                        if (map.getInterception() instanceof MultiMapInterception)
                            ((MultiMapInterception) map.getInterception()).setCallbacks(CommitStatus.SUCCESS, null);
                    }
                }

                // Notify extensions waiting for acknowledgment

                if (snapshot.getSlowChanging() != null) {
                    Walker[] walkers = snapshot.getSlowChanging().getWalkers();

                    if (walkers != null) {
                        if (Debug.ENABLED)
                            Debug.assertion(walkers.length > 0);

                        for (int i = walkers.length - 1; i >= 0; i--)
                            walkers[i].requestRun();
                    }

                    Acknowledger[] acknowledgers = snapshot.getSlowChanging().getAcknowledgers();

                    if (acknowledgers != null) {
                        if (Debug.ENABLED)
                            Debug.assertion(acknowledgers.length > 0);

                        for (int i = acknowledgers.length - 1; i >= 0; i--)
                            acknowledgers[i].requestRun();
                    }
                }

                break;
            }
        }
    }

    public static final void nack(Transaction branch, CommitStatus result, Throwable throwableOrNullForConflict) {
        for (;;) {
            Snapshot snapshot = branch.getSharedSnapshot();
            int length = snapshot.getAcknowledgedIndex() + 1;

            if (trim(branch, snapshot, length, result, throwableOrNullForConflict))
                break;
        }
    }

    public static final void revalidateSpeculative(Transaction branch, VersionMap lastValidated) {
        for (;;) {
            Snapshot snapshot = branch.getSharedSnapshot();
            int lastValidatedIndex = Helper.getIndex(snapshot, lastValidated);

            if (Debug.ENABLED) {
                Debug.assertion(lastValidatedIndex >= 0);
                Debug.assertion(lastValidatedIndex < snapshot.getAcknowledgedIndex());
            }

            int length = snapshot.getVersionMaps().length;

            if (snapshot.getReads() != null) {
                for (length = snapshot.getAcknowledgedIndex() + 1; length < snapshot.getVersionMaps().length; length++) {
                    VersionMap map = snapshot.getVersionMaps()[length];
                    Version[] reads = snapshot.getReads()[length];
                    int start = lastValidatedIndex + 1;
                    int stop = snapshot.getAcknowledgedIndex() + 1;

                    if (!Helper.validate(map, reads, snapshot, start, stop))
                        break;
                }
            }

            if (trim(branch, snapshot, length, CommitStatus.CONFLICT, null))
                break;
        }
    }

    private static final boolean trim(Transaction branch, Snapshot snapshot, int length, CommitStatus result, Throwable throwableOrNullForConflict) {
        if (length == snapshot.getVersionMaps().length)
            return true;

        VersionMap newLast = snapshot.getVersionMaps()[length - 1];

        if (newLast.tryToAddWatchers(1)) {
            if (Debug.ENABLED)
                Helper.getInstance().addWatcher(newLast, branch, "Acknowledger nack");

            boolean multi = snapshot.getInterception() instanceof MultiMapInterception;
            Interception newInterception;

            if (multi)
                newInterception = new MultiMapInterception();
            else {
                WritableFuture<CommitStatus> async = PlatformAdapter.createCommitStatusFuture();
                newInterception = new SingleMapInterception(async);
            }

            byte id = snapshot.getVersionMaps()[length].getInterception().getId();

            // Restart ids with first failed so series no broken
            newInterception.setId(id);

            /*
             * Block reader so that there will be no new commit until clients acknowledge
             * the abort and restart.
             */
            List<Connection.Version> blocked = null;

            for (int i = length; i < snapshot.getVersionMaps().length; i++) {
                VersionMap map = snapshot.getVersionMaps()[i];

                if (map.getSource() != null && map.getSource() != VersionMap.IMPORTS_SOURCE) {
                    Connection.Version connection = map.getSource().Connection;

                    if (blocked == null)
                        blocked = new List<Connection.Version>();

                    if (!blocked.contains(connection))
                        blocked.add(connection);
                }
            }

            Snapshot newSnapshot = new Snapshot();
            SlowChanging newSlowChanging;

            if (blocked != null)
                newSlowChanging = snapshot.getSlowChanging().block(blocked);
            else
                newSlowChanging = snapshot.getSlowChanging();

            snapshot.trimWithoutReads(newSnapshot, newInterception, newSlowChanging, length);

            if (snapshot.getReads() != null) {
                for (int i = snapshot.getAcknowledgedIndex() + 1; i < length; i++) {
                    if (snapshot.getReads()[i] != null) {
                        Version[][] reads = new Version[length][];
                        PlatformAdapter.arraycopy(snapshot.getReads(), 0, reads, 0, reads.length);
                        newSnapshot.setReads(reads);
                        break;
                    }
                }
            }

            if (Debug.ENABLED) {
                // Switch interception, old is removed after CAS
                if (multi)
                    Helper.getInstance().addWatcher(newSnapshot.getLast(), newInterception, "Acknowledger nack add newInterception");
            }

            if (!branch.casSharedSnapshot(snapshot, newSnapshot)) {
                if (Debug.ENABLED)
                    if (multi)
                        Helper.getInstance().removeWatcher(newSnapshot.getLast(), newInterception, "Acknowledger nack retry");
            } else {
                if (snapshot.getSlowChanging() != null) {
                    Walker[] walkers = snapshot.getSlowChanging().getWalkers();

                    if (blocked != null && walkers != null) {
                        if (Debug.ENABLED)
                            Debug.assertion(blocked.size() > 0 && walkers.length > 0);

                        for (int i = walkers.length - 1; i >= 0; i--)
                            for (int j = blocked.size(); j >= 0; j--)
                                walkers[i].onBlocked(branch, blocked.get(j));
                    }

                    Acknowledger[] acknowledgers = snapshot.getSlowChanging().getAcknowledgers();

                    if (acknowledgers != null) {
                        if (Debug.ENABLED)
                            Debug.assertion(acknowledgers.length > 0);

                        for (int i = acknowledgers.length - 1; i >= 0; i--)
                            acknowledgers[i].requestRun();
                    }
                }

                if (!multi)
                    if (newLast.getInterception() != snapshot.getVersionMaps()[length].getInterception())
                        newLast.removeWatchers(branch, 1, false, null);

                if (snapshot.getSlowChanging().getNoMergeWalkers() != null) {
                    int count = snapshot.getSlowChanging().getNoMergeWalkers().length;
                    newLast.removeWatchers(branch, count, false, null);
                }

                for (int i = length; i < snapshot.getVersionMaps().length; i++) {
                    VersionMap previous = snapshot.getVersionMaps()[i - 1];
                    VersionMap map = snapshot.getVersionMaps()[i];

                    if (Debug.ENABLED)
                        Debug.assertion(map.getInterception() != null);

                    if (previous.getInterception() != map.getInterception()) {
                        if (Debug.ENABLED) {
                            Helper.getInstance().removeWatcher(previous, map.getInterception(), "Acknowledger nack debug only");
                            boolean done = map.getInterception().getAsync().isDone();
                            Debug.assertion(!done);
                        }

                        if (throwableOrNullForConflict == null)
                            map.getInterception().getAsync().set(result);
                        else
                            map.getInterception().getAsync().setException(throwableOrNullForConflict);

                        if (multi)
                            ((MultiMapInterception) map.getInterception()).setCallbacks(result, throwableOrNullForConflict);
                    }

                    if (Debug.ENABLED)
                        if (snapshot.getSlowChanging().getNoMergeWalkers() != null)
                            for (Walker walker : snapshot.getSlowChanging().getNoMergeWalkers())
                                Helper.getInstance().removeWatcher(previous, walker, "Acknowledger nack walker");

                    map.onAborted();
                }

                if (Debug.ENABLED) {
                    Helper.getInstance().removeWatcher(snapshot.getLast(), branch, "Acknowledger nack old last");

                    if (multi)
                        if (snapshot.getLast().getInterception() != snapshot.getInterception())
                            Helper.getInstance().removeWatcher(snapshot.getLast(), snapshot.getInterception(), "Acknowledger nack old last interception");
                }

                if (Debug.THREADS)
                    for (int i = length; i < snapshot.getVersionMaps().length; i++)
                        ThreadAssert.removeShared(snapshot.getVersionMaps()[i]);

                return true;
            }

            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(newLast, branch, "Acknowledger nack retry");

            newLast.removeWatchers(branch, 1, false, null);
        }

        return false;
    }
}
