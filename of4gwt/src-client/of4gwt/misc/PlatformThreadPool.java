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

import com.google.gwt.user.client.Timer;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public final class PlatformThreadPool {

    private PlatformThreadPool() {
    }

    public static final Executor getInstance() {
        return TransparentExecutor.getInstance();
    }

    public static Future schedule(final Runnable command, int ms) {
        TimerFuture timer = new TimerFuture() {

            @Override
            public void run() {
                command.run();
            }
        };

        timer.schedule(ms);
        return timer;
    }

    private static abstract class TimerFuture extends Timer implements Future {

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            super.cancel();
            return true;
        }

        @Override
        public boolean isCancelled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDone() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException();
        }
    }
}
