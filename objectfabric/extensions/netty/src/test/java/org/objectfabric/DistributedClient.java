///**
// * This file is part of ObjectFabric (http://objectfabric.org).
// *
// * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
// * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
// * 
// * Copyright ObjectFabric Inc.
// * 
// * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
// * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
// */
//
//package org.objectfabric;
//
//import java.util.concurrent.atomic.AtomicInteger;
//
//import org.junit.Assert;
//import org.objectfabric.generated.Limit32;
//import org.objectfabric.generated.LimitsObjectModel;
//
//public class DistributedClient {
//
//    public static final int GO = 1, EXIT = 2;
//
//    public static final int LIMIT = 10;
//
//    private static Limit32 _object;
//
//    private static volatile boolean _callback, _closing;
//
//    private static int[] _last = new int[3];
//
//    public static void main(final AtomicInteger progress, int client, int flags) {
//        if (Debug.ENABLED)
//            Helper.instance().ProcessName = "Client " + client;
//
//        LimitsObjectModel.register();
//
//        final Workspace workspace = Distributed.createClientWorkspace(flags);
//        workspace.addURIHandler(VMURIHandler.getInstance());
//        String uri;
//
//        if ((flags & Distributed.FLAG_SEPARATE_JVMS) == 0)
//            uri = "vm:///object";
//        else
//            uri = "tcp://localhost:8080/object";
//
//        workspace.resolve(uri).getAsync(new AsyncCallback<Object>() {
//
//            @Override
//            public void onSuccess(Object result) {
//                _object = (Limit32) result;
//
//                progress.set(GO);
//
//                _object.addListener(new IndexListener() {
//
//                    public void onSet(int fieldIndex) {
//                        if (Debug.ENABLED)
//                            Debug.assertion(!_callback);
//
//                        _callback = true;
//
//                        if (!_closing) {
//                            int current = (Integer) _object.getField(fieldIndex);
//
//                            if (fieldIndex != 2)
//                                Assert.assertTrue(current >= _last[fieldIndex]);
//
//                            _last[fieldIndex] = current;
//
//                            if (_last[0] == LIMIT && _last[1] == LIMIT && _last[2] == LIMIT) {
//                                Assert.assertTrue(_object.getInt0() == LIMIT && _object.getInt1() == LIMIT && _object.getInt2() == LIMIT);
//
//                                if (!_closing) {
//                                    _closing = true;
//
//                                    workspace.closeAsync(new AsyncCallback<Void>() {
//
//                                        @Override
//                                        public void onSuccess(Void _) {
//                                            ClientURIHandler.disableNetwork();
//                                        }
//
//                                        @Override
//                                        public void onFailure(Exception e) {
//                                        }
//                                    });
//                                }
//                            }
//                        }
//
//                        _callback = false;
//                    }
//                });
//            }
//
//            @Override
//            public void onFailure(Exception e) {
//                Log.write(e);
//            }
//        });
//
//        if (Debug.THREADS)
//            ThreadAssert.assertCurrentIsEmpty();
//    }
//
//    public static boolean hasObject() {
//        return _object != null;
//    }
//
//    public static void step() {
//        if (!_closing) {
//            if (_object != null && !_object.workspace().isClosing()) {
//                _object.atomic(new Runnable() {
//
//                    public void run() {
//                        if (_object.getInt1() < LIMIT)
//                            _object.setInt1(_object.getInt1() + 1);
//                    }
//                });
//
//                _object.atomic(new Runnable() {
//
//                    public void run() {
//                        if (_object.getInt2() < LIMIT)
//                            _object.setInt2(_object.getInt2() + 1);
//                    }
//                });
//            }
//        }
//    }
//}
