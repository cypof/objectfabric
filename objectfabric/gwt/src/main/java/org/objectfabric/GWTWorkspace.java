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

/**
 * Workspace for a GWT application. This configuration executes notifications directly
 * instead of on a thread pool or passing to the UI thread as would be done in a JVM.
 */
public class GWTWorkspace extends Workspace {

    private static Executor _transparent = new Executor() {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    static {
        GWTPlatform.loadClass();
    }

    public GWTWorkspace() {
        super(Granularity.COALESCE);
    }

    @Override
    protected Executor createCallbackExecutor() {
        return _transparent;
    }
}