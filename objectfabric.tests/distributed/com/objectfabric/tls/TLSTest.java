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

package com.objectfabric.tls;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Test;

import com.objectfabric.Privileged;
import com.objectfabric.TestsHelper;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.filters.TLS;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

public class TLSTest extends Privileged {

    private SocketServer _server;

    public TLSTest() {
    }

    @Test
    public void run1() throws Exception {
        run(1);
    }

    @Test
    public void run8() throws Exception {
        run(8);
    }

    private void run(int clientCount) throws Exception {
        // System.setProperty("javax.net.debug", "all");

        if (Debug.ENABLED)
            Debug.ProcessName = "Server";

        SimpleObjectModel.register();

        _server = new SocketServer(4444);
        _server.addFilter(new TLS(createSSLContext()));

        final ArrayList<SeparateClassLoader> clients = new ArrayList<SeparateClassLoader>();
        final AtomicInteger count = new AtomicInteger();

        _server.setCallback(new Callback<SocketConnection>() {

            public void onConnection(SocketConnection session) {
            }

            public void onDisconnection(SocketConnection session, Exception e) {
            }

            public void onReceived(SocketConnection session, Object object) {
                if (count.incrementAndGet() == clients.size())
                    for (SeparateClassLoader client : clients)
                        client.interrupt();
            }
        });

        _server.start();

        for (int i = 0; i < clientCount; i++) {
            SeparateClassLoader client = new SeparateClassLoader("Client Thread " + (i + 1), TLSTestClient.class.getName(), true);
            client.setArgTypes(int.class);
            client.setArgs(i + 1);
            clients.add(client);
            client.start();
        }

        for (SeparateClassLoader client : clients) {
            client.join();
            client.close();
        }

        TestsHelper.assertMemory("All done");

        _server.stop();

        PlatformAdapter.reset();
    }

    static final SSLContext createSSLContext() throws Exception {
        /*
         * Open a secure socket server by loading the server key store and creating a
         * SSLContext that will be used by the ObjectFabric transport to create its
         * SSLEngine and accept TLS or SSL connections.
         */
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = "passphrase".toCharArray();
        ks.load(new FileInputStream("./distributed/com/objectfabric/tls/keystore.jks"), passphrase);
        ts.load(new FileInputStream("./distributed/com/objectfabric/tls/keystore.jks"), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ssl;
    }

    public static void main(String[] args) throws Exception {
        TLSTest test = new TLSTest();

        for (int i = 0; i < 10; i++)
            test.run8();
    }
}
