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
import com.objectfabric.Connection.Endpoint.Status;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.UserTObject.Method;
import com.objectfabric.TObject.Version;
import com.objectfabric.Transaction.ConflictDetection;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.Queue;
import com.objectfabric.misc.ThreadAssert.AllowSharedRead;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class PropagatorWriter extends DistributedWriter {

    // TODO unify Propagator & Interceptor?
    // TODO make sure method data is not sent back with arguments

    // Publishing command
    public static final byte COMMAND_COMMIT = DistributedWriter.MAX_COMMAND_ID + 1;

    // Publishing command
    public static final byte COMMAND_ACK_INTERCEPTION = DistributedWriter.MAX_COMMAND_ID + 2;

    public static final byte COMMAND_NACK_INTERCEPTION = DistributedWriter.MAX_COMMAND_ID + 3;

    // TODO
    public static final byte COMMAND_BRANCH_DISCONNECTED = DistributedWriter.MAX_COMMAND_ID + 4;

    public static final byte COMMAND_NEXT_INTERCEPTION_SNAPSHOT = DistributedWriter.MAX_COMMAND_ID + 5;

    private final PropagationWalker _walker = new PropagationWalker(null);

    private final Queue<Transaction> _newPendingSnapshotsBranches = new Queue<Transaction>();

    private final List<Transaction> _snapshottedBranches = new List<Transaction>();

    @AllowSharedRead
    private PropagatorReader _reader;

    private Snapshot _createdSnapshot;

    public PropagatorWriter(Endpoint endpoint) {
        super(endpoint);

        init(_walker, false);
    }

    public Walker getWalker() {
        return _walker;
    }

    public Queue<Transaction> getNewPendingSnapshotsBranches() {
        return _newPendingSnapshotsBranches;
    }

    public PropagatorReader getReader() {
        return _reader;
    }

    public void setReader(PropagatorReader value) {
        _reader = value;
    }

    public void ensurePropagated(Transaction branch) {
        if (!_walker.registered(branch)) {
            if (Debug.COMMUNICATIONS_LOG)
                Log.write("PropagatorWriter: registering branch " + branch);

            getEndpoint().register(_walker, branch);

            /*
             * Sometimes registration happens after commit made by reader, and request is
             * lost.
             */
            getWalker().requestRun();
        }
    }

    @Override
    protected void onStopped(Throwable t) {
        disposeCreatedSnapshot();
        _reader.onWriteThreadStopped(_walker.copyBranches());
        _walker.unregisterFromAllBranches(t);
        super.onStopped(t);
    }

    private enum WriteStep {
        PARENT, WALKER, OTHER_WRITERS_SNAPSHOTS
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
            case WALKER: {
                _walker.walk(this);

                if (interrupted()) {
                    interrupt(WriteStep.WALKER);
                    return;
                }
            }
            case OTHER_WRITERS_SNAPSHOTS: {
                writeCommit(false);

                if (interrupted()) {
                    interrupt(WriteStep.OTHER_WRITERS_SNAPSHOTS);
                    return;
                }
            }
        }
    }

    private enum CommitTransactionSteps {
        FLUSH, SNAPSHOTS, BRANCH, COMMAND
    }

    @SuppressWarnings("fallthrough")
    private void writeCommit(boolean walking) {
        CommitTransactionSteps step = CommitTransactionSteps.FLUSH;

        if (interrupted())
            step = (CommitTransactionSteps) resume();
        else if (!walking)
            resetWroteCommand();

        switch (step) {
            case FLUSH: {
                if (walking) {
                    if (Debug.ENABLED)
                        Debug.assertion(!getEndpoint().intercepts(getBranch()));

                    if (getBranch().getGranularity() != Granularity.ALL) {
                        flush();

                        if (interrupted()) {
                            interrupt(CommitTransactionSteps.FLUSH);
                            return;
                        }
                    }
                }
            }
            case SNAPSHOTS: {
                writePendingSnapshots();

                if (interrupted()) {
                    interrupt(CommitTransactionSteps.SNAPSHOTS);
                    return;
                }
            }
            case BRANCH: {
                if (walking || getWroteCommand()) {
                    if (getBranch() != null) {
                        ensureRemoteBranchIs(getBranch());

                        if (interrupted()) {
                            interrupt(CommitTransactionSteps.BRANCH);
                            return;
                        }
                    }
                }
            }
            case COMMAND: {
                if (walking || getWroteCommand()) {
                    writeCommand(COMMAND_COMMIT);

                    if (interrupted()) {
                        interrupt(CommitTransactionSteps.COMMAND);
                        return;
                    }
                }

                for (int i = _snapshottedBranches.size() - 1; i >= 0; i--) {
                    Transaction branch = _snapshottedBranches.remove(i);
                    getEndpoint().onBranchUpToDate(branch);
                }
            }
        }
    }

    private final void writePendingSnapshots() {
        Transaction initialBranch = getBranch();

        boolean transactionDone = false;

        if (interrupted()) {
            transactionDone = resumeBoolean();
            setBranch((Transaction) resume());
        } else {
            setVisitingNewObject(true);
            setNextCommand(COMMAND_PUBLIC_IMPORT);
        }

        for (;;) {
            if (getBranch() == null)
                setBranch(_newPendingSnapshotsBranches.poll());

            if (getBranch() == null)
                break;

            if (!transactionDone) {
                ensureRemoteBranchIs(getBranch());

                if (interrupted()) {
                    interrupt(getBranch());
                    interruptBoolean(false);

                    setBranch(initialBranch);
                    return;
                }
            }

            Snapshot snapshot;

            if (getBranch() != initialBranch)
                snapshot = getWalker().getSnapshot(getBranch());
            else
                snapshot = getSnapshot();

            writePendingSnapshots(snapshot);

            if (interrupted()) {
                interrupt(getBranch());
                interruptBoolean(true);

                setBranch(initialBranch);
                return;
            }

            _snapshottedBranches.add(getBranch());
            setBranch(null);
            transactionDone = false;
        }

        setBranch(initialBranch);
        setVisitingNewObject(false);
    }

    private final void writePendingSnapshots(Snapshot snapshot) {
        if (Debug.ENABLED)
            Debug.assertion(!getEndpoint().intercepts(getBranch()));

        Snapshot initialSnapshot = getSnapshot();
        int initialMapIndex1 = getMapIndex1();
        int initialMapIndex2 = getMapIndex2();

        Version shared = null;
        boolean registered;

        if (interrupted()) {
            shared = (Version) resume();
            registered = resumeBoolean();
            setSnapshot((Snapshot) resume());
            setMapIndex1(resumeInt());
            setMapIndex2(resumeInt());
        } else {
            registered = snapshot != null;

            if (registered) {
                setSnapshot(snapshot);
                setMapIndex1(TransactionManager.OBJECTS_VERSIONS_INDEX);
                setMapIndex2(getSnapshot().getAcknowledgedIndex() + 1);
            }
        }

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

            UserTObject object = shared.getOrRecreateTObject();

            if (!registered && !shared.isImmutable() && !(object instanceof Method)) {
                ensurePropagated(getBranch());
                getEndpoint().addGCPreventer(object);
                setSnapshot(getWalker().getSnapshot(getBranch()));
                registered = true;
                disposeCreatedSnapshot();
                setMapIndex1(TransactionManager.OBJECTS_VERSIONS_INDEX);
                setMapIndex2(getSnapshot().getAcknowledgedIndex() + 1);
            }

            if (!registered && _createdSnapshot == null) {
                _createdSnapshot = getBranch().takeSnapshot();

                if (Debug.ENABLED)
                    getBranch().takeSnapshotDebug(_createdSnapshot, this, "PropagatorWriter.writePendingSnapshots");

                setSnapshot(_createdSnapshot);
                setMapIndex1(TransactionManager.OBJECTS_VERSIONS_INDEX);
                setMapIndex2(getSnapshot().getAcknowledgedIndex() + 1);
            }

            shared.visit(this);

            if (interrupted()) {
                interruptInt(getMapIndex2());
                interruptInt(getMapIndex1());
                interrupt(getSnapshot());
                interruptBoolean(registered);
                interrupt(shared);

                setSnapshot(initialSnapshot);
                setMapIndex1(initialMapIndex1);
                setMapIndex2(initialMapIndex2);
                return;
            }

            getEndpoint().setStatus(shared, Status.SNAPSHOTTED);

            if (Debug.COMMUNICATIONS)
                getEndpoint().getHelper().markAsSnapshoted(shared.getTrunk(), shared);

            shared = null;
        }

        setSnapshot(initialSnapshot);
        setMapIndex1(initialMapIndex1);
        setMapIndex2(initialMapIndex2);
        disposeCreatedSnapshot();
    }

    private final void disposeCreatedSnapshot() {
        if (_createdSnapshot != null) {
            if (Debug.ENABLED)
                getBranch().releaseSnapshotDebug(_createdSnapshot, this, "PropagatorWriter.disposeCreatedSnapshot");

            getBranch().releaseSnapshot(_createdSnapshot);
            _createdSnapshot = null;
        }
    }

    private enum WriteAckStep {
        COMMAND, INTERCEPTION_ID
    }

    @SuppressWarnings("fallthrough")
    public void writeAck(Transaction branch, byte interceptionId) {
        WriteAckStep step = WriteAckStep.COMMAND;

        if (interrupted())
            step = (WriteAckStep) resume();

        switch (step) {
            case COMMAND: {
                writeCommandInBranch(branch, COMMAND_ACK_INTERCEPTION);

                if (interrupted()) {
                    interrupt(WriteAckStep.COMMAND);
                    return;
                }
            }
            case INTERCEPTION_ID: {
                if (!canWriteByte()) {
                    interrupt(WriteAckStep.INTERCEPTION_ID);
                    return;
                }

                writeByte(interceptionId);
            }
        }
    }

    //

    private final class PropagationWalker extends Walker {

        private final SourceSplitter _sourceSplitter = new SourceSplitter();

        private boolean _writingData, _wroteData;

        public PropagationWalker(Granularity granularity) {
            super(granularity);
        }

        @Override
        boolean casSnapshotWithThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot) {
            if (super.casSnapshotWithThis(branch, snapshot, newSnapshot)) {
                if (branch.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS)
                    _reader.onRegistered(branch, snapshot);
                else
                    _sourceSplitter.register(branch);

                return true;
            }

            return false;
        }

        @Override
        boolean casSnapshotWithoutThis(Transaction branch, Snapshot snapshot, Snapshot newSnapshot, Throwable throwable) {
            if (super.casSnapshotWithoutThis(branch, snapshot, newSnapshot, throwable)) {
                if (branch.getConflictDetection() != ConflictDetection.LAST_WRITE_WINS)
                    _reader.onUnregistered(branch, snapshot);
                else
                    _sourceSplitter.unregister(branch, throwable);

                return true;
            }

            return false;
        }

        @Override
        protected void onVisitingBranch(Visitor visitor) {
            super.onVisitingBranch(visitor);

            if (!visitor.interrupted())
                _wroteData = false;
        }

        @Override
        protected Action onVisitingMap(Visitor visitor, int mapIndex) {
            Action action = super.onVisitingMap(visitor, mapIndex);

            if (action == Action.VISIT) {
                VersionMap map = PropagatorWriter.this.getSnapshot().getVersionMaps()[mapIndex];

                if (map.getSource() != null && map.getSource().Connection == getEndpoint().getConnection().getSharedVersion_objectfabric()) {
                    if (_writingData) {
                        writeCommit(true);

                        if (interrupted())
                            return null;

                        _writingData = false;
                    }

                    writeAck(getBranch(), map.getSource().InterceptionId);

                    if (interrupted())
                        return null;

                    return Action.SKIP;
                }
            }

            return action;
        }

        @Override
        protected boolean flushOnSourceChangeAndReturnIfDelayMerge(Visitor visitor) {
            // Skip, flush done before committing transaction
            return true;
        }

        @Override
        protected Action onVisitingTObject(Visitor visitor, TObject object) {
            Action action = super.onVisitingTObject(visitor, object);

            if (action == Action.VISIT) {
                Status status = getEndpoint().getStatus((Version) object);

                /*
                 * TODO check if all modified objects are sent without this. Sending in
                 * all cases, even if remotely GC, until object is disconnected, where
                 * status goes back to unknown.
                 */
                if (status == Status.UNKNOWN)
                    return Action.SKIP;
            }

            return action;
        }

        @Override
        protected void onVisitingVersions(Visitor visitor, Version shared) {
            super.onVisitingVersions(visitor, shared);

            if (Debug.ENABLED)
                Debug.assertion(!(shared.getReference().get() instanceof Method));

            if (!_writingData) {
                ensureRemoteBranchIs(getBranch());

                if (interrupted())
                    return;

                _writingData = true;
                _wroteData = true;
            }

            setNextCommand(COMMAND_PUBLIC_VERSION);
        }

        @Override
        protected void onVisitedMap(Visitor visitor, int mapIndex) {
            boolean visitedMap = false;

            if (interrupted())
                visitedMap = resumeBoolean();

            if (!visitedMap) {
                super.onVisitedMap(visitor, mapIndex);

                if (interrupted()) {
                    interruptBoolean(false);
                    return;
                }
            }

            if (visitor.getBranch().getGranularity() == Granularity.ALL) {
                if (_writingData) {
                    writeCommit(true);

                    if (interrupted()) {
                        interruptBoolean(true);
                        return;
                    }

                    _writingData = false;
                }
            }
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

            if (_writingData) {
                if (Debug.ENABLED)
                    Debug.assertion(visitor.getBranch().getGranularity() != Granularity.ALL);

                writeCommit(true);

                if (interrupted()) {
                    interruptBoolean(true);
                    return;
                }

                _writingData = false;
            }

            if (getBranch().getConflictDetection() != ConflictDetection.LAST_WRITE_WINS) {
                _reader.onPropagationEnded(getBranch(), PropagatorWriter.this.getSnapshot(), _wroteData);

                if (interrupted()) {
                    interruptBoolean(true);
                    return;
                }
            }
        }

        @Override
        protected void onUpToDate(Transaction branch) {
            super.onUpToDate(branch);

            getEndpoint().onBranchUpToDate(branch);
        }

        @Override
        protected void releaseSnapshot(Transaction branch, Snapshot snapshot, int mapIndex) {
            if (getBranch().getConflictDetection() == ConflictDetection.LAST_WRITE_WINS)
                _sourceSplitter.transferWatch(branch, snapshot, mapIndex, _walker);
            else if (!_reader.onDisposingReturnIfKeep(branch, snapshot, mapIndex))
                super.releaseSnapshot(branch, snapshot, mapIndex);
        }

        // Other threads

        @Override
        protected boolean requestRun() {
            getEndpoint().getConnection().requestWrite();
            return true;
        }

        @Override
        void onBlocked(final Transaction branch, Connection.Version connection) {
            if (Debug.THREADS)
                getEndpoint().assertNotWriteThread();

            if (connection == getEndpoint().getConnection().getSharedVersion_objectfabric())
                _reader.onBlocked(branch);
        }

        @Override
        protected void onGarbageCollected(TObject shared) {
            super.onGarbageCollected(shared);

            getEndpoint().onGarbageCollected((Version) shared);
        }
    }

    // Debug

    @Override
    void addThreadContextObjects(List<Object> list) {
        super.addThreadContextObjects(list);

        list.add(_walker);
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
            case COMMAND_COMMIT:
                return "COMMIT";
            case COMMAND_ACK_INTERCEPTION:
                return "ACK_INTERCEPTION";
            case COMMAND_NACK_INTERCEPTION:
                return "NACK_INTERCEPTION";
            case COMMAND_NEXT_INTERCEPTION_SNAPSHOT:
                return "NEXT_INTERCEPTION_SNAPSHOT";
            default:
                return null;
        }
    }
}
