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

package finance;


import com.objectfabric.misc.Log;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketServer;
import com.objectfabric.transports.socket.SocketServer.Session;

import finance.generated.ObjectModel;

public class Server {

    public static void main(String[] args) throws Exception {
        ObjectModel.register();

        System.out.println("Creating a market");

        final MarketImpl market = new MarketImpl();

        System.out.println("Starting server");

        SocketServer server = new SocketServer(4444);

        server.setCallback(new Callback<Session>() {

            public void onConnection(Session session) {
                // Share market between clients
                session.send(market);
            }

            public void onDisconnection(Session session, Exception e) {
            }

            public void onReceived(Session session, Object object) {
            }
        });

        server.start();

        Log.write("Started a server on port " + server.getPort());
        Thread.sleep(Long.MAX_VALUE);
    }
}
