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

import org.junit.Assert;

import com.objectfabric.AsyncOptions;
import com.objectfabric.OF;
import com.objectfabric.OF.Config;
import com.objectfabric.Privileged;
import com.objectfabric.Site;
import com.objectfabric.TArrayTObject;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.ReferencesClass;
import com.objectfabric.generated.ReferencesObjectModel;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class VMTest4Client extends Privileged {

    private static VMClient _client;

    private static boolean _hasObject, _exit;

    public static void main(final int client, final int serverGranularity, final int clientGranularity) {
        Debug.ProcessName = "Client " + client;
        ReferencesObjectModel.register();

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
                _hasObject = true;
                final ReferencesClass object = (ReferencesClass) share;
                Assert.assertEquals(42, object.getInt());

                if (serverGranularity >= 0)
                    Assert.assertEquals(43, object.getRef().getInt());

                if (clientGranularity >= 0) {
                    Transaction trunk2 = Site.getLocal().createTrunk(Granularity.values()[clientGranularity]);
                    ReferencesClass object2 = new ReferencesClass(trunk2);
                    object2.setInt(44 + client);
                    TArrayTObject<ReferencesClass> array = object.getArray();

                    Transaction transaction = Transaction.start(array.getTrunk());
                    array.set(client, object2);

                    transaction.commitAsync(new AsyncCallback<Transaction.CommitStatus>() {

                        public void onSuccess(CommitStatus result) {
                            _exit = true;
                        }

                        public void onFailure(Exception _) {
                        }
                    });
                } else
                    _exit = true;
            }
        };

        _client.connectAsync();
    }

    public static boolean hasObject() {
        return _hasObject;
    }

    public static int transfer(byte[] buffer, int length) {
        if (!_exit) {
            int written = _client.transfer(buffer, length);

            if (_exit) {
                _client.close();
                PlatformAdapter.shutdown();
            }

            return written;
        }

        return VMConnection.EXIT;
    }
}
