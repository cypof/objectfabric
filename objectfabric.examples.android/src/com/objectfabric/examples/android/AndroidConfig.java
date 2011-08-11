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

package com.objectfabric.examples.android;

import java.util.concurrent.Executor;

import android.os.Looper;
import android.view.View;

import com.objectfabric.AsyncOptions;
import com.objectfabric.OF.AutoCommitPolicy;
import com.objectfabric.OF.Config;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;

/**
 * Configures ObjectFabric for an Android application. This configuration delays commits
 * of transactions that are automatically started by the UI thread (C.f.
 * createAutoCommitPolicy()) and executes notifications on the UI thread.
 */
public class AndroidConfig extends Config {

    private final View _view;

    public AndroidConfig(View view, boolean log) {
        _view = view;

        if (log) {
            Log.add(new Log() {

                @Override
                protected void onWrite(String message) {
                    android.util.Log.i("", message);
                }
            });
        }
    }

    @Override
    public AutoCommitPolicy getAutoCommitPolicy() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread())
            return AutoCommitPolicy.DELAYED;

        return super.getAutoCommitPolicy();
    }

    @Override
    public void delayCommit(Runnable runnable) {
        if (Debug.ENABLED)
            Debug.assertion(Thread.currentThread() == Looper.getMainLooper().getThread());

        _view.post(runnable);
    }

    @Override
    public AsyncOptions createAsyncOptions() {
        return new AndroidAsyncOptions();
    }

    private final class AndroidAsyncOptions extends AsyncOptions implements Executor {

        @Override
        public Executor getExecutor() {
            return this;
        }

        public void execute(final Runnable runnable) {
            _view.post(runnable);
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