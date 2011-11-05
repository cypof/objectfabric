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

import of4gwt.Strings;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public class PlatformFuture<V> implements Future<V> {

    private boolean _isDone;

    public PlatformFuture() {
    }

    @SuppressWarnings("unused")
    public V get() throws InterruptedException, ExecutionException {
        throw new IllegalStateException(Strings.THREAD_BLOCKING_DISALLOWED);
    }

    public boolean isDone() {
        return _isDone;
    }

    protected void done() {
    }

    protected final void setDone() {
        if (!_isDone)
            done();

        _isDone = true;
    }

    /**
     * @param value  
     */
    protected void set(V value) {
        setDone();
    }

    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }
}
