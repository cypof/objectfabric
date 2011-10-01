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

package part12.users;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;

import part12.users.generated.ObjectModel;
import part12.users.generated.PrivateData;
import part12.users.generated.PublicData;

import com.objectfabric.LazyMap;
import com.objectfabric.TList;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.security.shiro.ShiroObjectModel;
import com.objectfabric.security.shiro.UsernamePasswordSession;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.socket.SocketClient;

public class Client {

    private static SocketClient _client;

    private static LazyMap<String, PublicData> _publicData;

    private static LazyMap<String, PrivateData> _privateData;

    private static final CountDownLatch _latch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        ObjectModel.register();
        ShiroObjectModel.register();

        _client = new SocketClient("localhost", 8080);

        _client.setCallback(new Callback() {

            @SuppressWarnings("unchecked")
            public void onReceived(Object object) {
                TList list = (TList) object;
                UsernamePasswordSession session = (UsernamePasswordSession) list.get(0);
                _publicData = (LazyMap) list.get(1);
                _privateData = (LazyMap) list.get(2);

                System.out.println("Connected to server as user1.");

                try {
                    readPublicData();
                    readPrivateData(false);
                    writePublicData(false);
                    writePrivateData(false);

                    session.login("user1", "password1");

                    readPublicData();
                    readPrivateData(true);
                    writePublicData(true);
                    writePrivateData(true);

                    session.logout();

                    readPublicData();
                    readPrivateData(false);
                    writePublicData(false);
                    writePrivateData(false);

                    _client.close();
                    _latch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            public void onDisconnected(Exception e) {
            }
        });

        _client.connect();

        if (SeparateClassLoader.isTest()) // Testing purposes
            _latch.await();
        else {
            System.out.println("Connected, press enter to exit.");
            PlatformConsole.readLine();
        }
    }

    /**
     * On security exceptions, server will close the connection.
     * 
     * @throws IOException
     */
    private static void reconnect() throws IOException {
        while (_client.getSocketChannel().isConnected())
            PlatformThread.sleep(1);

        System.out.println("Server has successfully closed the connection, reconnecting");
        _client = new SocketClient("localhost", 8080);
        _client.connect();
    }

    private static final void readPublicData() {
        System.out.println("Reading user1 public data.");
        _publicData.get("user1");

        System.out.println("Reading user2 public data.");
        _publicData.get("user2");
    }

    private static final void readPrivateData(boolean loggedIn) throws IOException {
        System.out.println("Reading user1 private data.");

        if (loggedIn) {
            PrivateData data = _privateData.get("user1");
            Assert.assertEquals(1, data.getData());
        } else {
            try {
                System.out.println("Reading private data while not logged in, should throw");
                Debug.expectException();
                _privateData.get("user1");
                Assert.fail();
            } catch (RuntimeException _) {
            }

            reconnect();
        }

        try {
            System.out.println("Reading user2 private data, should throw");
            Debug.expectException();
            _privateData.get("user2");
            Assert.fail();
        } catch (RuntimeException _) {
        }

        reconnect();
    }

    private static final void writePublicData(boolean loggedIn) throws IOException {
        System.out.println("Writing user1 public data.");

        if (loggedIn) {
            PublicData data = _publicData.get("user1");
            data.setData(42);
        } else {
            try {
                System.out.println("Writing public data while not logged in, should throw");
                Debug.expectException();
                PublicData data = _publicData.get("user1");
                data.setData(42);
                Assert.fail();
            } catch (RuntimeException _) {
            }

            reconnect();
        }

        try {
            System.out.println("Writing user2 public data, should throw");
            Debug.expectException();
            PublicData data = _publicData.get("user2");
            data.setData(42);
        } catch (RuntimeException _) {
        }

        reconnect();
    }

    private static final void writePrivateData(boolean loggedIn) throws IOException {
        System.out.println("Writing user1 private data.");

        if (loggedIn) {
            PrivateData data = _privateData.get("user1");
            data.setData(42);
        } else {
            try {
                System.out.println("Writing private data while not logged in, should throw");
                Debug.expectException();
                PrivateData data = _privateData.get("user1");
                data.setData(42);
                Assert.fail();
            } catch (RuntimeException _) {
            }

            reconnect();
        }

        try {
            System.out.println("Writing user2 private data, should throw");
            Debug.expectException();
            PrivateData data = _privateData.get("user2");
            data.setData(42);
        } catch (RuntimeException _) {
        }

        reconnect();
    }
}
