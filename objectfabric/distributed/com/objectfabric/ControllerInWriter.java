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
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class ControllerInWriter extends DistributedWriter {

    public static final byte COMMAND_LOCAL_SITE = MAX_COMMAND_ID + 1;

    public ControllerInWriter(Endpoint endpoint) {
        super(endpoint);

        setBranch(Site.getLocal().getTrunk());
    }

    //

    /**
     * No need to snapshot objects for first connection. Data will be sent when with each
     * channel.
     */
    @SuppressWarnings({ "fallthrough" })
    public void writeLocalSite() {
        if (Debug.ENABLED) {
            Debug.assertion(getBranch() == Site.getLocal().getTrunk());
            Debug.assertion(getListener() == null && getSnapshot() == null);
        }

        pushTObject(Site.getLocal());

        if (!interrupted()) {
            runWhenBranchesAreUpToDate(new Runnable() {

                public void run() {
                    writeCommand(COMMAND_LOCAL_SITE);

                    if (!interrupted()) {
                        if (Debug.ENABLED)
                            Helper.getInstance().setNoTransaction(false);

                        getEndpoint().getConnection().onDialogEstablished();

                        if (Debug.ENABLED)
                            Helper.getInstance().setNoTransaction(true);

                        if (Debug.COMMUNICATIONS) {
                            int count = popStack(1);
                            // Otherwise stack would be reversed on other side
                            Debug.assertion(count == 0);
                        }
                    }
                }
            });
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
            case COMMAND_LOCAL_SITE:
                return "LOCAL_SITE";
            default:
                return null;
        }
    }
}
