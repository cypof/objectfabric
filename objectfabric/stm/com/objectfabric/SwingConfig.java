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

import java.util.concurrent.Executor;

import javax.swing.SwingUtilities;

import com.objectfabric.OF.AutoCommitPolicy;
import com.objectfabric.OF.Config;

/**
 * Configures ObjectFabric for a Swing application. This configuration delays commits of
 * transactions that are automatically started by the UI thread (C.f.
 * createAutoCommitPolicy()) and executes notifications on the UI thread.
 */
public class SwingConfig extends Config {

    @Override
    public AutoCommitPolicy getAutoCommitPolicy() {
        if (SwingUtilities.isEventDispatchThread())
            return AutoCommitPolicy.DELAYED;

        return super.getAutoCommitPolicy();
    }

    @Override
    public void delayCommit(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    @Override
    public AsyncOptions createAsyncOptions() {
        return new SwingAsyncOptions();
    }

    private static final class SwingAsyncOptions extends AsyncOptions implements Executor {

        @Override
        public Executor getExecutor() {
            return this;
        }

        public void execute(final Runnable runnable) {
            if (SwingUtilities.isEventDispatchThread())
                runnable.run();
            else
                SwingUtilities.invokeLater(runnable);
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