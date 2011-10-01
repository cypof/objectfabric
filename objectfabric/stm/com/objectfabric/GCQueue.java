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

import java.lang.ref.ReferenceQueue;

import com.objectfabric.TObject.Reference;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformThread;

final class GCQueue extends ReferenceQueue {

    private static final GCQueue _instance = new GCQueue();

    private Watcher _watcher;

    private GCQueue() {
        _watcher = new Watcher();
        _watcher.setName("ObjectFabric GC Queue");
        _watcher.setDaemon(true);
        _watcher.start();
    }

    public void dispose() {
        Log.write("GCQueue.dispose " + this);
        _watcher.dispose();

        // For GC, queue is held by TObject references
        _watcher = null;
    }

    public static GCQueue getInstance() {
        return _instance;
    }

    private final class Watcher extends Thread {

        private volatile boolean _shutdown;

        @SuppressWarnings("static-access")
        public void dispose() {
            _shutdown = true;

            while (isAlive()) {
                interrupt();

                PlatformThread.sleep(1);
            }
        }

        @Override
        public void run() {
            while (!_shutdown) {
                try {
                    Reference ref = (Reference) remove();
                    ref.collected();
                } catch (java.lang.InterruptedException _) {
                } catch (Exception e) {
                    Log.write(e);
                }
            }
        }
    }
}
