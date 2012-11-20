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

import java.util.concurrent.CyclicBarrier;

class SplitRemoteClient extends Thread {

    final TMap<String, String> _map;

    final int _client, _count;

    final CyclicBarrier _barrier;

    int _counter;

    SplitRemoteClient(TMap<String, String> map, int client, int count, CyclicBarrier barrier) {
        _map = map;
        _client = client;
        _count = count;
        _barrier = barrier;
    }

    @Override
    public void run() {
        try {
            _barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        boolean remote = false;

        for (int i = 0; i < _count; i++) {
            if (Platform.get().randomInt(10) == 0) {
                remote = !remote;

                if (!remote)
                    _counter++;
            }

            final boolean remote_ = remote;
            final int counter_ = _counter;

            _map.workspace().atomic(new Runnable() {

                @Override
                public void run() {
                    VersionMap versionMap = _map.resource().transaction().getOrCreateVersionMap();

                    if (remote_)
                        versionMap.setRemote();

                    String s = "" + _client + ":" + (remote_ ? "remote" : "local") + ":" + counter_;
                    _map.put(s, s);
                }
            });

            // Assert no memory leak

            if (i == _count / 2 || i == _count * 9 / 10) {
                TestsHelper.assertMemory("at i=" + i);

                /*
                 * Otherwise sometimes threads run one after the other and there is no
                 * transaction aborted, which breaks the asserts.
                 */
                try {
                    Thread.sleep(1);
                } catch (java.lang.InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}
