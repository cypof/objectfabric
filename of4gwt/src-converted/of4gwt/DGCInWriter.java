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
final class DGCInWriter extends DistributedWriter {

    public static final byte COMMAND_ABOUT_TO_DISCONNECT = DistributedWriter.MAX_COMMAND_ID + 1;

    public DGCInWriter(Endpoint endpoint) {
        super(endpoint);
    }

    private enum Step {
        COMMAND, OBJECT
    }

    @SuppressWarnings("fallthrough")
    public final void writeAboutToDisconnect(Version shared) {
        Step step = Step.COMMAND;

        if (interrupted())
            step = (Step) resume();

        switch (step) {
            case COMMAND: {
                writeCommand(COMMAND_ABOUT_TO_DISCONNECT);

                if (interrupted()) {
                    interrupt(Step.COMMAND);
                    return;
                }
            }
            case OBJECT: {
                writeTObject(shared);

                if (interrupted()) {
                    interrupt(Step.OBJECT);
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
            case COMMAND_ABOUT_TO_DISCONNECT:
                return "ABOUT_TO_DISCONNECT";
            default:
                return null;
        }
    }
}
