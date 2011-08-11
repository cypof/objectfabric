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

import java.util.concurrent.Executor;

import org.junit.Assert;

import com.objectfabric.ConcurrentClient.Transfer;
import com.objectfabric.OF.Config;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class ShareStoredObjectClient extends Privileged {

    public static final int LIMIT = Transfer.TOTAL / 2;

    private static VMClient _client;

    private static boolean _exit;

    private static SimpleClass _object;

    public static void main(String[] args) {
        Debug.ProcessName = "Client";
        SimpleObjectModel.register();

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
            public void onObject(Object object) {
                _object = (SimpleClass) object;
                Assert.assertEquals(42, _object.getInt0());

                Transaction.runAsync(new Runnable() {

                    public void run() {
                        _object.setInt1(12);
                    }
                }, _object.getTrunk(), new AsyncCallback<CommitStatus>() {

                    public void onSuccess(CommitStatus result) {
                        _exit = true;
                    }

                    public void onFailure(Throwable t) {
                        throw new IllegalStateException();
                    }
                });
            }
        };

        _client.connectAsync();
    }

    public static int transfer(byte[] buffer, int length) {
        if (!_exit) {
            int written = _client.transfer(buffer, length);

            if (_exit) {
                Assert.assertEquals(42, _object.getInt0());
                Assert.assertEquals(12, _object.getInt1());
                _client.close();
                Debug.ProcessName = "";
                PlatformAdapter.shutdown();
            }

            return written;
        }

        return VMConnection.EXIT;
    }
}
