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

import junit.framework.Assert;

import com.objectfabric.Site;
import com.objectfabric.TArrayTObject;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.ReferencesClass;
import com.objectfabric.generated.ReferencesObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;

/**
 * Needs to run on another thread, otherwise the thread locals map keeps references to the
 * class loader and it doesn't GC. C.f. VMTest<X>.
 */
public class VMTest4Server extends TestsHelper {

    public static void main(int granularity, int serverGranularity, int clientGranularity, int clientCount) throws Exception {
        Debug.ProcessName = "Server";
        Debug.AssertNoConflict = true;

        Log.write("");
        Log.write("Starting VMTest4Server: " + Granularity.values()[granularity] + ", server: " + serverGranularity + ", client: " + clientGranularity + ", clientCount: " + clientCount);

        ReferencesObjectModel.register();
        Transaction trunk;

        if (granularity == Granularity.ALL.ordinal())
            trunk = Site.getLocal().createTrunk(Granularity.ALL);
        else
            trunk = Site.getLocal().getTrunk();

        ReferencesClass object = new ReferencesClass(trunk);
        object.setInt(42);

        if (serverGranularity >= 0) {
            Transaction trunk2 = Site.getLocal().createTrunk(Granularity.values()[serverGranularity]);
            ReferencesClass object2 = new ReferencesClass(trunk2);
            object2.setInt(43);
            object.setRef(object2);
        }

        if (clientGranularity >= 0)
            object.setArray(new TArrayTObject<ReferencesClass>(clientCount));

        VMServer server = new VMServer(object);

        ArrayList<VMConnection> connections = new ArrayList<VMConnection>();

        for (int i = 0; i < clientCount; i++) {
            VMConnection connection = server.createConnection();
            connections.add(connection);
        }

        ArrayList<SeparateClassLoader> clients = new ArrayList<SeparateClassLoader>();

        for (int i = 0; i < clientCount; i++) {
            SeparateClassLoader client = new SeparateClassLoader(VMTest4Client.class.getName());
            client.setArgTypes(int.class, int.class, int.class);
            client.setArgs(i, serverGranularity, clientGranularity);
            client.run();
            clients.add(client);
            connections.get(i).setClassLoader(client);
        }

        for (;;) {
            int count = 0;

            for (int i = 0; i < clientCount; i++) {
                VMConnection c = connections.get(i);
                c.setLength(c.transfer(c.getBuffer(), c.length()));
                SeparateClassLoader client = c.getClassLoader();
                c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class }, c.getBuffer(), c.length()));

                if ((Boolean) client.invoke("hasObject", new Class[0]))
                    count++;
            }

            if (count == clientCount)
                break;
        }

        while (server.getSessions().size() > 0) {
            for (int i = connections.size() - 1; i >= 0; i--) {
                VMConnection c = connections.get(i);

                if (c.length() != VMConnection.EXIT) {
                    c.setLength(c.transfer(c.getBuffer(), c.length()));
                    SeparateClassLoader client = c.getClassLoader();
                    c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class }, c.getBuffer(), c.length()));

                    if (c.length() == VMConnection.EXIT)
                        c.close();
                }
            }
        }

        if (clientGranularity >= 0) {
            for (int i = 0; i < clientCount; i++) {
                ReferencesClass ref = object.getArray().get(i);
                Assert.assertEquals(44 + i, ref.getInt());
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
