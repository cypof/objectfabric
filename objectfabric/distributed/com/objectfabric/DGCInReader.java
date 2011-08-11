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


import com.objectfabric.TObjectWriter;
import com.objectfabric.Connection.Endpoint;
import com.objectfabric.Connection.Endpoint.Status;
import com.objectfabric.DistributedWriter.Command;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class DGCInReader extends DistributedReader {

    private final DGCInWriter _writer;

    public DGCInReader(Endpoint endpoint, DGCInWriter writer) {
        super(endpoint);

        _writer = writer;
    }

    private enum Steps {
        CODE, COMMAND
    }

    @Override
    @SuppressWarnings({ "fallthrough" })
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
                        case DGCOutWriter.COMMAND_GCED: {
                            final Version shared = readTObjectShared();

                            if (interrupted()) {
                                interruptByte(code);
                                interrupt(Steps.COMMAND);
                                return 0;
                            }

                            final Endpoint endpoint = getEndpoint();
                            final DGCInWriter writer = _writer;

                            endpoint.enqueueOnWriterThread(new Command() {

                                public DistributedWriter getWriter() {
                                    return writer;
                                }

                                public void run() {
                                    Status status = endpoint.getStatus(shared);

                                    if (status == Status.SNAPSHOTTED) {
                                        endpoint.setStatus(shared, Status.GCED);
                                        writer.writeAboutToDisconnect(shared);
                                    }
                                }
                            });

                            break;
                        }
                        case DGCOutWriter.COMMAND_ACK_DISCONNECT: {
                            final Version shared = readTObjectShared();

                            if (interrupted()) {
                                interruptByte(code);
                                interrupt(Steps.COMMAND);
                                return 0;
                            }

                            final Endpoint endpoint = getEndpoint();

                            endpoint.enqueueOnWriterThread(new Runnable() {

                                public void run() {
                                    endpoint.addPendingDisconnection(shared);
                                }
                            });
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

    @Override
    protected String getCommandString(byte command) {
        String value = DGCOutWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
