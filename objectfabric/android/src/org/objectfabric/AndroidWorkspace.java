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

import java.util.concurrent.Executor;

import android.view.View;

/**
 * Workspace for Android applications. This configuration executes notifications on the UI
 * thread and logs using android.util.Log.i().
 */
public class AndroidWorkspace extends JVMWorkspace {

    private final View _view;

    static {
        Log.set(new Log() {

            @Override
            protected void log(String message) {
                android.util.Log.i("", message);
            }
        });
    }

    public AndroidWorkspace(View view) {
        _view = view;
    }

    @Override
    protected Executor createCallbackExecutor() {
        return new Executor() {

            public void execute(final Runnable runnable) {
                _view.post(runnable);
            }
        };
    }
}