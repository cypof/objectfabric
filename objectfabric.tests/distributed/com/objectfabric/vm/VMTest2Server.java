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

import com.objectfabric.ConcurrentClient.Transfer;
import com.objectfabric.FieldListener;
import com.objectfabric.OverloadHandler;
import com.objectfabric.Site;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;

/**
 * Needs to run on another thread, otherwise the thread locals map keeps references to the
 * class loader and it doesn't GC. C.f. VMTest<X>.
 */
public class VMTest2Server extends TestsHelper {

    public static void main(final int granularity, int clients, final int flags) {
        Debug.ProcessName = "Server";
        VMTest.writeStart(granularity, clients, flags, "VMTest2Server");
        final boolean serverWrites = (flags & VMTest.FLAG_PROPAGATE) != 0;
        SimpleObjectModel.register();
        Transaction trunk = VMTest.createTrunk(granularity);
        final SimpleClass object = new SimpleClass();
        object.setInt0(Transfer.TOTAL);

        object.addListener(new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                Transfer.assertTotal(object, granularity == Granularity.COALESCE.ordinal());
            }
        });

        VMServer server = new VMServer(object);

        ArrayList<VMConnection> connections = new ArrayList<VMConnection>();

        for (int i = 0; i < clients; i++) {
            VMConnection connection = server.createConnection();
            connections.add(connection);
        }

        ArrayList<SeparateClassLoader> loaders = new ArrayList<SeparateClassLoader>();

        for (int i = 0; i < clients; i++) {
            SeparateClassLoader client = new SeparateClassLoader(VMTest2Client.class.getName());
            client.setArgTypes(int.class, int.class);
            client.setArgs(i, flags);
            client.run();
            loaders.add(client);
            connections.get(i).setClassLoader(client);
        }

        for (;;) {
            int count = 0;

            for (int i = 0; i < clients; i++) {
                VMConnection c = connections.get(i);
                c.setLength(c.transfer(c.getBuffer(), c.length()));
                SeparateClassLoader client = c.getClassLoader();
                c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class }, c.getBuffer(), c.length()));

                if ((Boolean) client.invoke("hasObject", new Class[0]))
                    count++;
            }

            if (count == clients)
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

                Transaction.run(new Runnable() {

                    public void run() {
                        if (serverWrites)
                            if (!OverloadHandler.isOverloaded(object.getTrunk()))
                                Transfer.to2(object);
                    }
                }, trunk);

                Transaction.run(new Runnable() {

                    public void run() {
                        if (serverWrites)
                            if (!OverloadHandler.isOverloaded(object.getTrunk()))
                                Transfer.between0And1(object);
                    }
                }, trunk);
            }
        }

        Transfer.assertTotal(object, false);

        server.stop();

        Debug.ProcessName = "";
        Debug.AssertNoConflict = false;

        for (SeparateClassLoader client : loaders) {
            int successes = (Integer) client.invoke("getSuccesses", new Class[0]);
            Debug.assertAlways(successes > 0);
            client.close();
        }

        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
        PlatformAdapter.shutdown();
    }
}
