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

import com.objectfabric.Site;
import com.objectfabric.TArrayTObject;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;
import com.objectfabric.vm.generated.MethodsObjectModel;
import com.objectfabric.vm.generated.SimpleMethod;

/**
 * Needs to run on another thread, otherwise the thread locals map keeps references to the
 * class loader and it doesn't GC. C.f. VMTest<X>.
 */
public class VMMethodsServer extends TestsHelper {

    public static void main(final int granularity, int testNumber, final int serverCalls, int clientCalls, int objectCount, int clientCount) throws Exception {
        Debug.ProcessName = "Server";
        Debug.AssertNoConflict = true;

        Log.write("");
        Log.write("Starting MethodsServer: " + Granularity.values()[granularity] + ", serverCalls: " + serverCalls + ", clientCalls: " + clientCalls + ", objectsCount: " + objectCount + ", clientCount: " + clientCount);

        MethodsObjectModel.register();
        Transaction trunk;

        if (granularity == Granularity.ALL.ordinal())
            trunk = Site.getLocal().createTrunk(Granularity.ALL);
        else
            trunk = Site.getLocal().getTrunk();

        Transaction.setDefaultTrunk(trunk);

        final TArrayTObject<SimpleMethod> array = new TArrayTObject<SimpleMethod>(objectCount);

        for (int i = 0; i < array.length(); i++)
            array.set(i, new SimpleMethodImpl());

        VMServer server = new VMServer(array);

        ArrayList<VMConnection> connections = new ArrayList<VMConnection>();

        for (int i = 0; i < clientCount; i++) {
            VMConnection connection = server.createConnection();
            connections.add(connection);
        }

        ArrayList<SeparateClassLoader> clients = new ArrayList<SeparateClassLoader>();

        for (int i = 0; i < clientCount; i++) {
            SeparateClassLoader client = new SeparateClassLoader(VMMethodsClient.class.getName());
            client.setArgTypes(int.class, int.class, int.class);
            client.setArgs(i, testNumber, clientCalls);
            client.run();
            clients.add(client);
            connections.get(i).setClassLoader(client);
        }

        while (server.getSessions().size() == 0) {
            for (int i = 0; i < clientCount; i++) {
                VMConnection c = connections.get(i);
                c.setLength(c.transfer(c.getBuffer(), c.length()));
                SeparateClassLoader client = c.getClassLoader();
                c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class }, c.getBuffer(), c.length()));
            }
        }

        // TODO make server side calls

        while (server.getSessions().size() > 0) {
            for (int i = connections.size() - 1; i >= 0; i--) {
                VMConnection c = connections.get(i);

                if (c.length() != VMConnection.EXIT) {
                    Debug.ProcessName = "Session " + i;
                    c.setLength(c.transfer(c.getBuffer(), c.length()));
                    Debug.ProcessName = "Server";

                    SeparateClassLoader client = c.getClassLoader();
                    c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class }, c.getBuffer(), c.length()));

                    if (c.length() == VMConnection.EXIT)
                        c.close();
                }
            }
        }

        server.stop();

        Debug.ProcessName = "";
        Debug.AssertNoConflict = false;

        for (SeparateClassLoader client : clients)
            client.close();

        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
        PlatformAdapter.shutdown();
    }
}
