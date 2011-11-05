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

package cassandra;

import images.generated.ImageInfo;
import images.generated.ImagesObjectModel;

import com.objectfabric.CassandraStore;
import com.objectfabric.Site;
import com.objectfabric.TSet;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Log;
import com.objectfabric.transports.Server;
import com.objectfabric.transports.http.HTTP;
import com.objectfabric.transports.socket.SocketServer;
import com.objectfabric.transports.socket.SocketServer.Session;

/**
 * This sample stores data in the Cassandra NoSQL store. It starts a server and waits for
 * connections from the Images sample (part09.images.Images). Once a client connects and
 * images are dragged, their positions are sent in real-time to Cassandra.
 */
public class Main {

    private static final int PORT = 4444;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        /*
         * Registers Images sample data model, to allow serialization of images positions.
         */
        ImagesObjectModel.register();

        /*
         * Opens a connection to a Cassandra store.
         */
        CassandraStore store = new CassandraStore("localhost", 9160);

        /*
         * Create a trunk (partition), set it as default so that objects we create further
         * are part of it, and start the store.
         */
        Transaction trunk = Site.getLocal().createTrunk(store);
        Transaction.setDefaultTrunk(trunk);
        store.start(trunk);

        /*
         * Read existing root in case the set of images has already been created.
         */
        Object root = store.getRoot();
        final TSet images;

        if (root != null)
            images = (TSet<ImageInfo>) root;
        else {
            /*
             * Otherwise create a new set of images.
             */
            images = new TSet<ImageInfo>(ImageInfo.TYPE);
            store.setRoot(images);
        }

        /*
         * Accept connections from Images sample clients.
         */
        final SocketServer server = new SocketServer(PORT);

        /*
         * This sample is multi-platform, allow connection over http.
         */
        server.addFilter(new HTTP());

        server.setCallback(new Server.Callback<Session>() {

            public void onConnection(Session session) {
                Log.write("Connection from " + session.getRemoteAddress());

                // Send image set to client
                session.send(images);
            }

            public void onDisconnection(Session session, Exception e) {
                Log.write("Disconnection from " + session.getRemoteAddress());
            }

            public void onReceived(Session session, Object object) {
            }
        });

        server.start();

        Log.write("Started a server on port " + server.getPort());
        Thread.sleep(Long.MAX_VALUE);
    }
}
