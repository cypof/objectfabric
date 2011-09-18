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
import of4gwt.MultiplexerWriter.Command;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class DGCOutReader extends DistributedReader {

    private final DGCOutWriter _writer;

    public DGCOutReader(Endpoint endpoint, DGCOutWriter writer) {
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
                        case DGCInWriter.COMMAND_ABOUT_TO_DISCONNECT: {
                            final Version shared = readTObjectShared();

                            if (interrupted()) {
                                interruptByte(code);
                                interrupt(Steps.COMMAND);
                                return 0;
                            }

                            final DGCOutWriter writer = _writer;

                            getEndpoint().enqueueOnWriterThread(new Command() {

                                public MultiplexerWriter getWriter() {
                                    return writer;
                                }

                                public void run() {
                                    if (shared.getReference().get() == null)
                                        writer.writeAckDisconnect(shared);
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
        String value = CallOutWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
