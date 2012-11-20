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

import java.lang.ref.ReferenceQueue;

@SuppressWarnings("rawtypes")
final class GCQueue extends ReferenceQueue {

    private static final GCQueue _instance = new GCQueue();

    private final Watcher _watcher;

    static GCQueue getInstance() {
        return _instance;
    }

    GCQueue() {
        _watcher = new Watcher();
        _watcher.setName("ObjectFabric GC Queue");
        _watcher.setDaemon(true);
        _watcher.start();
    }

    void close() {
        _watcher.close();
    }

    private final class Watcher extends Thread {

        private volatile boolean _shutdown;

        final void close() {
            _shutdown = true;

            while (isAlive()) {
                interrupt();

                Platform.get().sleep(1);
            }
        }

        @Override
        public void run() {
            while (!_shutdown) {
                try {
                    PlatformRef ref = (PlatformRef) remove();
                    ref.collected();
                } catch (java.lang.InterruptedException _) {
                } catch (Exception e) {
                    Log.write(e);
                }
            }
        }
    }
}
