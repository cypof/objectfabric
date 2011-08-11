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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import com.objectfabric.CompileTimeSettings;
import com.objectfabric.Strings;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public class PlatformFuture<V> extends FutureTask<V> {

    public PlatformFuture() {
        super(new Callable<V>() {

            public V call() throws Exception {
                return null;
            }
        });
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        if (CompileTimeSettings.DISALLOW_THREAD_BLOCKING) {
            // Should be thrown before by caller
            throw new RuntimeException(Strings.THREAD_BLOCKING_DISALLOWED);
        }

        return super.get();
    }
}
