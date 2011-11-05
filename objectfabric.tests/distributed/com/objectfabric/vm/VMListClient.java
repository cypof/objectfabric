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

import java.util.concurrent.Executor;

import com.objectfabric.OverloadHandler;
import com.objectfabric.Privileged;
import com.objectfabric.TList;
import com.objectfabric.TListExtended;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThreadPool;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.tools.ExtendedTester;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class VMListClient extends Privileged {

    public static final int LIMIT = 10;

    private static VMClient _client;

    private static int _flags;

    static TList _list, _list2;

    private static boolean _exit;

    public static void main(int client, int flags) {
        Debug.ProcessName = "Client " + client;
        _flags = flags;

        _client = new VMClient() {

            @Override
            public void onObject(Object share) {
                TList pair = (TList) share;
                _list = (TList) pair.get(0);
                _list2 = (TList) pair.get(1);
                Transaction.setCurrent(_list.getTrunk());
            }

        };

        _client.connectAsync();
    }

    public static int transfer(byte[] buffer, int length) {
        if (!_exit) {
            int written = _client.transfer(buffer, length);

            if (_list != null) {
                Executor executor = (_flags & VMTest.FLAG_TRANSPARENT_EXECUTOR) != 0 ? TransparentExecutor.getInstance() : PlatformThreadPool.getInstance();

                Transaction.runAsync(new Runnable() {

                    public void run() {
                        if (!OverloadHandler.isOverloaded(_list.getTrunk()))
                            new TListExtended().test(new TListExtended.Tests(1, ExtendedTester.REUSE_MAP_BY_CLEAR, _list, _list2), 1, false);
                    }
                }, executor, null, null);
            }

            if (_exit) {
                _client.close();
                PlatformAdapter.shutdown();
            }

            return written;
        }

        return VMConnection.EXIT;
    }
}
