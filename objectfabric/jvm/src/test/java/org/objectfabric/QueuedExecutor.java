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

import java.util.ArrayList;
import java.util.concurrent.Executor;

import org.objectfabric.PlatformThreadLocal;

public class QueuedExecutor implements Executor {

    private static final QueuedExecutor _instance = new QueuedExecutor();

    private static final PlatformThreadLocal<ArrayList<Runnable>> _runnables = new PlatformThreadLocal<ArrayList<Runnable>>();

    private QueuedExecutor() {
    }

    public static QueuedExecutor getInstance() {
        return _instance;
    }

    public static void run() {
        ArrayList<Runnable> runnables = _runnables.get();

        if (runnables != null) {
            for (Runnable runnable : runnables)
                runnable.run();

            runnables.clear();
        }
    }

    public void execute(Runnable runnable) {
        ArrayList<Runnable> runnables = _runnables.get();

        if (runnables == null)
            _runnables.set(runnables = new ArrayList<Runnable>());

        runnables.add(runnable);
    }
}