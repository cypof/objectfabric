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
import java.util.HashMap;

import junit.framework.Assert;

import com.objectfabric.KeyListener;
import com.objectfabric.LazyMap;
import com.objectfabric.OverloadHandler;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;

/**
 * Needs to run on another thread, otherwise the thread locals map keeps references to the
 * class loader and it doesn't GC. C.f. VMTest<X>.
 */
public class VMLazyMapServer extends TestsHelper {

    public static void main(final int granularity, int clients, final int flags) {
        Debug.ProcessName = "Server";
        VMTest.writeStart(granularity, clients, flags, "VMTest1Server");
        final boolean serverWrites = (flags & VMTest.FLAG_PROPAGATE) != 0;
        final boolean clientWrites = (flags & VMTest.FLAG_INTERCEPT) != 0;
        Debug.AssertNoConflict = serverWrites != clientWrites && clients == 1;
        Transaction trunk = VMTest.createTrunk(granularity, flags);
        final LazyMap<String, Integer> object = new LazyMap<String, Integer>();
        final HashMap<String, Integer> ref = new HashMap<String, Integer>();

        if (trunk.getStore() != null)
            trunk.getStore().setRoot(object);

        object.addListener(new KeyListener<String>() {

            @Override
            public void onPut(String key) {
                Integer current = object.get(key);
                Integer last = ref.get(key);
                last = last == null ? 0 : last;

                if (object.getTrunk().getGranularity() == Granularity.ALL)
                    Assert.assertTrue(current.equals(++last));
                else {
                    Assert.assertTrue(current >= last);
                    last = current;
                }

                ref.put(key, last);
            }

            @Override
            public void onRemoved(String key) {
                ref.remove(key);
            }

            @Override
            public void onCleared() {
                ref.clear();
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
            SeparateClassLoader client = new SeparateClassLoader(VMLazyMapClient.class.getName());
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
                        Integer value = object.get(VMLazyMap.SERVER_KEY);
                        value = (value != null ? value : 0);

                        if (!OverloadHandler.isOverloaded(object.getTrunk()))
                            if (value < VMLazyMapClient.LIMIT)
                                object.put(VMLazyMap.SERVER_KEY, value + 1);
                    }
                }, trunk);

                Transaction.run(new Runnable() {

                    public void run() {
                        if (serverWrites) {
                            Integer value = object.get(VMLazyMap.MIXED_KEY);
                            value = (value != null ? value : 0);

                            if (!OverloadHandler.isOverloaded(object.getTrunk()))
                                if (value < VMLazyMapClient.LIMIT)
                                    object.put(VMLazyMap.MIXED_KEY, value + 1);
                        }
                    }
                }, trunk);
            }
        }

        Assert.assertTrue(object.get(VMLazyMap.SERVER_KEY) == VMTest1Client.LIMIT && object.get(VMLazyMap.CLIENT_KEY) == VMTest1Client.LIMIT && object.get(VMLazyMap.MIXED_KEY) == VMTest1Client.LIMIT);

        server.stop();

        for (SeparateClassLoader client : loaders)
            client.close();

        Assert.assertTrue(ref.get(VMLazyMap.SERVER_KEY) == VMTest1Client.LIMIT && ref.get(VMLazyMap.CLIENT_KEY) == VMTest1Client.LIMIT && ref.get(VMLazyMap.MIXED_KEY) == VMTest1Client.LIMIT);

        PlatformAdapter.shutdown();
    }
}
