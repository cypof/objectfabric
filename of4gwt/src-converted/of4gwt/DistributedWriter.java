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

import of4gwt.Connection.Endpoint;
import of4gwt.Connection.Endpoint.Status;
import of4gwt.TObject.Descriptor;
import of4gwt.TObject.Reference;
import of4gwt.TObject.UserTObject;
import of4gwt.TObject.UserTObject.Method;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformClass;
import of4gwt.misc.Queue;
import of4gwt.misc.QueueOfInt;
import of4gwt.misc.ThreadAssert.AllowSharedRead;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
abstract class DistributedWriter extends MultiplexerWriter {

    public interface Command extends Runnable {

        DistributedWriter getWriter();
    }

    public static final byte COMMAND_PUBLIC_IMPORT = 0;

    public static final byte COMMAND_PUBLIC_VERSION = 1;

    public static final byte COMMAND_READ = 2;

    public static final byte COMMAND_WRITE = 3;

    public static final byte COMMAND_SET_CURRENT_BRANCH = 4;

    //

    public static final byte MAX_COMMAND_ID = 4;

    //

    @AllowSharedRead
    private final Endpoint _endpoint;

    private final Queue<Runnable> _runnables = new Queue<Runnable>();

    private Transaction _remoteBranch;

    //

    private final QueueOfInt _waitedBranchCounts = new QueueOfInt();

    private final Queue<Runnable> _waiting = new Queue<Runnable>();

    //

    private boolean _wroteCommand;

    // Debug

    private final Queue<List<Transaction>> _waitedBranchesForDebug = new Queue<List<Transaction>>();

    private int _expectedReaderStackSize;

    static {
        if (Debug.ENABLED) {
            Debug.assertion(COMMAND_PUBLIC_IMPORT != NULL_COMMAND);
            Debug.assertion(COMMAND_SET_CURRENT_BRANCH == MAX_COMMAND_ID);
        }
    }

    public DistributedWriter(Endpoint endpoint) {
        if (endpoint == null)
            throw new IllegalArgumentException();

        _endpoint = endpoint;
    }

    public final Endpoint getEndpoint() {
        return _endpoint;
    }

    public final Transaction getRemoteBranch() {
        return _remoteBranch;
    }

    public final boolean getWroteCommand() {
        return _wroteCommand;
    }

    public final void resetWroteCommand() {
        _wroteCommand = false;
    }

    //

    public final void enqueue(Runnable runnable) {
        if (Debug.THREADS)
            _endpoint.assertWriteThread();

        _runnables.add(runnable);
    }

    //

    @Override
    protected void write() {
        Runnable runnable = null;

        if (interrupted())
            runnable = (Runnable) resume();

        for (;;) {
            if (runnable == null)
                runnable = _runnables.poll();

            if (runnable == null)
                break;

            runnable.run();

            if (interrupted()) {
                interrupt(runnable);
                return;
            }

            runnable = null;
        }
    }

    //

    public final void pushObject(Object value) {
        int debug = -1;

        if (Debug.COMMUNICATIONS) {
            if (interrupted())
                debug = resumeInt();
            else
                debug = getAndIncrementDebugCommandCounter();
        }

        UnknownObjectSerializer.write(this, value, debug);

        if (Debug.COMMUNICATIONS) {
            if (interrupted())
                interruptInt(debug);
            else
                _expectedReaderStackSize++;
        }
    }

    public final void pushTObject(TObject object) {
        writeTObject(object);

        if (Debug.COMMUNICATIONS)
            if (!interrupted())
                _expectedReaderStackSize++;
    }

    public final void pushTObject(TObject.Version shared) {
        writeTObject(shared);

        if (Debug.COMMUNICATIONS)
            if (!interrupted())
                _expectedReaderStackSize++;
    }

    //

