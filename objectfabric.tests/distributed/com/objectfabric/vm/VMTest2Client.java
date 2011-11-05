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

import com.objectfabric.AsyncOptions;
import com.objectfabric.ConcurrentClient.Transfer;
import com.objectfabric.FieldListener;
import com.objectfabric.OF;
import com.objectfabric.OF.Config;
import com.objectfabric.OverloadHandler;
import com.objectfabric.Privileged;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThreadPool;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class VMTest2Client extends Privileged {

    public static final int LIMIT = Transfer.TOTAL / 2;

    private static VMClient _client;

    private static int _flags;

    private static boolean _exit;

    private static int _successes;

    private static SimpleClass _object;

    public static void main(int client, int flags) {
        Debug.ProcessName = "Client " + client;
        SimpleObjectModel.register();
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
                _object = (SimpleClass) share;

                Transaction.setDefaultTrunk(_object.getTrunk());

                _object.addListener(new FieldListener() {

                    public void onFieldChanged(int fieldIndex) {
                        Transfer.assertTotal(_object, _object.getTrunk().getGranularity() == Granularity.COALESCE);
                        Transaction transaction = null;

                        if (_object.getTrunk().getGranularity() == Granularity.COALESCE) {
                            // Make sure we see only acknowledged data
                            transaction = Transaction.start(Transaction.FLAG_IGNORE_SPECULATIVE_DATA);
                        }

                        Transfer.assertTotal(_object, false);

                        if (_object.getInt2() > LIMIT)
                            _exit = true;

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

    public static int getSuccesses() {
        return _successes;
    }

    public static int transfer(byte[] buffer, int length) {
        if (!_exit) {
            int written = _client.transfer(buffer, length);

            if (_object != null) {
                Executor executor = (_flags & VMTest.FLAG_TRANSPARENT_EXECUTOR) != 0 ? TransparentExecutor.getInstance() : PlatformThreadPool.getInstance();

                if (_object.getTrunk().getGranularity() == Granularity.COALESCE)
                    Debug.assertAlways(!OverloadHandler.isOverloaded(_object.getTrunk()));

                Transaction.runAsync(new Runnable() {

                    public void run() {
                        if ((_flags & VMTest.FLAG_INTERCEPT) != 0)
                            if (!OverloadHandler.isOverloaded(_object.getTrunk()))
                                Transfer.to2(_object);
                    }
                }, executor, new AsyncCallback<Transaction.CommitStatus>() {

                    public void onSuccess(CommitStatus result) {
                        _successes++;
                    }

                    public void onFailure(Exception _) {
                    }
                });

                Transaction.runAsync(new Runnable() {

                    public void run() {
                        if ((_flags & VMTest.FLAG_INTERCEPT) != 0)
                            if (!OverloadHandler.isOverloaded(_object.getTrunk()))
                                Transfer.between0And1(_object);
                    }
                }, executor, new AsyncCallback<Transaction.CommitStatus>() {

                    public void onSuccess(CommitStatus result) {
                        _successes++;
                    }

                    public void onFailure(Exception _) {
                    }
                });
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
