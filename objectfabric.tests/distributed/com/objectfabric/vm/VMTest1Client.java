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

import junit.framework.Assert;

import com.objectfabric.AsyncOptions;
import com.objectfabric.FieldListener;
import com.objectfabric.OF;
import com.objectfabric.OF.Config;
import com.objectfabric.OverloadHandler;
import com.objectfabric.Privileged;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.Limit32;
import com.objectfabric.generated.LimitsObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThreadPool;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class VMTest1Client extends Privileged {

    public static final int LIMIT = 100;

    private static VMClient _client;

    private static int _flags;

    private static boolean _exit;

    private static Limit32 _object;

    public static void main(int client, int flags) {
        Debug.ProcessName = "Client " + client;
        LimitsObjectModel.register();
        _flags = flags;

        OF.setConfig(new Config() {

            @Override
            public AsyncOptions createAsyncOptions() {
                return new AsyncOptions() {

                    @Override
                    public Executor getExecutor() {
                        return TransparentExecutor.getInstance();
                    }
                };
            }
        });

        _client = new VMClient() {

            @Override
            public void onObject(Object share) {
                _object = (Limit32) share;

                Transaction.setDefaultTrunk(_object.getTrunk());

                _object.addListener(new FieldListener() {

                    private int[] _last = new int[3];

                    public void onFieldChanged(int fieldIndex) {
                        Transaction transaction = null;

                        if (_object.getTrunk().getGranularity() == Granularity.COALESCE) {
                            // Make sure we see only acknowledged data
                            transaction = Transaction.start(Transaction.FLAG_IGNORE_SPECULATIVE_DATA);
                        }

                        int current = (Integer) _object.getField(fieldIndex);

                        if (_object.getTrunk().getGranularity() == Granularity.ALL) {
                            int last = ++_last[fieldIndex];
                            Assert.assertTrue(current == last);
                        } else {
                            Assert.assertTrue(current >= _last[fieldIndex]);
                            _last[fieldIndex] = current;
                        }

                        _exit = true;

                        if (_object.getInt0() < LIMIT || _object.getInt1() < LIMIT || _object.getInt2() < LIMIT)
                            _exit = false;

                        if (transaction != null)
                            transaction.abort();
                    }
                });
            }
        };

        _client.connectAsync();
    }

    public static boolean hasObject() {
        return _object != null;
    }

    public static int transfer(byte[] buffer, int length) {
        if (!_exit) {
            int written = _client.transfer(buffer, length);

            if (_object != null) {
                Executor executor = (_flags & VMTest.FLAG_TRANSPARENT_EXECUTOR) != 0 ? TransparentExecutor.getInstance() : PlatformThreadPool.getInstance();

                Transaction.runAsync(new Runnable() {

                    public void run() {
                        if (!OverloadHandler.isOverloaded(_object.getTrunk()))
                            if (PlatformAdapter.getRandomDouble() < 0.1)
                                if (_object.getInt1() < LIMIT)
                                    _object.setInt1(_object.getInt1() + 1);
                    }
                }, executor, null, null);

                Transaction.runAsync(new Runnable() {

                    public void run() {
                        if ((_flags & VMTest.FLAG_INTERCEPT) != 0)
                            if (!OverloadHandler.isOverloaded(_object.getTrunk()))
                                if (PlatformAdapter.getRandomDouble() < 0.1)
                                    if (_object.getInt2() < LIMIT)
                                        _object.setInt2(_object.getInt2() + 1);
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