    @Override
    protected final boolean isKnown(Version shared) {
        Status status = _endpoint.getStatus(shared);
        boolean known = status == Status.SNAPSHOTTED || status == Status.CREATED;

        if (!known) {
            Validator validator = _endpoint.getValidator();

            if (validator != null) {
                UserTObject object = shared.getOrRecreateTObject();

                if (!(object instanceof Session) && !(object instanceof Method)) {
                    if (Debug.ENABLED)
                        Helper.getInstance().setNoTransaction(false);

                    ExpectedExceptionThrower.validateRead(validator, object);

                    if (Debug.ENABLED)
                        Helper.getInstance().setNoTransaction(true);
                }
            }
        }

        return known;
    }

    @Override
    protected final void setCreated(Version shared) {
        // Re-test status as two objects can be created in parallel
        Status status = _endpoint.getStatus(shared);

        if (status == Status.UNKNOWN || status == Status.GCED) {
            _endpoint.setStatus(shared, Status.CREATED);

            // Read objects do not need to be snapshotted
            if (!visitingReads())
                _endpoint.addPendingSnapshot(shared);
        }
    }

    //

    /**
     * Exit does not use the interruption stack.
     */
    public final void writeExit(byte command) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Debug.assertion((command & FLAG_TOBJECT) == 0);
        Debug.assertion((command & FLAG_EOF) != 0);
        Debug.assertion((command & FLAG_IMMUTABLE) == 0);

        if (Debug.COMMUNICATIONS_LOG)
            Log.write(PlatformClass.getSimpleName(getClass()) + ".writeExit, code: " + command);

