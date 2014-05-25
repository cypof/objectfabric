/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.objectfabric.generated.Limit32;
import org.objectfabric.generated.LimitsObjectModel;

public class DistributedClient {

    public static final int GO = 1, DONE = 2, CLOSING = 3, CLOSED = 4;

    public static final int LIMIT = 10;

    private static Limit32 _object;

    private static AtomicInteger _progress;

    private static int _flags;

    private static volatile boolean _callback;

    private static int[] _last = new int[Limit32.FIELD_COUNT];

    public static void main(AtomicInteger progress, final int clients, int client, int flags) {
        if (Debug.ENABLED)
            Helper.instance().ProcessName = "Client " + client;

        JVMPlatform.loadClass();
        _progress = progress;
        _flags = flags;

        LimitsObjectModel.register();

        final Workspace workspace = Distributed.createClientWorkspace(flags);
        workspace.addURIHandler(VMURIHandler.getInstance());
        String uri;

        // if ((flags & Distributed.FLAG_SEPARATE_JVMS) == 0)
        uri = "vm:///object";
        // else
        // uri = "tcp://localhost:8080/object";

        workspace.openAsync(uri, new AsyncCallback<Resource>() {

            @Override
            public void onSuccess(Resource result) {
                _object = (Limit32) result.get();

                _progress.set(GO);

                _object.addListener(new IndexListener() {

                    public void onSet(int field) {
                        if (Debug.ENABLED)
                            Debug.assertion(!_callback);

                        _callback = true;

                        int current = (Integer) _object.getField(field);

                        if (clients == 1 && (field == 0 || field == 1))
                            Assert.assertTrue(current >= _last[field]);

                        _last[field] = current;

                        if (_last[0] == LIMIT && _last[1] == LIMIT && _last[2] == LIMIT) {
                            int int0 = _object.int0();
                            int int1 = _object.int0();
                            int int2 = _object.int0();
                            Assert.assertTrue(int0 == LIMIT && int1 == LIMIT && int2 == LIMIT);
                            _progress.set(DONE);
                        }

                        _callback = false;
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.write(e);
            }
        });

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();
    }

    public static int[] getEndValues() {
        return _last;
    }

    public static void step() {
        if (_progress.get() == GO) {
            if (_object != null && !_object.workspace().resolver().isClosing()) {
                _object.atomic(new Runnable() {

                    public void run() {
                        if (_object.int1() < LIMIT)
                            _object.int1(_object.int1() + 1);

                        for (int i = 3; i < Limit32.FIELD_COUNT; i++)
                            _object.setField(i, Platform.get().randomInt());
                    }
                });

                _object.atomic(new Runnable() {

                    public void run() {
                        if (_object.int2() < LIMIT)
                            _object.int2(_object.int2() + 1);
                    }
                });
            }
        } else {
            if (_progress.get() == CLOSING) {
                _progress.set(CLOSED);

                _object.workspace().flushNotifications();

                for (int i = 0; i < Limit32.FIELD_COUNT; i++)
                    Assert.assertEquals(_object.getField(i), _last[i]);

                _object.workspace().closeAsync(null);
            }
        }
    }
}
