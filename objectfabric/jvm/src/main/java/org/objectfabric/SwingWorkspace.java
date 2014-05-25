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

import javax.swing.SwingUtilities;

/**
 * Executes notifications and callbacks on the Swing UI thread.
 */
public class SwingWorkspace extends JVMWorkspace {

    @Override
    protected Executor createCallbackExecutor() {
        return new Executor() {

            @Override
            public void execute(Runnable runnable) {
                if (SwingUtilities.isEventDispatchThread())
                    runnable.run();
                else
                    SwingUtilities.invokeLater(runnable);
            }
        };
    }
}