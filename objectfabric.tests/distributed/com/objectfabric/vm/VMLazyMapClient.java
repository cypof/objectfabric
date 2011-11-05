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
import com.objectfabric.KeyListener;
import com.objectfabric.LazyMap;
import com.objectfabric.OF;
import com.objectfabric.OF.Config;
import com.objectfabric.OverloadHandler;
import com.objectfabric.Privileged;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.generated.LimitsObjectModel;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThreadPool;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.VMClient;
import com.objectfabric.transports.VMConnection;

public class VMLazyMapClient extends Privileged {

    public static final int LIMIT = 100;

    private static VMClient _client;

    private static int _flags;

    private static boolean _exit;

    private static LazyMap<String, Integer> _object;

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
            public void onObject(Object share) {
                _object = (LazyMap<String, Integer>) share;

                Transaction.setDefaultTrunk(_object.getTrunk());

                _object.addListener(new KeyListener<String>() {

                    @Override
                    public void onPut(String key) {
                        // Should not receive updates from server
                        Assert.assertTrue(!key.equals(VMLazyMap.SERVER_KEY));
                    }

                    @Override
                    public void onRemoved(String key) {
                        throw new AssertionError();
                    }

                    @Override
                    public void onCleared() {
                        throw new AssertionError();
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
                        // get aborted on client close
                        TestsHelper.disableExpectedExceptionThrowerCounter();

                        if (!OverloadHandler.isOverloaded(_object.getTrunk())) {
                            if (PlatformAdapter.getRandomDouble() < 0.1) {
                                Integer value;

                                try {
                                    value = _object.get(VMLazyMap.CLIENT_KEY);
                                } catch (Exception e) {
                                    Debug.assertion(e.getMessage().endsWith("Connection closed"));
                                    return;
                                }

                                value = (value != null ? value : 0);

                                if (value < LIMIT)
                                    _object.put(VMLazyMap.CLIENT_KEY, value + 1);
                            }
                        }
                    }
                }, executor, null, null);

                Transaction.runAsync(new Runnable() {

                    public void run() {
                        // get aborted on client close
                        TestsHelper.disableExpectedExceptionThrowerCounter();

                        if ((_flags & VMTest.FLAG_INTERCEPT) != 0) {
                            if (!OverloadHandler.isOverloaded(_object.getTrunk())) {
                                if (PlatformAdapter.getRandomDouble() < 0.1) {
                                    Integer value;

                                    try {
                                        value = _object.get(VMLazyMap.MIXED_KEY);
                                    } catch (Exception e) {
                                        Debug.assertion(e.getMessage().endsWith("Connection closed"));
                                        return;
                                    }

                                    value = (value != null ? value : 0);

                                    if (value < LIMIT)
                                        _object.put(VMLazyMap.MIXED_KEY, value + 1);
                                }
                            }
                        }
                    }
                }, executor, null, null);

                final int[] test = new int[3];

                for (int i = 0; i < test.length; i++) {
                    final int key = i;

                    _object.getAsync("" + key, new AsyncCallback<Integer>() {

                        @Override
                        public void onSuccess(Integer result) {
                            if (result != null) {
                                test[key] = result;
                                _exit = true;

                                for (int j = 0; j < test.length; j++)
                                    if (test[j] < LIMIT)
                                        _exit = false;
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {
                        }
                    });
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
}
