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

import java.util.HashMap;
import java.util.concurrent.Executor;

import com.objectfabric.Connection.Endpoint;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.ThreadAssert.AllowSharedRead;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class CallOutWriter extends DistributedWriter implements Executor {

    public static final byte COMMAND_CALL_STORE = DistributedWriter.MAX_COMMAND_ID + 1;

    public static final byte COMMAND_CALL_EXECUTE = DistributedWriter.MAX_COMMAND_ID + 2;

    // TODO COMMAND_CANCEL -> cancel the future on other site

    @AllowSharedRead
    private final HashMap<Transaction, MethodCall> _pendingCalls = new HashMap<Transaction, MethodCall>();

    private Exception _closingException;

    public CallOutWriter(Endpoint endpoint) {
        super(endpoint);

        setNextCommand(COMMAND_WRITE);
    }

    public final Object getPendingCallsLock() {
        return _pendingCalls;
    }

    public final HashMap<Transaction, MethodCall> getPendingCalls() {
        if (Debug.ENABLED)
            PlatformThread.assertHoldsLock(_pendingCalls);

        return _pendingCalls;
    }

    public final void setClosingException(Exception value) {
        if (Debug.ENABLED) {
            Debug.assertion(_closingException == null && value != null);
            PlatformThread.assertHoldsLock(_pendingCalls);
        }

        _closingException = value;
    }

    //

    public void execute(Runnable runnable) {
        final MethodCall call = (MethodCall) runnable;
        Transaction transaction = call.getTransaction();

        if (transaction == null) {
            transaction = Site.getLocal().getTrunk().getOrCreateChild();
            call.setFakeTransaction(transaction);
        }

        int count;

        synchronized (_pendingCalls) {
            if (_closingException != null)
                call.setException(_closingException, true);
            else {
                if (Debug.ENABLED) {
                    Helper.getInstance().disableEqualsOrHashCheck();
                    Debug.assertion(!_pendingCalls.containsKey(transaction));
                }

                _pendingCalls.put(transaction, call);

                if (Debug.ENABLED)
                    Helper.getInstance().enableEqualsOrHashCheck();
            }

            count = _pendingCalls.size();
        }

        if (count >= OverloadHandler.getInstance().getPendingCallsThreshold())
            OverloadHandler.getInstance().onPendingCallsThresholdReached(getEndpoint().getConnection());

        if (_closingException == null) {
            getEndpoint().enqueueOnWriterThread(new Command() {

                public MultiplexerWriter getWriter() {
                    return CallOutWriter.this;
                }

                public void run() {
                    writeCall(call);
                }
            });
        }
    }

    private enum WriteCallStep {
        VERSIONS, METHOD_VERSION, METHOD_INDEX, TARGET, METHOD, TRANSACTION, COMMAND
    }

    @SuppressWarnings("fallthrough")
    private final void writeCall(MethodCall call) {
        if (Debug.ENABLED)
            Debug.assertion(getListener() == null);

        WriteCallStep step = WriteCallStep.VERSIONS;

        if (interrupted())
            step = (WriteCallStep) resume();
        else {
            if (Debug.THREADS)
                ThreadAssert.exchangeTake(call);

            if (call.getTransaction() != null) {
                int count = 1;

                TObject.Version[][] privateSnapshot = call.getTransaction().getPrivateSnapshotVersions();

                if (privateSnapshot != null) {
                    count += privateSnapshot.length;

                    if (privateSnapshot[TransactionSets.IMPORTS_INDEX] == null)
                        count--;
                }

                if (call.getTransaction().getWrites() != null)
                    count++;

                TObject.Version[][] writes = new TObject.Version[count][];
                writes[0] = TransactionManager.OBJECTS_VERSIONS;

                if (privateSnapshot != null) {
                    if (privateSnapshot[TransactionSets.IMPORTS_INDEX] == null)
                        PlatformAdapter.arraycopy(privateSnapshot, 1, writes, 1, privateSnapshot.length - 1);
                    else
                        PlatformAdapter.arraycopy(privateSnapshot, 0, writes, 1, privateSnapshot.length);
                }

                if (call.getTransaction().getWrites() != null)
                    writes[writes.length - 1] = call.getTransaction().getWrites();

                Snapshot snapshot = new Snapshot();
                snapshot.setWrites(writes);

                setBranch(call.getTransaction().getBranch());
                setSnapshot(snapshot);
                setMapIndex1(1);
                setMapIndex2(snapshot.getWrites().length);
                visitBranch();
            }
        }

        switch (step) {
            case VERSIONS: {
                if (call.getTransaction() != null) {
                    writeSnapshotVersions();

                    if (interrupted()) {
                        interrupt(WriteCallStep.VERSIONS);
                        return;
                    }
                }
            }
            case METHOD_VERSION: {
                setVisitingGatheredVersions(false);
                call.getMethodVersion().visit(this);

                if (interrupted()) {
                    interrupt(WriteCallStep.METHOD_VERSION);
                    return;
                }

                setVisitingGatheredVersions(true);
            }
            case METHOD_INDEX: {
                int index = call.getTransaction() != null ? call.getIndex() : -call.getIndex() - 1;
                pushObject(index);

                if (interrupted()) {
                    interrupt(WriteCallStep.METHOD_INDEX);
                    return;
                }
            }
            case TARGET: {
                pushTObject(call.getTarget());

                if (interrupted()) {
                    interrupt(WriteCallStep.TARGET);
                    return;
                }
            }
            case METHOD: {
                pushTObject(call.getMethod());

                if (interrupted()) {
                    interrupt(WriteCallStep.METHOD);
                    return;
                }
            }
            case TRANSACTION: {
                if (call.getTransaction() != null)
                    pushTObject(call.getTransaction());
                else
                    pushTObject(call.getFakeTransaction());

                if (interrupted()) {
                    interrupt(WriteCallStep.TRANSACTION);
                    return;
                }
            }
            case COMMAND: {
                /*
                 * TODO also makes sure call is invoked when its snapshot has just been
                 * written so that callee is in sync with caller.
                 */
                writeCommand(COMMAND_CALL_STORE);

                if (interrupted()) {
                    interrupt(WriteCallStep.COMMAND);
                    return;
                }

                if (Debug.COMMUNICATIONS)
                    popStack(4);
            }
        }

        if (Debug.THREADS)
            ThreadAssert.exchangeGive(call, call);

        runWhenBranchesAreUpToDate(new Runnable() {

            public void run() {
                writeCommand(COMMAND_CALL_EXECUTE);
            }
        });
    }

    private final void writeSnapshotVersions() {
        Version shared = null;

        if (interrupted())
            shared = (Version) resume();

        for (;;) {
            if (shared == null)
                shared = popWrite();

            if (shared == null)
                break;

            shared.visit(this);

            if (interrupted()) {
                interrupt(shared);
                return;
            }

            shared = null;
        }

        onPoppedAllWrites();
    }

    // Debug

    @Override
    protected void assertIdle() {
        super.assertIdle();

        Debug.assertion(_pendingCalls.size() == 0);
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
            case COMMAND_CALL_STORE:
                return "CALL_STORE";
            case COMMAND_CALL_EXECUTE:
                return "CALL_EXECUTE";
            default:
                return null;
        }
    }
}
