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

package part07.methods;

import part07.methods.generated.MyClass;
import part07.methods.generated.ObjectModel;

import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.socket.SocketClient;

public class Client {

    public static void main(String[] args) throws Exception {
        ObjectModel.register();

        final SocketClient client = new SocketClient("localhost", 4444);

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                System.out.println("Received: " + object);

                /*
                 * Invokes method on server.
                 */
                MyClass myObject = (MyClass) object;
                myObject.start("Blah");

                /*
                 * Invocation can be asynchronous.
                 */
                myObject.stopAsync(new AsyncCallback<Void>() {

                    public void onSuccess(Void result) {
                        client.send("Done!");
                    }

                    public void onFailure(Exception e) {
                    }
                });
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
