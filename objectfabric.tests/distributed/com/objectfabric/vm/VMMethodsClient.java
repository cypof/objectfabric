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
import com.objectfabric.TArrayTObject;
import com.objectfabric.Transaction;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.ReplicatedException;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.vm.generated.MethodsObjectModel;
import com.objectfabric.vm.generated.SimpleMethod;

public class VMMethodsClient extends Privileged {

    private static VMClient _client;

    private static int _calls;

    private static TArrayTObject<SimpleMethod> _array;

    private static int _receivedCount;

    private static boolean _exit;

    public static void main(int client, final int testNumber, int calls) {
        Debug.ProcessName = "Client " + client;
        MethodsObjectModel.register();
        _calls = calls;

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
            public void onObject(Object share) {
                _array = (TArrayTObject<SimpleMethod>) share;
                Transaction.setDefaultTrunk(_array.getTrunk());

                switch (testNumber) {
                    case 0: {
                        run0();
                        break;
                    }
                    case 1: {
                        run1();
                        break;
                    }
                    case 2: {
                        run2();
                        break;
                    }
                }
            }
        };

        _client.connectAsync();
    }

    private static void run0() {
        for (SimpleMethod object : _array) {
            for (int i = 0; i < _calls; i++) {
                final String expected = "" + i;

                object.methodAsync(expected, null, new AsyncCallback<String>() {

                    public void onSuccess(String result) {
                        _receivedCount++;
                        Assert.assertEquals(expected, result);
                    }

                    public void onFailure(Throwable t) {
                        Debug.fail();
                    }
                });
            }
        }
    }

    private static void run1() {
        for (final SimpleMethod object : _array) {
            for (int i = 0; i < _calls; i++) {
                final Transaction transaction = Transaction.start();

                final int expected = i;
                object.setInt(i);
                final SimpleMethod arg = new SimpleMethod();
                arg.setInt2(i);

                object.methodAsync(null, arg, new AsyncCallback<String>() {

                    public void onSuccess(String result) {
                        _receivedCount++;
                        Assert.assertEquals(expected + 23, object.getInt());
                        Assert.assertEquals(expected + 55, object.getInt2());
                        Assert.assertEquals(expected, arg.getInt());
                        Assert.assertEquals("" + expected, result);

                        Debug.assertion(Transaction.getCurrent() == transaction);
                        transaction.abort();
                    }

                    public void onFailure(Throwable t) {
                        Debug.fail();
                    }
                });

                Debug.assertion(Transaction.getCurrent() == null);
            }
        }
    }

    private static void run2() {
        for (final SimpleMethod object : _array) {
            for (int i = 0; i < _calls; i++) {
                //final boolean error = PlatformAdapter.getRandomBoolean();
                final boolean error = true;

                object.methodAsync(error ? SimpleMethodImpl.ERROR : "", null, new AsyncCallback<String>() {

                    public void onSuccess(String result) {
                        Assert.assertFalse(error);
                    }

                    public void onFailure(Throwable t) {
                        Assert.assertTrue(error);
                        Assert.assertTrue(t instanceof ReplicatedException);
                        Assert.assertEquals(SimpleMethodImpl.ERROR_MESSAGE, t.getMessage());
                    }
                });

                Debug.assertion(Transaction.getCurrent() == null);
            }
        }
    }

    public static int transfer(byte[] buffer, int length) {
        if (!_exit) {
            int written = _client.transfer(buffer, length);

            if (_array != null && _receivedCount == _array.length() * _calls) {
                _exit = true;

                _client.close();
                PlatformAdapter.shutdown();
            }

            return written;
        }

        return VMConnection.EXIT;
    }
}
