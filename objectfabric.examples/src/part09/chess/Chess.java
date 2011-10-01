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

package part09.chess;

import com.objectfabric.KeyListener;
import com.objectfabric.TArrayDouble;
import com.objectfabric.TMap;
import com.objectfabric.misc.Log;
import com.objectfabric.transports.Server;
import com.objectfabric.transports.http.HTTP;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

/**
 * This sample replicates chess pieces positions through a socket connection.
 */
public class Chess {

    private TMap<String, TArrayDouble> _games = new TMap<String, TArrayDouble>();

    @SuppressWarnings("unchecked")
    public void run() throws Exception {
        _games.addListener(new KeyListener<String>() {

            public void onPut(String key) {
                Log.write("onPut(" + key + ")");
            }

            public void onRemoved(String key) {
            }

            public void onCleared() {
            }
        });

        final SocketServer server = new SocketServer(8080);

        /*
         * This sample is multi-platform, allow connection over http.
         */
        server.addFilter(new HTTP());

        server.setCallback(new Server.Callback<SocketConnection>() {

            public void onConnection(SocketConnection session) {
                Log.write("Connection from " + session.getRemoteAddress());

                // Send shared image set to client
                session.send(_games);
            }

            public void onDisconnection(SocketConnection session, Exception e) {
                Log.write("Disconnection from " + session.getRemoteAddress());
            }

            public void onReceived(SocketConnection session, Object object) {
            }
        });

        server.start();

        Log.write("Started a socket server on port " + server.getPort());
        Thread.sleep(Long.MAX_VALUE);
    }

    public static void main(String[] args) throws Exception {
        Chess main = new Chess();
        main.run();
    }
}
