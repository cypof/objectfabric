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
import java.util.concurrent.atomic.AtomicInteger;

import com.objectfabric.AsyncOptions;
import com.objectfabric.CrossHelper;
import com.objectfabric.FieldListener;
import com.objectfabric.OF;
import com.objectfabric.OF.Config;
import com.objectfabric.Privileged;
import com.objectfabric.TArrayInteger;
import com.objectfabric.TArrayTObject;
import com.objectfabric.TList;
import com.objectfabric.TMap;
import com.objectfabric.TObject;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.Limit32;
import com.objectfabric.generated.LimitN;
import com.objectfabric.generated.LimitsObjectModel;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThreadPool;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class VMTest3Client extends Privileged {

    public static final int LIMIT = 1000;

    private static VMClient _client;

    private static int[] _last = new int[LimitN.FIELD_COUNT];

    private static int _flags;

    private static boolean _exit;

    private static Limit32 _limit32;

    private static LimitN _limitN;

    private static TMap<Integer, Integer> _map;

    private static TList<Integer> _listIndexes;

    private static TList<Integer> _listCounters;

    private static TArrayTObject<Limit32> _arrayTObjects;

    private static TArrayInteger _ref;

    private static final AtomicInteger _commitCount = new AtomicInteger();

    private static int _delta;

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

            @SuppressWarnings("unchecked")
            @Override
            public void onObject(Object object) {
                TArrayTObject<TObject> share = (TArrayTObject) object;
                _limit32 = (Limit32) share.get(0);
                _limitN = (LimitN) share.get(1);
                _map = (TMap<Integer, Integer>) share.get(2);
                _listIndexes = (TList<Integer>) share.get(3);
                _listCounters = (TList<Integer>) share.get(4);
                _arrayTObjects = (TArrayTObject<Limit32>) share.get(5);
                _ref = (TArrayInteger) share.get(6);

                _limitN.addListener(new FieldListener() {

                    @SuppressWarnings("null")
                    public void onFieldChanged(int fieldIndex) {
                        Transaction transaction = null;

                        if (_limit32.getTrunk().getGranularity() == Granularity.COALESCE) {
                            // Make sure we see only acknowledged data
                            transaction = Transaction.start(Transaction.FLAG_IGNORE_SPECULATIVE_DATA);
                        }

                        int total = 0;

                        for (int i = 0; i < LimitN.FIELD_COUNT; i++)
                            total += _ref.get(i);

                        if (total == LIMIT)
                            _exit = true;

                        if (isReady())
                            CrossHelper.check(_limit32, _limitN, _map, _listIndexes, _listCounters, _arrayTObjects, _ref, _last, fieldIndex, _flags);

                        if (transaction != null)
                            transaction.abort();
                    }
                });
            }
        };

        _client.connectAsync();
    }

    public static int transfer(byte[] buffer, int length, final int flags) {
        if (!_exit) {
            int written = _client.transfer(buffer, length);

            if (isReady()) {
                Transaction.setDefaultTrunk(_limit32.getTrunk());

                if ((flags & VMTest.FLAG_INTERCEPT) != 0) {
                    if (PlatformAdapter.getRandomInt(VMTest3Server.CYCLES_BEFORE_INCREMENT) == 0) {
                        Executor executor = (_flags & VMTest.FLAG_TRANSPARENT_EXECUTOR) != 0 ? TransparentExecutor.getInstance() : PlatformThreadPool.getInstance();
                        final AtomicInteger delta = new AtomicInteger();

                        Transaction.runAsync(new Runnable() {

                            public void run() {
                                int total = 0;

                                for (int i = 0; i < LimitN.FIELD_COUNT; i++)
                                    total += _ref.get(i);

                                if (total < LIMIT)
                                    delta.set(CrossHelper.update(_limit32, _limitN, _map, _listIndexes, _listCounters, _arrayTObjects, _ref, flags));
                                else
                                    delta.set(0);
                            }
                        }, executor, new AsyncCallback<CommitStatus>() {

                            public void onSuccess(CommitStatus result) {
                                _delta += delta.get();

                                if (delta.get() != 0)
                                    _commitCount.incrementAndGet();
                            }

                            public void onFailure(Exception t) {
                                Log.write(t);
                            }
                        });
                    }
                }
            }

            if (_exit) {
                _client.close();
                PlatformAdapter.shutdown();
            }

            return written;
        }

        return VMConnection.EXIT;
    }

    public static boolean isReady() {
        return _limit32 != null;
    }

    public static int getCommitCount() {
        return _commitCount.get();
    }

    public static int getDelta() {
        return _delta;
    }
}
