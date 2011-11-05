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
import java.util.ArrayList;

import org.junit.Test;

import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.http.HTTP;
import com.objectfabric.transports.socket.SocketServer;

public class ConnectionTest extends Privileged {

    public static final int SOCKET = 0;

    public static final int HTTP = 1;

    private SocketServer _server;

    public ConnectionTest() {
    }

    @Test
    public void socket1() throws IOException {
        run(1, false, SOCKET);
    }

    @Test
    public void socket8() throws IOException {
        run(8, false, SOCKET);
    }

    @Test
    public void socketLoop1() throws IOException {
        run(1, true, SOCKET);
    }

    @Test
    public void socketLoop8() throws IOException {
        run(8, true, SOCKET);
    }

    @Test
    public void http1() throws IOException {
        run(1, false, HTTP);
    }

    @Test
    public void http8() throws IOException {
        run(8, false, HTTP);
    }

    @Test
    public void httpLoop1() throws IOException {
        run(1, true, HTTP);
    }

    @Test
    public void httpLoop8() throws IOException {
        run(8, true, HTTP);
    }

    private void run(int clientCount, boolean loop, int transport) throws IOException {
        Debug.ProcessName = "Server";
        final Object share;

        if (loop) {
            SimpleObjectModel.register();
            share = new SimpleClass();
        } else
            share = "blah";

        _server = new SocketServer(4444);
        _server.addFilter(new HTTP());

        _server.setCallback(new Callback<SocketServer.Session>() {

            public void onConnection(SocketServer.Session session) {
                session.send(share);
            }

            public void onDisconnection(SocketServer.Session session, Exception e) {
            }

            public void onReceived(SocketServer.Session session, Object object) {
            }
        });

        _server.start();

        ArrayList<SeparateClassLoader> clients = new ArrayList<SeparateClassLoader>();

        for (int i = 0; i < clientCount; i++) {
            SeparateClassLoader client = new SeparateClassLoader("Client Thread " + (i + 1), ConnectionTestClient.class.getName(), true);
            client.setArgTypes(int.class, boolean.class, int.class);
            client.setArgs(i + 1, loop, transport);
            clients.add(client);
            client.start();
        }

        for (SeparateClassLoader client : clients)
            client.waitForProgress(ConnectionTestClient.CONNECTED);

        for (SeparateClassLoader client : clients)
            client.getProgress().set(ConnectionTestClient.GO);

        TestsHelper.assertMemory("All connected");

        for (SeparateClassLoader client : clients)
            client.waitForProgress(ConnectionTestClient.DONE);

        TestsHelper.assertMemory("All done");

        _server.stop();

        Debug.ProcessName = "";
        PlatformAdapter.reset();

        for (SeparateClassLoader client : clients)
            client.close();
    }

    public static void main(String[] args) throws Exception {
        ConnectionTest test = new ConnectionTest();

        for (int i = 0; i < 100; i++)
            test.run(1, true, HTTP);
    }
}
