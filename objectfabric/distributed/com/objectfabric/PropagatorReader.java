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

import com.objectfabric.Connection.Endpoint;
import com.objectfabric.Extension.TObjectMapEntry;
import com.objectfabric.Snapshot.SlowChanging;
import com.objectfabric.Transaction.ConflictDetection;
import com.objectfabric.VersionMap.Source;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.SparseArrayHelper;

//No @SingleThreaded as all fields are shared 
final class PropagatorReader extends DistributedReader {

    /**
     * Keeps track of the snapshot up to which the client has already validated
     * transactions when they arrive on the server.
     */
    private static final class ValidationPoint {

        /**
         * This snapshot has been acknowledged by the client.
         */
        public VersionMap Client;

        /**
         * This one is pending an acknowledgment.
         */
        public VersionMap Pending;

        /**
         * This is the last snapshot propagated to the client.
         */
        public VersionMap Last;

        /**
         * Used to keep track of which snapshots were saved from disposal after walker was
         * done with them so that they could be used for validation.
         */
        public boolean ClientKept, PendingKept, LastKept;
    }

    private final PropagatorWriter _writer;

    private final SourceSplitter _sourceSplitter = new SourceSplitter();

    @SuppressWarnings("unchecked")
    private TObjectMapEntry<ValidationPoint>[] _points = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

    private static final Transaction[] READ_THREAD_DONE = new Transaction[0];

    private Transaction[] _branchesToUnblock;

    private final Object _lock = new Object();

    public PropagatorReader(Endpoint endpoint, PropagatorWriter writer) {
        super(endpoint);

        _writer = writer;
    }

    public void onRegistered(Transaction branch, Snapshot snapshot) {
        if (Debug.ENABLED)
            Debug.assertion(branch.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS);

        ValidationPoint point = new ValidationPoint();
        point.Client = snapshot.getAcknowledged();

        if (Debug.COMMUNICATIONS_LOG)
            Log.write("onRegistered point.Client: " + point.Client);

        TObjectMapEntry<ValidationPoint> entry = new TObjectMapEntry<ValidationPoint>(branch, point);

        synchronized (_lock) {
            _points = TObjectMapEntry.put(_points, entry);

            if (Debug.ENABLED)
                checkInvariants();
        }
    }

    public void onUnregistered(Transaction branch, Snapshot snapshot) {
        if (Debug.ENABLED)
            Debug.assertion(branch.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS);

        TObjectMapEntry<ValidationPoint> entry;

        synchronized (_lock) {
            entry = TObjectMapEntry.remove(_points, branch);

            if (Debug.ENABLED)
                checkInvariants();
        }

        ValidationPoint point = entry.getValue();

        if (point.ClientKept) {
            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(point.Client, _writer.getWalker(), "PropagatorReader.onUnregistered Client");

            point.Client.removeWatchers(branch, 1, false, null);
        }

        if (point.PendingKept) {
            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(point.Pending, _writer.getWalker(), "PropagatorReader.onUnregistered Pending");

            point.Pending.removeWatchers(branch, 1, false, null);
        }

        if (point.LastKept) {
            if (Debug.ENABLED)
                Helper.getInstance().removeWatcher(point.Last, _writer.getWalker(), "PropagatorReader.onUnregistered Last");

            point.Last.removeWatchers(branch, 1, false, null);
        }

        Connection.Version[] blocked = snapshot.getSlowChanging().getBlocked();

        if (blocked != null && blocked != SlowChanging.BLOCKED_AS_BRANCH_DISCONNECTED) {
            Connection.Version connection = (Connection.Version) getEndpoint().getConnection().getSharedVersion_objectfabric();

            if (snapshot.getSlowChanging().isBlocked(connection))
                TransactionManager.unblock(branch, connection);
        }
    }

    public void ensureSourcesSplitted(Transaction branch) {
        if (!_sourceSplitter.registered(branch)) {
            if (Debug.COMMUNICATIONS_LOG)
                Log.write("PropagatorWriter: sources splitted for branch " + branch);

            _sourceSplitter.register(branch);
        }
    }

