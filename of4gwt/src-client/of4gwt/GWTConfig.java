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

package of4gwt;

import of4gwt.OF.AutoCommitPolicy;
import of4gwt.OF.Config;
import of4gwt.misc.Debug;
import of4gwt.misc.Executor;
import of4gwt.misc.TransparentExecutor;

import com.google.gwt.user.client.Timer;

/**
 * Configures ObjectFabric for a GWT application. This configuration delays commits of
 * transactions that are automatically started (C.f. createAutoCommitPolicy()) and
 * executes notifications directly instead of on a thread pool as would be done in a JVM.
 */
public class GWTConfig extends Config {

    private Timer _timer;

    @Override
    public AutoCommitPolicy getAutoCommitPolicy() {
        return AutoCommitPolicy.DELAYED;
    }

    @Override
    public void delayCommit(final Runnable runnable) {
        if (Debug.ENABLED)
            Debug.assertion(_timer == null);

        _timer = new Timer() {

            @Override
            public void run() {
                runnable.run();
            }
        };

        _timer.schedule(1);
    }

    @Override
    void onCommit() {
        _timer = null;
    }

    @Override
    public AsyncOptions createAsyncOptions() {
        return new GWTAsyncOptions();
    }

    public static class GWTAsyncOptions extends AsyncOptions {

        @Override
        public Executor getExecutor() {
            return TransparentExecutor.getInstance();
        }

        @Override
        public boolean waitForListenersRegistration() {
            /*
             * Do not wait if called on dispatcher thread, it would block processing of
             * events so future might never get done.
             */
            return false;
        }
    }
}