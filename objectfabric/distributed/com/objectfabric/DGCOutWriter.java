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
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class DGCOutWriter extends DistributedWriter {

    public static final byte COMMAND_GCED = DistributedWriter.MAX_COMMAND_ID + 1;

    public static final byte COMMAND_ACK_DISCONNECT = DistributedWriter.MAX_COMMAND_ID + 2;

    public DGCOutWriter(Endpoint endpoint) {
        super(endpoint);
    }

    public void onGarbageCollected(final Version shared) {
        getEndpoint().enqueueOnWriterThread(new Command() {

            public MultiplexerWriter getWriter() {
                return DGCOutWriter.this;
            }

            public void run() {
                writeGCed(shared);
            }
        });
    }

    private enum WriteGCedStep {
        COMMAND, OBJECT
    }

    @SuppressWarnings("fallthrough")
    private final void writeGCed(Version shared) {
        WriteGCedStep step = WriteGCedStep.COMMAND;

        if (interrupted())
            step = (WriteGCedStep) resume();

        switch (step) {
            case COMMAND: {
                writeCommand(COMMAND_GCED);

                if (interrupted()) {
                    interrupt(WriteGCedStep.COMMAND);
                    return;
                }
            }
            case OBJECT: {
                writeTObject(shared);

                if (interrupted()) {
                    interrupt(WriteGCedStep.OBJECT);
                    return;
                }
            }
        }
    }

    private enum WriteAckDisconnectStep {
        COMMAND, OBJECT
    }

    @SuppressWarnings("fallthrough")
    public final void writeAckDisconnect(Version shared) {
        WriteAckDisconnectStep step = WriteAckDisconnectStep.COMMAND;

        if (interrupted())
            step = (WriteAckDisconnectStep) resume();

        switch (step) {
            case COMMAND: {
                writeCommand(COMMAND_ACK_DISCONNECT);

                if (interrupted()) {
                    interrupt(WriteAckDisconnectStep.COMMAND);
                    return;
                }
            }
            case OBJECT: {
                writeTObject(shared);

                if (interrupted()) {
                    interrupt(WriteAckDisconnectStep.OBJECT);
                    return;
                }
            }
        }
    }

    // Debug

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
            case COMMAND_GCED:
                return "GCED";
            case COMMAND_ACK_DISCONNECT:
                return "ACK_DISCONNECT";
            default:
                return null;
        }
    }
}
