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

import java.io.IOException;

import part03.replication.generated.MyClass;
import part03.replication.generated.MyObjectModel;

import com.objectfabric.TMap;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.socket.SocketClient;

/**
 * This sample replicates an object through a socket connection.
 */
public class Client {

    public static void main(String[] args) throws IOException {
        /*
         * Register your generated object model so ObjectFabric can deserialize your
         * objects.
         */
        MyObjectModel.register();

        /*
         * Connect to the server through a socket connection.
         */
        SocketClient client = new SocketClient("localhost", 4444);

        client.setCallback(new Callback() {

            /*
             * Update objects received from server, changes will be replicated and invoke
             * listeners on the server.
             */
            public void onReceived(Object object) {
                System.out.println("Received: " + object);

                if (object instanceof MyClass) {
                    MyClass myObject = (MyClass) object;
                    myObject.setText("New text.");
                    System.out.println("Updated myObject.");
                }

                if (object instanceof TMap) {
                    @SuppressWarnings("unchecked")
                    TMap<String, MyClass> map = (TMap) object;
                    map.put("key", new MyClass());
                    System.out.println("Added map entry.");
                }
            }

            public void onDisconnected(Exception e) {
            }
        });

        client.connect();

        if (SeparateClassLoader.isTest()) // Testing purposes
            SeparateClassLoader.waitForInterruption(client);
        else {
            System.out.println("Connected, press enter to exit.");
            PlatformConsole.readLine();
        }
    }
}