    @Override
    protected void onStopped(Exception e) {
        super.onStopped(e);

        _sourceSplitter.unregisterFromAllBranches(e);

        synchronized (_lock) {
            if (_branchesToUnblock == null)
                _branchesToUnblock = READ_THREAD_DONE;
            else
                unblockAllBranches();
        }
    }

    final void onWriteThreadStopped(Transaction[] branches) {
        synchronized (_lock) {
            if (_branchesToUnblock == READ_THREAD_DONE)
                unblockAllBranches();
            else
                _branchesToUnblock = branches;
        }
    }

    private final void unblockAllBranches() {
        for (int i = 0; i < _branchesToUnblock.length; i++) {
            Transaction branch = _branchesToUnblock[i];

            if (branch != null) { // Might have been GCed before copy
                SlowChanging data = branch.getSharedSnapshot().getSlowChanging();
                Connection.Version connection = (Connection.Version) getEndpoint().getConnection().getSharedVersion_objectfabric();

                if (data.isBlocked(connection))
                    data.unblock(connection);
            }
        }
    }

    public void onPropagationEnded(Transaction branch, Snapshot snapshot, boolean transmitted) {
        if (Debug.ENABLED)
            Debug.assertion(branch.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS);

        boolean notifyPendingSnapshot = false;
        VersionMap map = snapshot.getAcknowledged();

        if (!_writer.interrupted()) {
            VersionMap acknowledgedToDispose = null, pendingToDispose = null, lastToDispose = null;

            synchronized (_lock) {
                ValidationPoint point = TObjectMapEntry.get(_points, branch);

                if (Debug.ENABLED)
                    Debug.assertion(map != point.Client && map != point.Pending && map != point.Last);

                if (point.Last != null) {
                    if (point.LastKept) {
                        lastToDispose = point.Last;
                        point.LastKept = false;
                    }

                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write("onPropagationEnded, point.Last (1): " + map);

                    point.Last = map;
                } else if (point.Pending != null) {
                    if (Debug.ENABLED)
                        Debug.assertion(!point.LastKept);

                    if (transmitted) {
                        if (Debug.COMMUNICATIONS_LOG)
                            Log.write("onPropagationEnded, point.Last (2): " + map);

                        point.Last = map;
                    } else {
                        if (point.PendingKept) {
                            pendingToDispose = point.Pending;
                            point.PendingKept = false;
                        }

                        if (Debug.COMMUNICATIONS_LOG)
                            Log.write("onPropagationEnded, point.Pending (1): " + map);

                        point.Pending = map;
                    }
                } else {
                    if (Debug.ENABLED)
                        Debug.assertion(!point.LastKept && !point.PendingKept);

                    if (transmitted) {
                        if (Debug.COMMUNICATIONS_LOG)
                            Log.write("onPropagationEnded, point.Pending (2): " + map);

                        point.Pending = map;
                        notifyPendingSnapshot = true;
                    } else {
                        if (point.ClientKept) {
                            acknowledgedToDispose = point.Client;
                            point.ClientKept = false;
                        }

                        if (Debug.COMMUNICATIONS_LOG)
                            Log.write("onPropagationEnded, point.Client: " + map);

                        point.Client = map;
                    }
                }

                if (Debug.ENABLED)
                    checkInvariants();
            }

            if (acknowledgedToDispose != null) {
                if (Debug.ENABLED)
                    Helper.getInstance().removeWatcher(acknowledgedToDispose, _writer.getWalker(), "acknowledgedToDispose");

                acknowledgedToDispose.removeWatchers(branch, 1, false, null);
            }

            if (pendingToDispose != null) {
                if (Debug.ENABLED)
                    Helper.getInstance().removeWatcher(pendingToDispose, _writer.getWalker(), "pendingToDispose");

                pendingToDispose.removeWatchers(branch, 1, false, null);
            }

            if (lastToDispose != null) {
                if (Debug.ENABLED)
                    Helper.getInstance().removeWatcher(lastToDispose, _writer.getWalker(), "lastToDispose");

                lastToDispose.removeWatchers(branch, 1, false, null);
            }
        } else
            notifyPendingSnapshot = true;

        if (notifyPendingSnapshot) {
            if (Debug.ENABLED)
                Debug.assertion(_writer.getRemoteBranch() == branch);

            _writer.writeCommand(PropagatorWriter.COMMAND_NEXT_INTERCEPTION_SNAPSHOT);
        }
    }

