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

import java.util.concurrent.Future;

import com.objectfabric.misc.CheckedRunnable;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformConcurrentQueue;
import com.objectfabric.misc.PlatformFuture;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

/**
 * Base class for any single threaded components which can receive tasks from other
 * threads. ObjectFabric is structured as a set of actors running on a thread pool.
 */
@SingleThreaded
abstract class Actor extends CheckedRunnable {

    private final PlatformConcurrentQueue<Runnable> _runnables = new PlatformConcurrentQueue<Runnable>();

    // TODO: unify with _runnables?
    private final PlatformConcurrentQueue<Flush> _pendingFlushes = new PlatformConcurrentQueue<Flush>();

    private final List<Flush> _currentFlushes = new List<Flush>();

    /**
     * @param e
     */
    public void onException(Exception e) {
        for (;;) {
            Flush flush = _pendingFlushes.poll();

            if (flush == null)
                break;

            flush.setResult();
        }

        for (int i = 0; i < _currentFlushes.size(); i++)
            _currentFlushes.get(i).setResult();
    }

    public Future<Void> startFlush() {
        Flush future = new Flush();
        _pendingFlushes.add(future);
        return future;
    }

    public final void add(Runnable runnable) {
        _runnables.add(runnable);
    }

    protected final void runTasks() {
        if (Debug.ENABLED)
            Debug.assertion(_currentFlushes.size() == 0);

        for (;;) {
            Flush flush = _pendingFlushes.poll();

            if (flush == null)
                break;

            _currentFlushes.add(flush);
        }

        for (;;) {
            Runnable runnable = _runnables.poll();

            if (runnable == null)
                break;

            runnable.run();
        }
    }

    protected final void setFlushes() {
        if (_currentFlushes.size() > 0) {
            for (int i = 0; i < _currentFlushes.size(); i++)
                _currentFlushes.get(i).setResult();

            _currentFlushes.clear();
        }
    }

    private static final class Flush extends PlatformFuture<Void> {

        public void setResult() {
            set(null);
        }
    }
}
