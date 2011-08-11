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
import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Client;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.http.HTTPClient;
import com.objectfabric.transports.socket.SocketClient;

public class ConnectionTestClient extends Privileged {

    public static final int START = 0;

    public static final int CONNECTED = 1;

    public static final int GO = 2;

    public static final int DONE = 3;

    public static void main(final AtomicInteger progress, int number, final boolean loop, int transport) throws IOException {
        Debug.ProcessName = "Client " + number;

        if (loop)
            SimpleObjectModel.register();

        final Client client;

        if (transport == ConnectionTest.SOCKET)
            client = new SocketClient("localhost", 4444);
        else
            client = new HTTPClient(new URL("http://localhost:4444"));

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                SimpleClass simple = null;

                if (loop)
                    simple = (SimpleClass) object;
                else
                    Debug.assertAlways(object.equals("blah"));

                progress.set(CONNECTED);

                SeparateClassLoader.waitForProgress(progress, GO);

                if (loop) {
                    Future<CommitStatus> future = ConcurrentClient.loop(simple, 0, 10, 0);

                    try {
                        future.get();
                    } catch (Exception e) {
                    }
                }

                progress.set(DONE);
                client.close();
            }

            public void onDisconnected(Throwable t) {
            }
        });

        client.connectAsync();
        SeparateClassLoader.waitForProgress(progress, DONE);
        client.close();
        NIOManager.getInstance().close();
        PlatformAdapter.shutdown();
    }
}