    public boolean onDisposingReturnIfKeep(Transaction branch, Snapshot snapshot, int mapIndex) {
        if (Debug.ENABLED)
            Debug.assertion(branch.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS);

        synchronized (_lock) {
            ValidationPoint point = TObjectMapEntry.get(_points, branch);
            VersionMap map = snapshot.getVersionMaps()[mapIndex];

            if (point != null) {
                if (map == point.Client) {
                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write("Keep point.Client: " + map);

                    point.ClientKept = true;
                    return true;
                }

                if (map == point.Pending) {
                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write("Keep point.Pending: " + map);

                    point.PendingKept = true;
                    return true;
                }

                if (map == point.Last) {
                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write("Keep point.Last: " + map);

                    point.LastKept = true;
                    return true;
                }
            }

            return false;
        }
    }

    private void startTransactionAtInterception(Transaction transaction) {
        Transaction branch = transaction.getParent();
        Snapshot snapshot;

        if (branch.getConflictDetection() == ConflictDetection.LAST_WRITE_WINS) {
            snapshot = branch.takeSnapshot();

            if (Debug.ENABLED)
                branch.takeSnapshotDebug(snapshot, transaction, "PropagatorReader.startTransactionAtInterception");
        } else {
            synchronized (_lock) {
                snapshot = branch.getSharedSnapshot();
                ValidationPoint point = TObjectMapEntry.get(_points, branch);
                int trim;

                if (point != null) {
                    trim = Helper.getIndex(snapshot, point.Client);

                    if (Debug.ENABLED)
                        Debug.assertion(trim >= 0);
                } else
                    trim = snapshot.getAcknowledgedIndex();

                snapshot = TransactionPublic.trim(snapshot, trim);
                boolean result = snapshot.getLast().tryToAddWatchers(1);

                if (Debug.ENABLED) {
                    if (point != null) {
                        Debug.assertion(snapshot.getLast() == point.Client);
                        Debug.assertion(snapshot.getAcknowledged() == point.Client);
                    }

                    Debug.assertion(result);
                    Helper.getInstance().addWatcher(snapshot.getAcknowledged(), transaction, "startTransactionAtInterception 2");
                }
            }
        }

        branch.startFromPublic(transaction, snapshot);
    }

    //

    public void onBlocked(final Transaction branch) {
        final PropagatorWriter writer = _writer;

        getEndpoint().enqueueOnWriterThread(new Runnable() {

            public void run() {
                writer.runWhenBranchesAreUpToDate(new Runnable() {

                    public void run() {
                        writer.writeCommandInBranch(branch, PropagatorWriter.COMMAND_NACK_INTERCEPTION);
                    }
                });
            }
        });
    }

    //

    private enum Steps {
        CODE, COMMAND
    }

