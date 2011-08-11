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


import com.objectfabric.Acknowledger;
import com.objectfabric.Interception;
import com.objectfabric.Snapshot;
import com.objectfabric.TObject;
import com.objectfabric.Transaction;
import com.objectfabric.TransactionManager;
import com.objectfabric.VersionMap;
import com.objectfabric.Visitor;
import com.objectfabric.Connection.Endpoint;
import com.objectfabric.Connection.Endpoint.Status;
import com.objectfabric.TObject.Version;
import com.objectfabric.Transaction.ConflictDetection;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.Queue;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class InterceptorWriter extends DistributedWriter {

    public static final byte COMMAND_IMPORTS = DistributedWriter.MAX_COMMAND_ID + 1;

    public static final byte COMMAND_COMMIT = DistributedWriter.MAX_COMMAND_ID + 2;

    public static final byte COMMAND_UNBLOCK = DistributedWriter.MAX_COMMAND_ID + 3;

    public static final byte COMMAND_NEXT_SNAPSHOT_ACK = DistributedWriter.MAX_COMMAND_ID + 4;

    private final InterceptionAcknowledger _acknowledger = new InterceptionAcknowledger();

    private final Queue<Transaction> _newPendingSnapshotsBranches = new Queue<Transaction>();

    private final List<Transaction> _snapshottedBranches = new List<Transaction>();

    public InterceptorWriter(Endpoint endpoint) {
        super(endpoint);

        init(_acknowledger, false);
    }

    public Acknowledger getAcknowledger() {
        return _acknowledger;
    }

    public Queue<Transaction> getNewPendingSnapshotsBranches() {
        return _newPendingSnapshotsBranches;
    }

    public void ensureIntercepted(Transaction branch) {
        if (!_acknowledger.registered(branch)) {
            if (Debug.COMMUNICATIONS_LOG)
                Log.write("InterceptorWriter: registering branch " + branch);

            getEndpoint().register(_acknowledger, branch);
        }
    }

    @Override
    protected void onStopped(Throwable t) {
        _acknowledger.unregisterFromAllBranches(t);

        super.onStopped(t);
    }

    private enum WriteStep {
        PARENT, ACKNOWLEDGER, OTHER_WRITERS_IMMUTABLES
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void write() {
        WriteStep step = WriteStep.PARENT;

        if (interrupted())
            step = (WriteStep) resume();

        switch (step) {
            case PARENT: {
                super.write();

                if (interrupted()) {
                    interrupt(WriteStep.PARENT);
                    return;
                }
            }
            case ACKNOWLEDGER: {
                _acknowledger.run(this);

                if (interrupted()) {
                    interrupt(WriteStep.ACKNOWLEDGER);
                    return;
                }
            }
            case OTHER_WRITERS_IMMUTABLES: {
                writeImports();

                if (interrupted()) {
                    interrupt(WriteStep.OTHER_WRITERS_IMMUTABLES);
                    return;
                }
            }
        }
    }

    private enum WriteImportsSteps {
        IMMUTABLES, COMMAND
    }

    @SuppressWarnings("fallthrough")
    private void writeImports() {
        WriteImportsSteps step = WriteImportsSteps.IMMUTABLES;

        if (interrupted())
            step = (WriteImportsSteps) resume();
        else
            resetWroteCommand();

        switch (step) {
            case IMMUTABLES: {
                writePendingImmutables();

                if (interrupted()) {
                    interrupt(WriteImportsSteps.IMMUTABLES);
                    return;
                }
            }
            case COMMAND: {
                if (getWroteCommand()) {
                    writeCommand(COMMAND_IMPORTS);

                    if (interrupted()) {
                        interrupt(WriteImportsSteps.COMMAND);
                        return;
                    }
                }
            }
        }
    }

    private enum WriteCommitSteps {
        IMMUTABLES, BRANCH, COMMAND, INTERCEPTION_ID
    }

    @SuppressWarnings("fallthrough")
    private void writeCommit(Interception interception) {
        WriteCommitSteps step = WriteCommitSteps.IMMUTABLES;

        if (interrupted())
            step = (WriteCommitSteps) resume();

        switch (step) {
            case IMMUTABLES: {
                writePendingImmutables();

                if (interrupted()) {
                    interrupt(WriteCommitSteps.IMMUTABLES);
                    return;
                }
            }
            case BRANCH: {
                ensureRemoteBranchIs(getBranch());

                if (interrupted()) {
                    interrupt(WriteCommitSteps.BRANCH);
                    return;
                }
            }
            case COMMAND: {
                writeCommand(COMMAND_COMMIT);

                if (interrupted()) {
                    interrupt(WriteCommitSteps.COMMAND);
                    return;
                }

                for (int i = _snapshottedBranches.size() - 1; i >= 0; i--) {
                    Transaction branch = _snapshottedBranches.remove(i);
                    getEndpoint().onBranchUpToDate(branch);
                }
            }
            case INTERCEPTION_ID: {
                if (!canWriteByte()) {
                    interrupt(WriteCommitSteps.INTERCEPTION_ID);
                    return;
                }

                writeByte(interception.getId());
            }
        }
    }

    private final void writePendingImmutables() {
        Transaction initialBranch;
        Snapshot initialSnapshot;
        int initialMapIndex1;
        int initialMapIndex2;

        boolean transactionDone = false;
        Transaction branch = null;

        if (interrupted()) {
            initialBranch = (Transaction) resume();
            initialSnapshot = (Snapshot) resume();
            initialMapIndex1 = resumeInt();
            initialMapIndex2 = resumeInt();

            transactionDone = resumeBoolean();
            branch = (Transaction) resume();
        } else {
            initialBranch = getBranch();
            initialSnapshot = getSnapshot();
            initialMapIndex1 = getMapIndex1();
            initialMapIndex2 = getMapIndex2();
            setVisitingGatheredVersions(false);
            setNextCommand(COMMAND_PUBLIC_IMPORT);
        }

        for (;;) {
            if (branch == null)
                branch = _newPendingSnapshotsBranches.poll();

            if (branch == null)
                break;

            if (!transactionDone) {
                ensureRemoteBranchIs(branch);

                if (interrupted()) {
                    interrupt(branch);
                    interruptBoolean(false);

                    interruptInt(initialMapIndex2);
                    interruptInt(initialMapIndex1);
                    interrupt(initialSnapshot);
                    interrupt(initialBranch);
                    return;
                }
            }

            setBranch(branch);
            writeBranchImmutables();

            if (interrupted()) {
                interrupt(branch);
                interruptBoolean(true);

                interruptInt(initialMapIndex2);
                interruptInt(initialMapIndex1);
                interrupt(initialSnapshot);
                interrupt(initialBranch);
                return;
            }

            _snapshottedBranches.add(branch);
            branch = null;
            transactionDone = false;
        }

        setBranch(initialBranch);
        setSnapshot(initialSnapshot);
        setMapIndex1(initialMapIndex1);
        setMapIndex2(initialMapIndex2);
        setVisitingGatheredVersions(true);
    }

    /**
     * All mutable data is intercepted and written by the acknowledger, so pending
     * snapshots are only sent for immutable objects.
     */
    private final void writeBranchImmutables() {
        if (Debug.ENABLED) {
            /*
             * Interception must have been added when reading the branch, even if
             * acknowledger is not registered yet.
             */
            Debug.assertion(getBranch().getSharedSnapshot().getInterception() != null);
        }

        Version shared = null;

        if (interrupted())
            shared = (Version) resume();
        else
            ensureIntercepted(getBranch());

        Queue<TObject.Version> queue = getEndpoint().getPendingSnapshots(getBranch());

        for (;;) {
            if (shared == null) {
                if (queue == null || queue.size() == 0)
                    break;

                shared = queue.poll();

                if (Debug.ENABLED)
                    Debug.assertion(shared.getTrunk() == getBranch());
            }

            if (Debug.ENABLED)
                Debug.assertion(getEndpoint().getStatus(shared) == Status.CREATED);

            if (shared.isImmutable()) {
                if (shared.visitable(this, TransactionManager.OBJECTS_VERSIONS_INDEX)) {
                    shared.visit(this);

                    if (interrupted()) {
                        interrupt(shared);
                        return;
                    }
                }

                getEndpoint().setStatus(shared, Status.SNAPSHOTTED);

                if (Debug.COMMUNICATIONS)
                    getEndpoint().getHelper().markAsSnapshoted(shared.getTrunk(), shared);
            }

            shared = null;
        }
    }

    private final class InterceptionAcknowledger extends Acknowledger {

        private boolean _written;

        @Override
        protected void onVisitingBranch(Visitor visitor) {
            super.onVisitingBranch(visitor);

            setVisitsReads(visitor.getBranch().getConflictDetection() == ConflictDetection.READ_WRITE_CONFLICTS);
        }

        @Override
        protected Action onVisitingMap(Visitor visitor, int mapIndex) {
            Action action = super.onVisitingMap(visitor, mapIndex);

            if (action == Action.VISIT) {
                VersionMap map = InterceptorWriter.this.getSnapshot().getVersionMaps()[mapIndex];

                if (map.getSource() != null && map.getSource().Connection == getEndpoint().getConnection().getSharedVersion_objectfabric())
                    action = Action.SKIP;
            }

            return action;
        }

        @Override
        protected void onVisitingVersions(Visitor visitor, Version shared) {
            super.onVisitingVersions(visitor, shared);

            if (visitingReads())
                setNextCommand(COMMAND_READ);
            else
                setNextCommand(COMMAND_WRITE);

            _written = true;
        }

        @Override
        protected void onVisitedBranch(Visitor visitor) {
            boolean visitedBranch = false;

            if (interrupted())
                visitedBranch = resumeBoolean();

            if (!visitedBranch) {
                super.onVisitedBranch(visitor);

                if (interrupted()) {
                    interruptBoolean(false);
                    return;
                }
            }

            if (_written) {
                writeCommit(getInterception());

                if (interrupted()) {
                    interruptBoolean(true);
                    return;
                }

                _written = false;
            }
        }

        @Override
        protected void onUpToDate(Transaction branch) {
            super.onUpToDate(branch);

            getEndpoint().onBranchUpToDate(branch);
        }

        @Override
        protected boolean requestRun() {
            getEndpoint().getConnection().requestWrite();
            return true;
        }
    }

    // Debug

    @Override
    void addThreadContextObjects(List<Object> list) {
        super.addThreadContextObjects(list);

        list.add(_acknowledger);
    }

    @Override
    protected String getCommandString(byte command) {
        String value = getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }

    public static String getCommandStringStatic(byte command) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        switch (command) {
            case COMMAND_IMPORTS:
                return "IMPORTS";
            case COMMAND_COMMIT:
                return "COMMIT";
            case COMMAND_UNBLOCK:
                return "UNBLOCK";
            case COMMAND_NEXT_SNAPSHOT_ACK:
                return "NEXT_SNAPSHOT_ACK";
            default:
                return null;
        }
    }
}