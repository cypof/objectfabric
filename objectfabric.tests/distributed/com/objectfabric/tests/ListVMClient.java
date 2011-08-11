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

package com.objectfabric.tests;

import java.util.concurrent.Executor;


import com.objectfabric.OverloadHandler;
import com.objectfabric.Privileged;
import com.objectfabric.Site;
import com.objectfabric.TList;
import com.objectfabric.TListExtended;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.tools.ExtendedTester;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class ListVMClient extends Privileged {

    public static final int LIMIT = 10;

    private static VMClient _client;

    static TList _list, _list2;

    static boolean _run;

    public static void main(String[] args) {
        Debug.ProcessName = "Client " + args[0];
        _client = new VMClient();

        _client.connectAsync(null, new AsyncCallbackWithExecutor<VMConnection>() {

            public void onSuccess(VMConnection connection) {
                if (Debug.ENABLED)
                    Debug.assertion(connection == _client.getConnection());

                Site server = connection.getTarget();

                server.getSharedAsync("list", new AsyncCallbackWithExecutor<Object>() {

                    public void onSuccess(Object shared) {
                        _list = (TList) shared;
                    }

                    public void onFailure(Throwable t) {
                        throw new IllegalStateException();
                    }

                    public Executor getExecutor() {
                        return TransparentExecutor.getInstance();
                    }
                });

                server.getSharedAsync("list2", new AsyncCallbackWithExecutor<Object>() {

                    public void onSuccess(Object shared) {
                        _list2 = (TList) shared;
                    }

                    public void onFailure(Throwable t) {
                        throw new IllegalStateException();
                    }

                    public Executor getExecutor() {
                        return TransparentExecutor.getInstance();
                    }
                });
            }

            public void onFailure(Throwable t) {
                throw new IllegalStateException();
            }

            public Executor getExecutor() {
                return TransparentExecutor.getInstance();
            }
        });
    }

    public static int transfer(byte[] buffer, int length) {
        int written = _client.getConnection().transfer(buffer, length);

        if (_list != null && _list2 != null) {
            if (!_run) {
                Transaction.setCurrent(_list.getTrunk());
                new TListExtended().test(new TListExtended.Tests(1, ExtendedTester.REUSE_MAP_BY_CLEAR, _list, _list2), 1, false);
                _run = true;
            }

            if (OverloadHandler.getQueueSize(_list.getTrunk()) == 1) {
                _client.getConnection().close();
                disposeGCQueue();
                Debug.ProcessName = "";
                TestsHelper.resetRuntime();
                abortPendingMaps(_list.getTrunk());
                assertIdleAndCleanup();
                return VMConnection.EXIT;
            }
        }

        return written;
    }
}
