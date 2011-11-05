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

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.objectfabric.ConcurrentClient;
import com.objectfabric.Privileged;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.socket.SocketClient;

public class DistributedClient extends Privileged {

    public static final int START = 0;

    public static final int CONNECTED = 1;

    public static final int DONE = 2;

    public static final int EXIT = 3;

    public static int main(AtomicInteger progress, int clientNumber, int count, int flags) throws IOException {
        Debug.assertAlways((flags & ConcurrentClient.MERGE_BY_SOURCE) == 0);
        Debug.ProcessName = "Client";
        SimpleObjectModel.register();
        SocketClient client = new SocketClient("localhost", 4444);
        SimpleClass simple = (SimpleClass) client.connectAndWaitObject();

        progress.set(CONNECTED);

        if (count > 0) {
            Future<CommitStatus> future = ConcurrentClient.loop(simple, clientNumber, count, flags);

            try {
                future.get();
            } catch (Exception e) {
            }
        }

        progress.set(DONE);

        SeparateClassLoader.waitForProgress(progress, EXIT);

        client.close();
        PlatformAdapter.shutdown();
        return TestsHelper.getLocalAborts();
    }
}
