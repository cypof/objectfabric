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

import of4gwt.CallInReader.Call;
import of4gwt.Connection.Endpoint;
import of4gwt.Connection.Endpoint.Status;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class CallInWriter extends DistributedWriter {

    public static final byte COMMAND_CALLBACK_STORE = MAX_COMMAND_ID + 1;

    public static final byte COMMAND_CALLBACK_EXECUTE = MAX_COMMAND_ID + 2;

    private final List<Version> _transactionVersions = new List<Version>();

    public CallInWriter(Endpoint endpoint) {
        super(endpoint);

        setNextCommand(COMMAND_WRITE);
    }

    private enum WriteCallbackStep {
        METHOD_VERSION, WRITES, TRANSACTION, COMMAND
    }

    @SuppressWarnings("fallthrough")
    public void writeCallback(Call call) {
        WriteCallbackStep step = WriteCallbackStep.METHOD_VERSION;

        if (interrupted())
            step = (WriteCallbackStep) resume();
        else {
            if (Debug.THREADS)
                ThreadAssert.exchangeTake(call);

            if (Debug.ENABLED) {
                Debug.assertion(getListener() == null && getBranch() == null && getSnapshot() == null);

                if (call.getTransaction() != null)
                    Debug.assertion(call.getTransaction().noReads());
            }
        }

        switch (step) {
            case METHOD_VERSION: {
                setVisitingGatheredVersions(false);

                call.getMethodVersion().visit(this);

                if (interrupted()) {
                    interrupt(WriteCallbackStep.METHOD_VERSION);
                    return;
                }

                if (call.getTransaction() != null) {
                    Version[] writes = call.getTransaction().getWrites();

                    if (writes != null)
                        for (int i = 0; i < writes.length; i++)
                            if (writes[i] != null)
                                _transactionVersions.add(writes[i]);
                }
            }
            case WRITES: {
                /*
                 * Also send private updates that could have been done on shared objects
                 * by transaction to maintain consistency between sides.
                 */
                if (call.getTransaction() != null && call.getTransaction().getWrites() != null) {
                    writeVersions();

                    if (interrupted()) {
                        interrupt(WriteCallbackStep.WRITES);
                        return;
                    }

                    TransactionManager.abort(call.getTransaction());
                }

                setVisitingGatheredVersions(true);
            }
            case TRANSACTION: {
                pushTObject(call.getTransaction() != null ? call.getTransaction() : call.getFakeTransaction());

                if (interrupted()) {
                    interrupt(WriteCallbackStep.TRANSACTION);
                    return;
                }
            }
            case COMMAND: {
                writeCommand(COMMAND_CALLBACK_STORE);

                if (interrupted()) {
                    interrupt(WriteCallbackStep.COMMAND);
                    return;
                }

                if (Debug.COMMUNICATIONS)
                    popStack(1);
            }
        }

        runWhenBranchesAreUpToDate(new Runnable() {

            public void run() {
                writeCommand(COMMAND_CALLBACK_EXECUTE);
            }
        });
    }

    /**
     * Only sends versions if shared with remote site. Loops to make sure we don't miss
     * versions that became shared during process.
     */
    private final void writeVersions() {
        Version version = null;

        if (interrupted())
            version = (Version) resume();

        for (;;) {
            if (version == null) {
                for (int i = _transactionVersions.size() - 1; i >= 0; i--) {
                    Version shared = _transactionVersions.get(i).getShared();
                    Status status = getEndpoint().getStatus(shared);
                    boolean known = status == Status.SNAPSHOTTED || status == Status.CREATED;

                    if (known) {
                        version = _transactionVersions.remove(i);
                        break;
                    }
                }

                if (version == null) {
                    _transactionVersions.clear();
                    break;
                }
            }

            version.visit(this);

            if (interrupted()) {
                interrupt(version);
                return;
            }
        }
    }

    // Debug

    @Override
    protected String getCommandString(byte command) {
        String value = CallInWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }

    public static String getCommandStringStatic(byte command) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        switch (command) {
            case COMMAND_CALLBACK_STORE:
                return "CALLBACK_STORE";
            case COMMAND_CALLBACK_EXECUTE:
                return "CALLBACK_EXECUTE";
            default:
                return null;
        }
    }
}
