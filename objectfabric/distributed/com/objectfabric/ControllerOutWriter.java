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

import java.util.concurrent.atomic.AtomicInteger;

import com.objectfabric.Connection.Endpoint;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class ControllerOutWriter extends DistributedWriter {

    public static final byte COMMAND_CONNECTION_SENT = DistributedWriter.MAX_COMMAND_ID + 1;

    public static final byte COMMAND_CONNECTION_READY = DistributedWriter.MAX_COMMAND_ID + 2;

    public static final byte COMMAND_OBJECT_SENT = DistributedWriter.MAX_COMMAND_ID + 3;

    public static final byte COMMAND_OBJECT_READY = DistributedWriter.MAX_COMMAND_ID + 4;

    public static final byte COMMAND_HEARTBEAT = DistributedWriter.MAX_COMMAND_ID + 5;

    private final AtomicInteger _pendingSends = new AtomicInteger();

    public ControllerOutWriter(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void onStarted() {
        super.onStarted();

        if (getEndpoint().clientSide()) {
            enqueue(new Command() {

                public DistributedWriter getWriter() {
                    return ControllerOutWriter.this;
                }

                public void run() {
                    writeStart();
                }
            });

            getEndpoint().getConnection().requestWrite();
        }
    }

    private enum WriteStep {
        OBJECT, COMMAND
    }

    @SuppressWarnings("fallthrough")
    private void writeStart() {
        WriteStep step = WriteStep.OBJECT;

        if (interrupted())
            step = (WriteStep) resume();

        if (Debug.ENABLED)
            Debug.assertion(getBranch() == null && getListener() == null && getSnapshot() == null);

        switch (step) {
            case OBJECT: {
                writeCommand(COMMAND_CONNECTION_SENT);

                if (interrupted()) {
                    interrupt(WriteStep.OBJECT);
                    return;
                }
            }
            case COMMAND: {
                writeTObject(getEndpoint().getConnection());

                if (interrupted()) {
                    interrupt(WriteStep.COMMAND);
                    return;
                }
            }
        }

        runWhenBranchesAreUpToDate(new Runnable() {

            public void run() {
                writeCommand(COMMAND_CONNECTION_READY);
            }
        });
    }

    //

    public int getPendingSendCount() {
        return _pendingSends.get();
    }

    public void addObjectToSend(final Object object) {
        int count = _pendingSends.incrementAndGet();

        if (count >= OverloadHandler.getInstance().getPendingSendsThreshold())
            OverloadHandler.getInstance().onPendingSendsThresholdReached(getEndpoint().getConnection());

        getEndpoint().enqueueOnWriterThread(new Command() {

            public DistributedWriter getWriter() {
                return ControllerOutWriter.this;
            }

            public void run() {
                send(object);
            }
        });
    }

    @SuppressWarnings("fallthrough")
    private void send(Object object) {
        WriteStep step = WriteStep.OBJECT;

        if (interrupted())
            step = (WriteStep) resume();

        if (Debug.ENABLED)
            Debug.assertion(getBranch() == null && getListener() == null && getSnapshot() == null);

        switch (step) {
            case OBJECT: {
                writeCommand(COMMAND_OBJECT_SENT);

                if (interrupted()) {
                    interrupt(WriteStep.OBJECT);
                    return;
                }
            }
            case COMMAND: {
                writeObject(object);

                if (interrupted()) {
                    interrupt(WriteStep.COMMAND);
                    return;
                }
            }
        }

        if (object instanceof TObject) {
            runWhenBranchesAreUpToDate(new Runnable() {

                public void run() {
                    writeCommand(COMMAND_OBJECT_READY);

                    if (!interrupted())
                        _pendingSends.decrementAndGet();
                }
            });
        } else
            _pendingSends.decrementAndGet();
    }

    public void sendHeartbeat() {
        getEndpoint().enqueueOnWriterThread(new Command() {

            public DistributedWriter getWriter() {
                return ControllerOutWriter.this;
            }

            public void run() {
                writeCommand(COMMAND_HEARTBEAT);
            }
        });
    }

    // Debug

    @Override
    void assertIdle() {
        super.assertIdle();

        getEndpoint().assertCommandsDone();
        getEndpoint().assertNoPendingSnapshots();

        Debug.assertion(_pendingSends.get() == 0);
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
            case COMMAND_CONNECTION_SENT:
                return "CONNECTION_SENT";
            case COMMAND_CONNECTION_READY:
                return "CONNECTION_READY";
            case COMMAND_OBJECT_SENT:
                return "OBJECT_SENT";
            case COMMAND_OBJECT_READY:
                return "OBJECT_READY";
            case COMMAND_HEARTBEAT:
                return "HEARTBEAT";
            default:
                return null;
        }
    }
}
