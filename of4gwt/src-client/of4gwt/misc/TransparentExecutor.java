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

package of4gwt.misc;

import of4gwt.Privileged;

/**
 * Specify this executor to execute a method on a transactional object on the current
 * thread. This is the default executor for methods executing on local objects.
 */
public class TransparentExecutor extends Privileged implements Executor {

    private static final Executor _instance = PlatformAdapter.createTransparentExecutor();

    protected TransparentExecutor() {
    }

    public static Executor getInstance() {
        return _instance;
    }

    public void execute(Runnable runnable) {
        executeStatic(runnable);
    }

    public static void executeStatic(Runnable runnable) {
        Object key;
        boolean noTransaction;

        if (Debug.ENABLED) {
            key = new Object();
            ThreadAssert.suspend(key);
            noTransaction = getNoTransaction();
            setNoTransaction(false);
        }

        runnable.run();

        if (Debug.ENABLED) {
            ThreadAssert.resume(key);
            setNoTransaction(noTransaction);
        }
    }
}