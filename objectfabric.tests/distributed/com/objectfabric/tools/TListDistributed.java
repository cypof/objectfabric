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

package com.objectfabric.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;

import com.objectfabric.Site;
import com.objectfabric.TList;
import com.objectfabric.TListTests;
import com.objectfabric.Transaction;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.tools.ExtendedTester;
import com.objectfabric.tools.TransactionalProxy;
import com.objectfabric.transports.socket.SocketClient;
import com.objectfabric.transports.socket.SocketServer;

@Ignore
@SuppressWarnings("unchecked")
public class TListDistributed extends ExtendedTester {

    public static final int CLIENTS = 1;

    @Override
    protected ExtendedTest getTest(int threads, int flags) {
        return new Tests(threads, flags);
    }

    private static final class Tests extends TListTests implements ExtendedTest {

        private final int _threads;

        private final int _flags;

        private final TList _list;

        private final TList _list2;

        private final List _wrapper;

        private final List _wrapper2;

        public Tests(int threads, int flags, TList list, TList list2) {
            _threads = threads;
            _flags = flags;
            _list = list;
            _list2 = list2;

            if ((flags & PROXY_3) != 0)
                _wrapper = (List) TransactionalProxy.wrap(_list, List.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper = (List) TransactionalProxy.wrap(_list, List.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper = (List) TransactionalProxy.wrap(_list, List.class, 1, true);
            else
                _wrapper = null;

            if ((flags & PROXY_3) != 0)
                _wrapper2 = (List) TransactionalProxy.wrap(_list2, List.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper2 = (List) TransactionalProxy.wrap(_list2, List.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper2 = (List) TransactionalProxy.wrap(_list2, List.class, 1, true);
            else
                _wrapper2 = null;
        }

        public int flags() {
            return _flags;
        }

        public int threadCount() {
            return _threads;
        }

        public void threadBefore() {
            ExtendedTester.threadBefore(this);
        }

        public void threadAfter() {
            ExtendedTester.threadAfter(this);
        }

        @Override
        protected boolean transactionIsPrivate() {
            return ExtendedTester.transactionIsPrivate(this);
        }

        @Override
        protected List createList() {
            List list = (List) getCachedOrProxy(_flags, _list, _wrapper);

            if (list != null)
                return list;

            return super.createList();
        }

        @Override
        protected List createList2() {
            List list = (List) getCachedOrProxy(_flags, _list2, _wrapper2);

            if (list != null)
                return list;

            return super.createList2();
        }
    }

    public static void main(String[] args) throws Exception {
        test(new Tests(1, PROXY_1), 100);
    }

    private final ThreadLocal<Session> _session = new ThreadLocal<Session>();

    private final ArrayList<Thread> _threads = new ArrayList<Thread>();

    private SocketServer _server;

    public TListDistributed() {
    }

    // @Test
    public void test() {
        _server = new SocketServer(4444);

        SimpleObjectModel.register();

        try {
            _server.start();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        for (int i = 0; i < 2; i++) {
            Thread thread = new Session();
            _threads.add(thread);
            thread.start();
        }

        for (Thread thread : _threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
        }

        _threads.clear();

        _server.stop();

        System.out.println(_commitCount.get() + " commits");
    }

    @Override
    protected Site getSite() {
        return _session.get().Site;
    }

    @Override
    protected void onListCreated(TList list) {
        Transaction transaction = null;

        if ((getRun() & ONE_TRANSACTION) == 0)
            transaction = Transaction.start();

        Site.getLocal().share("list", list);

        if (transaction != null)
            transaction.commit();
    }

    @Override
    protected void onListDisposed(TList list) {
        Transaction transaction = null;

        if ((getRun() & ONE_TRANSACTION) == 0)
            transaction = Transaction.start();

        Site.getLocal().unshare("list");

        if (transaction != null)
            transaction.commit();
    }

    private final class Session extends Thread {

        public final Site Site = null;

        public Session() {
        }

        @Override
        public void run() {
            SocketClient client;
            ConnectionInfo connection;

            try {
                client = new SocketClient("localhost", 4444);
                connection = client.connect();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            connection.getServer().getShare("list");

            DistributedTestRunner.this._session.set(this);
            DistributedTestRunner.this.run();
            DistributedTestRunner.this._session.set(null);

            client.disconnect();
        }
    }
}
