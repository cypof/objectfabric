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
import of4gwt.misc.Debug;
import of4gwt.misc.Queue;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class ControllerInReader extends DistributedReader {

    private final ControllerInWriter _writer;

    private final Queue<Object> _objects = new Queue<Object>();

    public ControllerInReader(Endpoint endpoint, ControllerInWriter writer) {
        super(endpoint);

        if (writer == null)
            throw new IllegalArgumentException();

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
                        case ControllerOutWriter.COMMAND_CONNECTION_SENT: {
                            readTObject(getEndpoint().getConnection());

                            if (interrupted()) {
                                interruptByte(code);
                                interrupt(Steps.COMMAND);
                                return 0;
                            }

                            break;
                        }
                        case ControllerOutWriter.COMMAND_CONNECTION_READY: {
                            if (Debug.ENABLED)
                                assertIdle();

                            final ControllerInWriter writer = _writer;

                            getEndpoint().enqueueOnWriterThread(new Command() {

                                public MultiplexerWriter getWriter() {
                                    return writer;
                                }

                                public void run() {
                                    writer.writeLocalSite();
                                }
                            });

                            break;
                        }
                        case ControllerOutWriter.COMMAND_OBJECT_SENT: {
                            Object object = readObject();

                            if (interrupted()) {
                                interruptByte(code);
                                interrupt(Steps.COMMAND);
                                return 0;
                            }

                            if (object instanceof TObject)
                                _objects.add(object);
                            else {
                                if (_objects.size() == 0)
                                    onObject(object);
                                else
                                    _objects.add(object); // Preserve order
                            }

                            break;
                        }
                        case ControllerOutWriter.COMMAND_OBJECT_READY: {
                            Object object = _objects.poll();

                            if (Debug.ENABLED)
                                Debug.assertion(object instanceof TObject);

                            onObject(object);

                            for (;;) {
                                object = _objects.peek();

                                if (object == null || object instanceof TObject)
                                    break;

                                onObject(_objects.poll());
                            }

                            break;
                        }
                        case ControllerOutWriter.COMMAND_HEARTBEAT:
                            break;
                        default:
                            throw new IllegalStateException();
                    }
                }
            }
        }
    }

    private final void onObject(Object object) {
        if (Debug.ENABLED)
            Helper.getInstance().setNoTransaction(false);

        getEndpoint().getConnection().onObject(object);

        if (Debug.ENABLED)
            Helper.getInstance().setNoTransaction(true);
    }

    static final void setConnected(Connection connection, Site target) {
        ConnectionBase.Version shared = (ConnectionBase.Version) connection.getSharedVersion_objectfabric();

        if (Debug.ENABLED) {
            Debug.assertion(shared._target == null);
            Debug.assertion(!shared.getBit(ConnectionBase.TARGET_INDEX));
        }

        shared._target = shared.mergeTObject(shared._target, target);
        shared.setBit(ConnectionBase.TARGET_INDEX);
    }

    // Debug

    @Override
    public String getCommandString(byte command) {
        String value = ControllerOutWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
