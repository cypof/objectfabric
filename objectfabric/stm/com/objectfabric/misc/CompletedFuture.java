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

package com.objectfabric.misc;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This future simply returns its given value. Useful when a future is needed and the
 * corresponding value has already been computed.
 */
public final class CompletedFuture<V> implements Future<V> {

    private final V _value;

    public CompletedFuture(V value) {
        _value = value;
    }

    //

    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    public V get() throws ExecutionException {
        return _value;
    }

    public V getDirect() {
        return _value;
    }

    public V get(long timeout, TimeUnit unit) throws ExecutionException {
        return get();
    }

    public boolean isCancelled() {
        return false;
    }

    public boolean isDone() {
        return true;
    }
}
