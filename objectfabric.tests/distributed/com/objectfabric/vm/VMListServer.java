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

package com.objectfabric.vm;

import java.util.ArrayList;

import com.objectfabric.TList;
import com.objectfabric.TestsHelper;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;

/**
 * Needs to run on another thread, otherwise the thread locals map keeps references to the
 * class loader and it doesn't GC. C.f. VMServerTestLoader.
 */
@SuppressWarnings("unchecked")
public class VMListServer extends TestsHelper {

    public static final int CLIENTS_COUNT = 1;

    public static void main(String[] args) throws Exception {
        Debug.ProcessName = "Server";

        // Transaction.createTrunk(Consistency.LAST_WRITE_WINS);

        TList pair = new TList();
        pair.add(new TList());
        pair.add(new TList());

        VMServer server = new VMServer(pair);

        ArrayList<VMConnection> connections = new ArrayList<VMConnection>();

        for (int i = 0; i < CLIENTS_COUNT; i++) {
            VMConnection connection = server.createConnection();
            connections.add(connection);
        }

        ArrayList<SeparateClassLoader> clients = new ArrayList<SeparateClassLoader>();

        for (int i = 0; i < CLIENTS_COUNT; i++) {
            SeparateClassLoader client = new SeparateClassLoader(VMListClient.class.getName());
            client.setArgs(new Object[] { new String[] { "" + i } });
            client.run();
            clients.add(client);
        }

        while (server.getSessions().size() < CLIENTS_COUNT) {
            for (int i = 0; i < CLIENTS_COUNT; i++) {
                VMConnection c = connections.get(i);
                c.setLength(c.transfer(c.getBuffer(), c.length()));
                c.setLength((Integer) clients.get(i).invoke("transfer", new Class[] { byte[].class, int.class }, c.getBuffer(), c.length()));
            }
        }

        while (server.getSessions().size() > 0) {
            for (int i = connections.size() - 1; i >= 0; i--) {
                VMConnection c = connections.get(i);
                c.setLength(connections.get(i).transfer(c.getBuffer(), c.length()));
                c.setLength((Integer) clients.get(i).invoke("transfer", new Class[] { byte[].class, int.class }, c.getBuffer(), c.length()));

                if (c.length() == VMConnection.EXIT)
                    connections.remove(i).close();
            }
        }

        server.stop();

        for (SeparateClassLoader client : clients)
            client.close();

        PlatformAdapter.reset();
    }
}
