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

import com.objectfabric.FieldListener;
import com.objectfabric.OverloadHandler;
import com.objectfabric.Site;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.Limit32;
import com.objectfabric.generated.LimitsObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;

/**
 * Needs to run on another thread, otherwise the thread locals map keeps references to the
 * class loader and it doesn't GC. C.f. VMTest<X>.
 */
public class VMTest1Server extends TestsHelper {

    public static void main(final int granularity, int clients, final int flags) {
        Debug.ProcessName = "Server";
        VMTest.writeStart(granularity, clients, flags, "VMTest1Server");
        final boolean serverWrites = (flags & VMTest.FLAG_PROPAGATE) != 0;
        final boolean clientWrites = (flags & VMTest.FLAG_INTERCEPT) != 0;
        Debug.AssertNoConflict = serverWrites != clientWrites && clients == 1;
        LimitsObjectModel.register();
        Transaction trunk = VMTest.createTrunk(granularity, flags);
        final Limit32 object = new Limit32();

        if (trunk.getStore() != null)
            trunk.getStore().setRoot(object);

        final int[] lastFields = new int[3];

        object.addListener(new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                int current = (Integer) object.getField(fieldIndex);

                if (object.getTrunk().getGranularity() == Granularity.ALL) {
                    int last = ++lastFields[fieldIndex];
                    Assert.assertTrue(current == last);
                } else {
                    Assert.assertTrue(current >= lastFields[fieldIndex]);
                    lastFields[fieldIndex] = current;
                }
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
            SeparateClassLoader client = new SeparateClassLoader(VMTest1Client.class.getName());
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

                if (granularity == Granularity.COALESCE.ordinal())
                    Debug.assertAlways(!OverloadHandler.isOverloaded(object.getTrunk()));

                Transaction.run(new Runnable() {

                    public void run() {
                        if (!OverloadHandler.isOverloaded(object.getTrunk()))
                            if (object.getInt0() < VMTest1Client.LIMIT)
                                object.setInt0(object.getInt0() + 1);
                    }
                }, trunk);

                Transaction.run(new Runnable() {

                    public void run() {
                        if (serverWrites)
                            if (!OverloadHandler.isOverloaded(object.getTrunk()))
                                if (object.getInt2() < VMTest1Client.LIMIT)
                                    object.setInt2(object.getInt2() + 1);
                    }
                }, trunk);
            }
        }

        Assert.assertTrue(object.getInt0() == VMTest1Client.LIMIT && object.getInt1() == VMTest1Client.LIMIT && object.getInt2() == VMTest1Client.LIMIT);

        server.stop();

        for (SeparateClassLoader client : loaders)
            client.close();

        Assert.assertTrue(lastFields[0] == VMTest1Client.LIMIT && lastFields[1] == VMTest1Client.LIMIT && lastFields[2] == VMTest1Client.LIMIT);

        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());

        if (trunk.getStore() != null)
            trunk.getStore().close();

        PlatformAdapter.shutdown();
    }
}