    @SuppressWarnings("fallthrough")
    @Override
    public byte read() {
        for (;;) {
            Steps step = Steps.CODE;
            byte code = 0;

            if (interrupted()) {
                step = (Steps) resume();
                code = resumeByte();
            }

            switch (step) {
                case CODE: {
                    code = readCode();

                    if (interrupted()) {
                        interruptByte(code);
                        interrupt(Steps.CODE);
                        return 0;
                    }

                    if (Debug.ENABLED)
                        Debug.assertion((code & TObjectWriter.FLAG_TOBJECT) == 0);

                    if ((code & TObjectWriter.FLAG_EOF) != 0)
                        return code;

                    if (Debug.ENABLED)
                        Debug.assertion((code & TObjectWriter.FLAG_IMMUTABLE) == 0);
                }
                case COMMAND: {
                    switch (code) {
                        case InterceptorWriter.COMMAND_IMPORTS: {
                            validateWrites(getBranch(), null);
                            getEndpoint().registerNewObjects();
                            propagateStandalone();
                            break;
                        }
                        case InterceptorWriter.COMMAND_COMMIT: {
                            if (!canReadByte()) {
                                interruptByte(code);
                                interrupt(Steps.COMMAND);
                                return 0;
                            }

                            validateWrites(getBranch(), null);
                            getEndpoint().registerNewObjects();
                            propagateStandalone();

                            byte interceptionId = readByte();
                            Transaction transaction = getBranch().getOrCreateChild();

                            startTransactionAtInterception(transaction);
                            transaction.setReads(takeReads());

                            if (Debug.ENABLED)
                                if (transaction.getReads() != null)
                                    Debug.assertion(getBranch().getConflictDetection() == ConflictDetection.READ_WRITE);

                            transaction.setWrites(takeWrites());
                            Source source = new Source((Connection.Version) getEndpoint().getConnection().getSharedVersion_objectfabric(), interceptionId, false);
                            transaction.getOrCreateVersionMap().setSource(source);
                            ensureSourcesSplitted(getBranch());
                            TransactionManager.commit(transaction, null);

                            if (Debug.ENABLED)
                                assertIdle();

                            break;
                        }
                        case InterceptorWriter.COMMAND_UNBLOCK: {
                            TransactionManager.unblock(getBranch(), (Connection.Version) getEndpoint().getConnection().getSharedVersion_objectfabric());
                            break;
                        }
                        case InterceptorWriter.COMMAND_NEXT_SNAPSHOT_ACK: {
                            final Transaction branch = getBranch();

                            if (Debug.ENABLED)
                                Debug.assertion(branch.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS);

                            VersionMap toDispose = null;
                            boolean notifyPending = false;

                            synchronized (_lock) {
                                ValidationPoint point = TObjectMapEntry.get(_points, branch);

                                if (Debug.ENABLED)
                                    Debug.assertion(point.Pending != null);

                                if (point.ClientKept)
                                    toDispose = point.Client;

                                if (Debug.COMMUNICATIONS_LOG)
                                    Log.write("read NEXT_SNAPSHOT_ACK, point.Client = point.Pending: " + point.Pending);

                                point.Client = point.Pending;
                                point.ClientKept = point.PendingKept;

                                if (Debug.COMMUNICATIONS_LOG)
                                    Log.write("read NEXT_SNAPSHOT_ACK, point.Pending = point.Last: " + point.Last);

                                point.Pending = point.Last;
                                point.PendingKept = point.LastKept;

                                if (point.Last != null) {
                                    notifyPending = true;
                                    point.Last = null;
                                    point.LastKept = false;
                                }

                                if (Debug.ENABLED)
                                    checkInvariants();
                            }

                            if (toDispose != null) {
                                Snapshot snapshot = branch.getSharedSnapshot();
                                int mapIndex = Helper.getIndex(snapshot, toDispose);
                                _sourceSplitter.transferWatch(branch, snapshot, mapIndex, _writer.getWalker());
                            }

                            if (notifyPending) {
                                getEndpoint().enqueueOnWriterThread(new Runnable() {

                                    public void run() {
                                        _writer.runWhenBranchesAreUpToDate(new Runnable() {

                                            public void run() {
                                                _writer.writeCommandInBranch(branch, PropagatorWriter.COMMAND_NEXT_INTERCEPTION_SNAPSHOT);
                                            }
                                        });
                                    }
                                });
                            }

                            break;
                        }
                        default:
                            throw new IllegalStateException();
                    }
                }
            }
        }
    }

    // Debug

    private final void checkInvariants() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        PlatformThread.assertHoldsLock(_lock);

        for (TObjectMapEntry<ValidationPoint> entry : _points) {
            if (entry != null && entry != TObjectMapEntry.REMOVED) {
                Debug.assertion(entry.getValue().Client != entry.getValue().Pending);
                Debug.assertion(entry.getValue().Client != entry.getValue().Last);
                Debug.assertion(entry.getValue().Pending == null || entry.getValue().Pending != entry.getValue().Last);
            }
        }
    }

    @Override
    protected String getCommandString(byte command) {
        String value = InterceptorWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
