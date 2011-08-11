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

package part10.trading;

import part10.trading.generated.TradingObjectModel;

import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

public class TradingServer {

    public static void main(String[] args) throws Exception {
        TradingObjectModel.register();

        System.out.println("Creating a market");

        final MarketImpl market = new MarketImpl();

        System.out.println("Starting server");

        SocketServer server = new SocketServer(4444);

        server.setCallback(new Callback<SocketConnection>() {

            public void onConnection(SocketConnection session) {
                // Share market between clients
                session.send(market);
            }

            public void onDisconnection(SocketConnection session, Throwable t) {
            }

            public void onReceived(SocketConnection session, Object object) {
            }
        });

        server.start();

        System.out.println("Press enter to continue");
        PlatformConsole.readLine();
    }
}
