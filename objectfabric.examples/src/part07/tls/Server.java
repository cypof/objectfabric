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

package part07.tls;

import javax.net.ssl.SSLContext;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.filters.TLS;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

/**
 * This example shows how to use the TLS (SSL) filter to enable secure connections.
 * ObjectFabric uses the standard Java SSLEngine.
 */
public class Server {

    public static SocketServer run(boolean waitForEnter) throws Exception {
        SocketServer server = new SocketServer(8443);

        server.setCallback(new Callback<SocketConnection>() {

            public void onConnection(SocketConnection session) {
                System.out.println("Connection from " + session.getRemoteAddress());
            }

            public void onDisconnection(SocketConnection session, Exception e) {
                System.out.println("Disconnection from " + session.getRemoteAddress());
            }

            public void onReceived(SocketConnection session, Object object) {
                Assert.assertEquals("Done!", object);

                if (_client != null)
                    _client.interrupt();
            }
        });

        SSLContext ssl = Shared.createSSLContext();
        server.addFilter(new TLS(ssl));
        server.start();

        if (waitForEnter) {
            System.out.println("Started secure socket server, press enter to exit.");
            PlatformConsole.readLine();
        }

        return server;
    }

    public static void main(String[] args) throws Exception {
        run(true);
    }

    // Testing purposes

    private static SeparateClassLoader _client;

    @Test
    public void asTest() throws Exception {
        SocketServer server = run(false);

        _client = new SeparateClassLoader(Client.class.getName());
        _client.start();
        _client.join();

        server.stop();
        PlatformAdapter.reset();
    }
}
