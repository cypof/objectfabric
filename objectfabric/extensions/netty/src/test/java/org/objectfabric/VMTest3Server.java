///**
// * This file is part of ObjectFabric (http://objectfabric.org).
// *
// * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
// * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
// * 
// * Copyright ObjectFabric Inc.
// * 
// * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
// * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
// */
//
//package org.objectfabric;
//
//import java.util.ArrayList;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import org.junit.Assert;
//
//import org.objectfabric.Workspace;
//import org.objectfabric.All;
//import org.objectfabric.Debug;
//import org.objectfabric.IndexListener;
//import org.objectfabric.Platform;
//import org.objectfabric.SeparateCL;
//import org.objectfabric.TArrayInteger;
//import org.objectfabric.TArrayTObject;
//import org.objectfabric.TList;
//import org.objectfabric.TMap;
//import org.objectfabric.TObject;
//import org.objectfabric.TestsHelper;
//import org.objectfabric.Transaction;
//import org.objectfabric.Workspace.Granularity;
//import org.objectfabric.generated.Limit32;
//import org.objectfabric.generated.LimitN;
//import org.objectfabric.generated.LimitsObjectModel;
//
///**
// * Needs to run on another thread, otherwise the thread locals map keeps references to the
// * class loader and it doesn't GC. C.f. VMServerTestLoader.
// */
//public class VMTest3Server extends TestsHelper {
//
//    public static final int CYCLES_BEFORE_INCREMENT = 1000;
//
//    public static void main(final int granularity, int clients, final int flags) {
//
//
//        ArrayList<VMConnection> connections = new ArrayList<VMConnection>();
//
//        for (int i = 0; i < clients; i++) {
//            VMConnection connection = server.createConnection();
//            connections.add(connection);
//        }
//
//        ArrayList<SeparateCL> loaders = new ArrayList<SeparateCL>();
//
//        for (int i = 0; i < clients; i++) {
//            SeparateCL client = new SeparateCL(VMTest3Client.class.getName());
//            client.setArgTypes(int.class, int.class);
//            client.setArgs(i, flags);
//            client.run(false);
//            loaders.add(client);
//            connections.get(i).setClassLoader(client);
//        }
//
//        while (server.getSessions().size() == 0) {
//            for (int i = 0; i < clients; i++) {
//                VMConnection c = connections.get(i);
//                c.setLength(c.transfer(c.getBuffer(), c.length()));
//                SeparateCL client = connections.get(i).getClassLoader();
//                c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class, int.class }, c.getBuffer(), c.length(), flags));
//            }
//        }
//
//        int commitCount = 0;
//        boolean clientsReady = false;
//        int delta = 0;
//
//        while (server.getSessions().size() > 0) {
//            for (int i = connections.size() - 1; i >= 0; i--) {
//                VMConnection c = connections.get(i);
//
//                if (c.length() != VMConnection.EXIT) {
//                    c.setLength(connections.get(i).transfer(c.getBuffer(), c.length()));
//                    SeparateCL client = connections.get(i).getClassLoader();
//                    c.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class, int.class }, c.getBuffer(), c.length(), flags));
//
//                    if (c.length() == VMConnection.EXIT)
//                        connections.remove(i).close();
//                }
//
//                if (!clientsReady) {
//                    Debug.assertAlways(connections.size() == loaders.size());
//                    clientsReady = true;
//
//                    for (int t = 0; t < clients; t++)
//                        clientsReady &= (Boolean) loaders.get(t).invoke("isReady", new Class[] {});
//                }
//
//                if (clientsReady) {
//                    if ((flags & VMTest.FLAG_PROPAGATE) != 0) {
//                        if (Platform.randomInt(CYCLES_BEFORE_INCREMENT) == 0) {
//                            final Counter counter = new Counter();
//
//                            Transaction.run(new Runnable() {
//
//                                public void run() {
//                                    int total = 0;
//
//                                    for (int t = 0; t < LimitN.FIELD_COUNT; t++)
//                                        total += ref.getOrCreate(t);
//
//                                    if (total < VMTest3Client.LIMIT)
//                                        counter.Value = All.update(limit32, limitN, map, listIndexes, listCounters, arrayTObjects, ref, flags);
//                                    else
//                                        counter.Value = 0;
//                                }
//                            });
//
//                            delta += counter.Value;
//                            commitCount++;
//                        }
//                    }
//                }
//            }
//        }
//
//        int clientDelta = 0;
//
//        for (int i = 0; i < clients; i++) {
//            if ((flags & VMTest.FLAG_INTERCEPT) != 0)
//                commitCount += (Integer) loaders.get(i).invoke("getCommitCount", new Class[] {});
//
//            clientDelta += (Integer) loaders.get(i).invoke("getDelta", new Class[] {});
//        }
//
//        int total = 0;
//
//        for (int i = 0; i < LimitN.FIELD_COUNT; i++)
//            total += ref.getOrCreate(i);
//
//        if (granularity == Granularity.ALL.ordinal())
//            Debug.assertAlways(listenerCount.get() == total);
//
//        Debug.assertAlways(delta + clientDelta == total);
//        Debug.assertAlways(commitCount == VMTest3Client.LIMIT / All.INCREMENTS);
//        All.check(limit32, limitN, map, listIndexes, listCounters, arrayTObjects, ref, flags);
//        Assert.assertTrue(total == VMTest3Client.LIMIT);
//
//        server.stop();
//
//        Debug.ProcessName = "";
//
//        for (SeparateCL client : loaders)
//            client.close();
//
//        if (trunk.getStore() != null)
//            trunk.getStore().close();
//
//        Platform.shutdown();
//    }
//
//    public static void main(String[] args) {
//        // for (int i = 0; i < 100; i++)
//        main(Granularity.ALL.ordinal(), 1, VMTest.FLAG_PROPAGATE | VMTest.FLAG_INTERCEPT);
//    }
//
//    private static final class Counter {
//
//        public int Value;
//    }
//}
