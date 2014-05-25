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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

class PlatformFuture<V> extends FutureTask<V> {

    public PlatformFuture() {
        super(new Callable<V>() {

            @Override
            public V call() throws Exception {
                return null;
            }
        });
    }

    final void getVoid() {
        try {
            get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        if (Debug.THREAD_LOG_BLOCKING)
            Log.write("Thread blocked");

        V v = super.get();

        if (Debug.THREAD_LOG_BLOCKING)
            Log.write("Thread unblocked");

        return v;
    }

    @Override
    public void set(V value) {
        super.set(value);
    }

    @Override
    protected final void setException(Throwable t) {
        setException((Exception) t);
    }

    public void setException(Exception e) {
        super.setException(e);
    }
}
