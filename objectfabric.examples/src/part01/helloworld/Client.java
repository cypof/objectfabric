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

package part01.helloworld;

import java.io.IOException;

import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.socket.SocketClient;

/**
 * Exchanges text with a server. Other versions of this client allow connections to the
 * same server from GWT (Check project of4gwt.examples.helloworld) and .NET
 * (of4dotnet.examples.HelloWorld).
 */
public class Client {

    public static void main(String[] args) throws IOException {
        /*
         * Connect to the server through a socket connection.
         */
        final SocketClient client = new SocketClient("localhost", 8080);

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                System.out.println("Received: " + object);

                // Send back the response
                client.send(object + " World!");
            }

            public void onDisconnected(Throwable t) {
            }
        });

        client.connect();

        System.out.println("Connected, press enter to exit.");
        PlatformConsole.readLine();
    }
}
