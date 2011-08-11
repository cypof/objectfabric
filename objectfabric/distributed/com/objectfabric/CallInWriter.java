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


import com.objectfabric.TObject;
import com.objectfabric.TransactionManager;
import com.objectfabric.Writer;
import com.objectfabric.CallInReader.Call;
import com.objectfabric.Connection.Endpoint;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class CallInWriter extends DistributedWriter {

    public static final byte COMMAND_CALLBACK_STORE = MAX_COMMAND_ID + 1;

    public static final byte COMMAND_CALLBACK_EXECUTE = MAX_COMMAND_ID + 2;

    public CallInWriter(Endpoint endpoint) {
        super(endpoint);

        setNextCommand(COMMAND_WRITE);
    }

    private enum WriteCallbackStep {
        WRITES, METHOD_VERSION, TRANSACTION, COMMAND
    }

    @SuppressWarnings("fallthrough")
    public void writeCallback(Call call) {
        WriteCallbackStep step = WriteCallbackStep.WRITES;

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
            case WRITES: {
                setVisitingGatheredVersions(false);

                if (call.getTransaction() != null) {
                    if (call.getTransaction().getWrites() != null) {
                        writeVersions(this, call.getTransaction().getWrites());

                        if (interrupted()) {
                            interrupt(WriteCallbackStep.WRITES);
                            return;
                        }
                    }

                    TransactionManager.abort(call.getTransaction());
                }

                // Otherwise fake transaction created for call will be GCed
            }
            case METHOD_VERSION: {
                call.getMethodVersion().visit(this);

                if (interrupted()) {
                    interrupt(WriteCallbackStep.METHOD_VERSION);
                    return;
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

    private static final void writeVersions(Writer writer, TObject.Version[] versions) {
        int index = 0;

        if (writer.interrupted())
            index = writer.resumeInt();

        for (; index < versions.length; index++) {
            if (versions[index] != null) {
                versions[index].visit(writer);

                if (writer.interrupted()) {
                    writer.interruptInt(index);
                    return;
                }
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
