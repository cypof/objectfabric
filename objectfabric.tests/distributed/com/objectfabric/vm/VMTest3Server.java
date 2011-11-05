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
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import com.objectfabric.CrossHelper;
import com.objectfabric.FieldListener;
import com.objectfabric.Site;
import com.objectfabric.TArrayInteger;
import com.objectfabric.TArrayTObject;
import com.objectfabric.TList;
import com.objectfabric.TMap;
import com.objectfabric.TObject;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.Limit32;
import com.objectfabric.generated.LimitN;
import com.objectfabric.generated.LimitsObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;

/**
 * Needs to run on another thread, otherwise the thread locals map keeps references to the
 * class loader and it doesn't GC. C.f. VMServerTestLoader.
 */
public class VMTest3Server extends TestsHelper {

    public static final int CYCLES_BEFORE_INCREMENT = 1000;

    public static void main(final int granularity, int clients, final int flags) {
        Debug.ProcessName = "Server";
        VMTest.writeStart(granularity, clients, flags, "VMTest3Server");
        LimitsObjectModel.register();
        final Transaction trunk = VMTest.createTrunk(granularity, flags);
        final Limit32 limit32 = new Limit32();
        final LimitN limitN = new LimitN();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>();
        final TList<Integer> listIndexes = new TList<Integer>();
        final TList<Integer> listCounters = new TList<Integer>();
        final TArrayTObject<Limit32> arrayTObjects = new TArrayTObject<Limit32>(LimitN.FIELD_COUNT);
        final TArrayInteger ref = new TArrayInteger(LimitN.FIELD_COUNT);

        final int[] last = new int[LimitN.FIELD_COUNT];
        final AtomicInteger listenerCount = new AtomicInteger();

        limitN.addListener(new FieldListener() {

            @SuppressWarnings("null")
            public void onFieldChanged(int fieldIndex) {
                Transaction snapshot = null;

                if (trunk.getGranularity() == Granularity.COALESCE)
                    snapshot = Transaction.start();

                CrossHelper.check(limit32, limitN, map, listIndexes, listCounters, arrayTObjects, ref, last, fieldIndex, flags);
                listenerCount.incrementAndGet();

                if (snapshot != null)
                    snapshot.abort();
            }
        });

        TArrayTObject<TObject> share = new TArrayTObject<TObject>(7);
        share.set(0, limit32);
        share.set(1, limitN);
        share.set(2, map);
        share.set(3, listIndexes);
        share.set(4, listCounters);
        share.set(5, arrayTObjects);
        share.set(6, ref);
        VMServer server = new VMServer(share);

        if (trunk.getStore() != null)
            trunk.getStore().setRoot(share);

        ArrayList<VMConnection> connections = new ArrayList<VMConnection>();

        for (int i = 0; i < clients; i++) {
            VMConnection connection = server.createConnection();
            connections.add(connection);
        }

        ArrayList<SeparateClassLoader> loaders = new ArrayList<SeparateClassLoader>();

        for (int i = 0; i < clients; i++) {
            SeparateClassLoader client = new SeparateClassLoader(VMTest3Client.class.getName());
            client.setArgTypes(int.class, int.class);
            client.setArgs(i, flags);
            client.run();
            loaders.add(client);
            connections.get(i).setClassLoader(client);
        }

        while (server.getSessions().size() == 0) {
            for (int i = 0; i < clients; i++) {
                VMConnection c = connections.get(i);
                c.setLength(c.transfer(c.getBuffer(), c.length()));
                SeparateClassLoader client = connections.get(i).getClassLoader();
                c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class, int.class }, c.getBuffer(), c.length(), flags));
            }
        }

        int commitCount = 0;
        boolean clientsReady = false;
        int delta = 0;

        while (server.getSessions().size() > 0) {
            for (int i = connections.size() - 1; i >= 0; i--) {
                VMConnection c = connections.get(i);

                if (c.length() != VMConnection.EXIT) {
                    c.setLength(connections.get(i).transfer(c.getBuffer(), c.length()));
                    SeparateClassLoader client = connections.get(i).getClassLoader();
                    c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class, int.class }, c.getBuffer(), c.length(), flags));

                    if (c.length() == VMConnection.EXIT)
                        connections.remove(i).close();
                }

                if (!clientsReady) {
                    Debug.assertAlways(connections.size() == loaders.size());
                    clientsReady = true;

                    for (int t = 0; t < clients; t++)
                        clientsReady &= (Boolean) loaders.get(t).invoke("isReady", new Class[] {});
                }

                if (clientsReady) {
                    if ((flags & VMTest.FLAG_PROPAGATE) != 0) {
                        if (PlatformAdapter.getRandomInt(CYCLES_BEFORE_INCREMENT) == 0) {
                            final Counter counter = new Counter();

                            Transaction.run(new Runnable() {

                                public void run() {
                                    int total = 0;

                                    for (int t = 0; t < LimitN.FIELD_COUNT; t++)
                                        total += ref.get(t);

                                    if (total < VMTest3Client.LIMIT)
                                        counter.Value = CrossHelper.update(limit32, limitN, map, listIndexes, listCounters, arrayTObjects, ref, flags);
                                    else
                                        counter.Value = 0;
                                }
                            });

                            delta += counter.Value;
                            commitCount++;
                        }
                    }
                }
            }
        }

        int clientDelta = 0;

        for (int i = 0; i < clients; i++) {
            if ((flags & VMTest.FLAG_INTERCEPT) != 0)
                commitCount += (Integer) loaders.get(i).invoke("getCommitCount", new Class[] {});

            clientDelta += (Integer) loaders.get(i).invoke("getDelta", new Class[] {});
        }

        int total = 0;

        for (int i = 0; i < LimitN.FIELD_COUNT; i++)
            total += ref.get(i);

        if (granularity == Granularity.ALL.ordinal())
            Debug.assertAlways(listenerCount.get() == total);

        Debug.assertAlways(delta + clientDelta == total);
        Debug.assertAlways(commitCount == VMTest3Client.LIMIT / CrossHelper.INCREMENTS);
        CrossHelper.check(limit32, limitN, map, listIndexes, listCounters, arrayTObjects, ref, flags);
        Assert.assertTrue(total == VMTest3Client.LIMIT);

        server.stop();

        Debug.ProcessName = "";

        for (SeparateClassLoader client : loaders)
            client.close();

        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());

        if (trunk.getStore() != null)
            trunk.getStore().close();

        PlatformAdapter.shutdown();
    }

    public static void main(String[] args) {
        // for (int i = 0; i < 100; i++)
        main(Granularity.ALL.ordinal(), 1, VMTest.FLAG_PROPAGATE | VMTest.FLAG_INTERCEPT);
    }

    private static final class Counter {

        public int Value;
    }
}