        writeByte(command, DEBUG_TAG_CODE);
    }

    @Override
    public void writeCommand(byte command) {
        if (Debug.ENABLED) {
            Debug.assertion((command & FLAG_TOBJECT) == 0);
            Debug.assertion((command & FLAG_EOF) == 0);
            Debug.assertion((command & FLAG_IMMUTABLE) == 0);
        }

        if (Debug.COMMUNICATIONS_LOG)
            if (!interrupted())
                log("Command " + getCommandString(command) + " (" + getDebugCommandCounter() + ")");

        if (!Debug.COMMUNICATIONS) {
            if (interrupted())
                resume();

            if (!canWriteByte()) {
                interrupt(null);
                return;
            }

            writeByte(command, DEBUG_TAG_CODE);
        } else {
            boolean written = false;

            if (interrupted())
                written = resumeBoolean();

            if (!written) {
                if (!canWriteByte()) {
                    interruptBoolean(false);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Log.write(PlatformClass.getSimpleName(getClass()) + ".writeCommand, code: " + command);

                writeByte(command, DEBUG_TAG_CODE);
            }

            if (!canWriteInteger()) {
                interruptBoolean(true);
                return;
            }

            writeInteger(getAndIncrementDebugCommandCounter());
        }

        _wroteCommand = true;
    }

    @SuppressWarnings("fallthrough")
    public void writeCommandInBranch(Transaction branch, byte command) {
        boolean transactionDone = false;

        if (interrupted())
            transactionDone = resumeBoolean();

        if (!transactionDone) {
            ensureRemoteBranchIs(branch);

            if (interrupted()) {
                interruptBoolean(false);
                return;
            }
        }

        writeCommand(command);

        if (interrupted()) {
            interruptBoolean(true);
            return;
        }
    }

    //

    protected final void runWhenBranchesAreUpToDate(Runnable runnable) {
        int count = getEndpoint().getRegisteredBranchCount();

        if (count != 0 || getEndpoint().hasPendingSnapshots()) {
            _waitedBranchCounts.add(count);
            _waiting.add(runnable);

            if (Debug.ENABLED) {
                _waitedBranchesForDebug.add(getEndpoint().getRegisteredBranchForDebug());
                Debug.assertion(count == getEndpoint().getRegisteredBranchForDebug().size());
                Debug.assertion(_waitedBranchCounts.size() == _waiting.size());
                Debug.assertion(_waitedBranchCounts.size() == _waitedBranchesForDebug.size());
            }
        } else
            enqueue(runnable);
    }

    /**
     * If a new branch is registered, we don't know where the extension index are in
     * interceptor and propagator, so restart all snapshots.
     */
    protected final void onBranchRegistration() {
        int count = getEndpoint().getRegisteredBranchCount();
        List<Transaction> debug;

        if (Debug.ENABLED) {
            debug = getEndpoint().getRegisteredBranchForDebug();
            Debug.assertion(count > 0 && count == debug.size());
        }

        for (int i = 0; i < _waitedBranchCounts.size(); i++) {
            _waitedBranchCounts.set(i, count);

            if (Debug.ENABLED)
                _waitedBranchesForDebug.set(i, debug);
        }
    }

    public final void onBranchUpToDate(Transaction branch) {
        if (Debug.ENABLED) {
            Debug.assertion(_waitedBranchCounts.size() == _waiting.size());
            Debug.assertion(_waitedBranchCounts.size() == _waitedBranchesForDebug.size());
        }

        if (_waitedBranchCounts.size() > 0) {
            boolean registered = getEndpoint().isRegistered(branch);

            for (int i = 0; i < _waitedBranchCounts.size(); i++) {
                int count = _waitedBranchCounts.get(i);

                if (count > 0) {
                    if (registered) {
                        count--;

                        if (Debug.ENABLED) {
                            for (int j = _waitedBranchesForDebug.get(i).size() - 1; j >= 0; j--)
                                if (_waitedBranchesForDebug.get(i).get(j) == branch)
                                    _waitedBranchesForDebug.get(i).remove(j);

                            Debug.assertion(count == _waitedBranchesForDebug.get(i).size());
                        }

                        _waitedBranchCounts.set(i, count);
                    }
                }
            }

            for (;;) {
                if (_waitedBranchCounts.size() == 0 || getEndpoint().hasPendingSnapshots())
                    break;

                _waitedBranchCounts.poll();
                enqueue(_waiting.poll());

                if (Debug.ENABLED)
                    _waitedBranchesForDebug.poll();
            }

            if (Debug.ENABLED) {
                Debug.assertion(_waitedBranchCounts.size() == _waiting.size());
                Debug.assertion(_waitedBranchCounts.size() == _waitedBranchesForDebug.size());
            }
        }
    }

    //

    private enum SwitchStep {
        COMMAND, TRANSACTION
    }

    @SuppressWarnings("fallthrough")
    public final void ensureRemoteBranchIs(Transaction branch) {
        if (Debug.ENABLED)
            Debug.assertion(branch != null);

        if (branch != _remoteBranch) {
            SwitchStep step = SwitchStep.COMMAND;

            if (interrupted())
                step = (SwitchStep) resume();

            switch (step) {
                case COMMAND: {
                    writeCommand(COMMAND_SET_CURRENT_BRANCH);

                    if (interrupted()) {
                        interrupt(SwitchStep.COMMAND);
                        return;
                    }
                }
                case TRANSACTION: {
                    writeTObject(branch);

                    if (interrupted()) {
                        interrupt(SwitchStep.TRANSACTION);
                        return;
                    }
                }
            }

            _remoteBranch = branch;
        }
    }

    // Debug

    protected final int popStack(int count) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        _expectedReaderStackSize -= count;
        return _expectedReaderStackSize;
    }

    protected String getCommandString(byte command) {
        return getCommandStringStatic(command);
    }

    public static String getCommandStringStatic(byte command) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        switch (command) {
            case COMMAND_PUBLIC_IMPORT:
                return "PUBLIC_IMPORT";
            case COMMAND_PUBLIC_VERSION:
                return "PUBLIC_VERSION";
            case COMMAND_READ:
                return "READ";
            case COMMAND_WRITE:
                return "WRITE";
            case COMMAND_SET_CURRENT_BRANCH:
                return "SET_CURRENT_BRANCH";
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Sends expected reader state for debug purposes.
     */
    @Override
    protected final int getCustomDebugInfo1() {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Descriptor descriptor = null;

        if (_remoteBranch != null) {
            Reference ref = _remoteBranch.getSharedVersion_objectfabric().getReference();

            if (ref instanceof Descriptor)
                descriptor = (Descriptor) ref;
        }

        return descriptor != null ? descriptor.getId() : -1;
    }

    @Override
    protected final int getCustomDebugInfo2() {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        return _expectedReaderStackSize;
    }
}
