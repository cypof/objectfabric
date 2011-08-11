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
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class ControllerOutReader extends DistributedReader {

    public ControllerOutReader(Endpoint endpoint) {
        super(endpoint);
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
                        case ControllerInWriter.COMMAND_LOCAL_SITE: {
                            Site site = (Site) ((Version) poll()).getReference().get();

                            if (Debug.ENABLED)
                                assertIdle();

                            ControllerInReader.setConnected(getEndpoint().getConnection(), site);

                            if (Debug.ENABLED)
                                Helper.getInstance().setNoTransaction(false);

                            getEndpoint().getConnection().onDialogEstablished();

                            if (Debug.ENABLED)
                                Helper.getInstance().setNoTransaction(true);

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
    public String getCommandString(byte command) {
        String value = ControllerInWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
