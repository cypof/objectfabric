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

package com.objectfabric;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketServer;

@SuppressWarnings("unchecked")
public class TListDistributed {

    @Test
    public void run1() throws IOException {
        run(1);
    }

    @Test
    public void run8() throws IOException {
        run(8);
    }

    private void run(int count) throws IOException {
        final TList list = new TList();
        final TList list2 = new TList();

        final AtomicInteger listAdds = new AtomicInteger();
        final AtomicInteger listRemoves = new AtomicInteger();
        final AtomicInteger listClears = new AtomicInteger();

        list.addListener(new ListListener() {

            @Override
            public void onAdded(int index) {
                listAdds.incrementAndGet();
            }

            @Override
            public void onRemoved(int index) {
                listRemoves.incrementAndGet();
            }

            @Override
            public void onCleared() {
                listClears.incrementAndGet();
            }
        });

        final AtomicInteger list2Adds = new AtomicInteger();
        final AtomicInteger list2Removes = new AtomicInteger();
        final AtomicInteger list2Clears = new AtomicInteger();

        list2.addListener(new ListListener() {

            @Override
            public void onAdded(int index) {
                list2Adds.incrementAndGet();
            }

            @Override
            public void onRemoved(int index) {
                list2Removes.incrementAndGet();
            }

            @Override
            public void onCleared() {
                list2Clears.incrementAndGet();
            }
        });

        SocketServer server = new SocketServer(4444);

        server.setCallback(new Callback<SocketServer.Session>() {

            public void onConnection(SocketServer.Session session) {
                TList pair = new TList();
                pair.add(list);
                pair.add(list2);
                session.send(pair);
            }

            public void onDisconnection(SocketServer.Session session, Exception e) {
                System.out.println("Disconnection from " + session.getRemoteAddress());
            }

            public void onReceived(SocketServer.Session session, Object object) {
                System.out.println("Received: " + object);
            }
        });

        server.start();

        ArrayList<Thread> clients = new ArrayList<Thread>();

        for (int i = 0; i < count; i++) {
            SeparateClassLoader client = new SeparateClassLoader("Client " + i, TListDistributedClient.class.getName(), false);
            client.setArgTypes(int.class);
            client.setArgs(i);
            clients.add(client);
            client.start();
        }

        for (Thread thread : clients) {
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
        }

        server.stop();

        Assert.assertTrue(listAdds.get() > 10);
        Assert.assertTrue(listRemoves.get() > 10);
        Assert.assertTrue(listClears.get() > 10);
        Assert.assertTrue(list2Adds.get() > 10);
        Assert.assertTrue(list2Adds.get() > 10);
        Assert.assertTrue(list2Adds.get() > 10);
    }

    public static void main(String[] args) throws Exception {
        TListDistributed test = new TListDistributed();
        test.run8();
        PlatformAdapter.reset();
    }
}
