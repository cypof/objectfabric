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


import of4gwt.Interceptor;
import of4gwt.TObjectWriter;
import of4gwt.Transaction;
import of4gwt.Connection.Endpoint;
import of4gwt.DistributedWriter.Command;
import of4gwt.Transaction.CommitStatus;
import of4gwt.misc.Debug;
import of4gwt.misc.ThreadAssert.SingleThreaded;

/**
 * TODO: allows parallel intercepting connection (only one interception at a time so no
 * need to identify which transaction is being sent?).
 */
@SingleThreaded
final class InterceptorReader extends DistributedReader {

    private final InterceptorWriter _writer;

    public InterceptorReader(Endpoint endpoint, InterceptorWriter writer) {
        super(endpoint);

        _writer = writer;
    }

    private enum Steps {
        CODE, COMMAND
    }

    @SuppressWarnings("fallthrough")
    @Override
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
                        case PropagatorWriter.COMMAND_COMMIT: {
                            validateWrites(getBranch(), null);
                            getEndpoint().registerNewObjects();
                            propagateStandalone();

                            if (Debug.ENABLED)
                                assertIdle();

                            break;
                        }
                        case PropagatorWriter.COMMAND_ACK_INTERCEPTION: {
                            if (!canReadByte()) {
                                interruptByte(code);
                                interrupt(Steps.COMMAND);
                                return 0;
                            }

                            byte interceptionId = readByte();
                            Interceptor.ack(getBranch(), interceptionId, true);
                            break;
                        }
                        case PropagatorWriter.COMMAND_NACK_INTERCEPTION: {
                            final Transaction branch = getBranch();
                            final InterceptorWriter writer = _writer;

                            getEndpoint().enqueueOnWriterThread(new Command() {

                                public DistributedWriter getWriter() {
                                    return writer;
                                }

                                public void run() {
                                    writer.writeCommandInBranch(branch, InterceptorWriter.COMMAND_UNBLOCK);

                                    if (!writer.interrupted())
                                        Interceptor.nack(branch, CommitStatus.CONFLICT, null);
                                }
                            });

                            break;
                        }
                        case PropagatorWriter.COMMAND_NEXT_INTERCEPTION_SNAPSHOT: {
                            final Transaction branch = getBranch();
                            final InterceptorWriter writer = _writer;

                            getEndpoint().enqueueOnWriterThread(new Runnable() {

                                public void run() {
                                    writer.runWhenBranchesAreUpToDate(new Runnable() {

                                        public void run() {
                                            writer.writeCommandInBranch(branch, InterceptorWriter.COMMAND_NEXT_SNAPSHOT_ACK);
                                        }
                                    });
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
        String value = PropagatorWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
