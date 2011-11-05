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

package chat;

import chat.generated.ObjectModel;
import chat.generated.User;

import com.objectfabric.TMap;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

public class Server {

    private static final int PORT = 4444;

    public static SocketServer run(boolean waitForEnter) throws Exception {
        ObjectModel.register();

        final TMap<String, User> users = new TMap<String, User>();

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
                session.send(users);
            }

            public void onDisconnection(SocketConnection session, Exception e) {
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
}
