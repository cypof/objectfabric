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

package part03.replication;

import org.junit.Test;

import part03.replication.generated.MyClass;
import part03.replication.generated.MyObjectModel;

import com.objectfabric.FieldListener;
import com.objectfabric.KeyListener;
import com.objectfabric.TMap;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

/**
 * If an object sent to another machine derives from TObject, any change made to it will
 * be replicated to the other replica. Generated objects and ObjectFabric collections
 * (e.g. TList) derive from TObject.
 */
public class Server {

    private static final int PORT = 4444;

    public static SocketServer run(boolean waitForEnter) throws Exception {
        /*
         * Register your generated object model so ObjectFabric can deserialize your
         * objects.
         */
        MyObjectModel.register();

        /*
         * Create a generated object.
         */
        final MyClass myObject = new MyClass();

        /*
         * Register a listener to log updates made by clients.
         */
        myObject.addListener(new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                if (fieldIndex == MyClass.TEXT_INDEX)
                    System.out.println("Text has changed on shared object, new value is: " + myObject.getText());
            }
        });

        /*
         * Create a collection.
         */
        final TMap<String, MyClass> map = new TMap<String, MyClass>();

        /*
         * Register a listener to log updates made by clients.
         */
        map.addListener(new KeyListener<String>() {

            public void onPut(String key) {
                System.out.println("An entry has been added to shared map: [" + key + ", " + map.get(key) + "]");

                if (_client != null && key.equals("key"))
                    _client.interrupt();
            }

            public void onRemoved(String key) {
                System.out.println("An entry has been removed from shared map: " + key);
            }

            public void onCleared() {
                System.out.println("Shared map has been cleared.");
            }
        });

        /*
         * Creates a socket server which shares an object.
         */
        SocketServer server = new SocketServer(PORT);

        /*
         * Listen for incoming connections and objects sent from clients.
         */
        server.setCallback(new Callback<SocketConnection>() {

            public void onConnection(SocketConnection session) {
                System.out.println("Connection from " + session.getRemoteAddress());

                /*
                 * Send objects to client.
                 */
                session.send(myObject);
                session.send(map);
            }

            public void onDisconnection(SocketConnection session, Throwable t) {
                System.out.println("Disconnection from " + session.getRemoteAddress());
            }

            public void onReceived(SocketConnection session, Object object) {
                System.out.println("Received: " + object);
            }
        });

        /*
         * Start the server.
         */
        server.start();

        if (waitForEnter) {
            System.out.println("Started socket server, press enter to exit.");
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
