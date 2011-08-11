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

import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFuture;
import com.objectfabric.misc.Utils;
import com.objectfabric.misc.WritableFuture;

/**
 * Combines a callback and a future. When the async event occurs, the future is set and
 * callback raised with the result.
 */
class FutureWithCallback<V> extends PlatformFuture<V> implements WritableFuture<V>, Runnable {

    /**
     * Avoids logging an exception when it would be raised by waiting on the future.
     */
    public static final AsyncCallback NOP_CALLBACK = new AsyncCallback() {

        public void onSuccess(Object result) {
        }

        public void onFailure(Throwable t) {
        }
    };

    private final AsyncCallback<V> _callback;

    private final AsyncOptions _options;

    public FutureWithCallback(AsyncCallback<V> callback, AsyncOptions options) {
        if (Debug.ENABLED)
            if (callback != null && callback != NOP_CALLBACK)
                Helper.getInstance().addCallback(callback);

        _callback = callback;
        _options = options;
    }

    private final AsyncOptions getAsyncOptions() {
        if (_options == null)
            return OF.getDefaultAsyncOptions();

        return _options;
    }

    @Override
    public void set(V value) {
        super.set(value);

        if (_callback != null && _callback != NOP_CALLBACK) {
            Executor executor = getAsyncOptions().getExecutor();
            executor.execute(this);
        }
    }

    @Override
    public void setException(Throwable t) {
        super.setException(t);

        if (_callback != null) {
            if (_callback != NOP_CALLBACK) {
                Executor executor = getAsyncOptions().getExecutor();
                executor.execute(this);
            }
        } else
            Log.write(Strings.NO_CALLBACK_FOR_EXCEPTION + Utils.NEW_LINE + PlatformAdapter.getStackAsString(t));
    }

    @Override
    public void run() {
        if (Debug.ENABLED) {
            Debug.assertion(isDone());
            Debug.assertion(_callback != null);
            Helper.getInstance().removeCallback(_callback);
        }

        V result = null;
        Throwable throwable = null;

        try {
            result = get();
        } catch (Throwable t) {
            throwable = t;
        }

        try {
            if (throwable == null)
                _callback.onSuccess(result);
            else
                _callback.onFailure(throwable);
        } catch (Throwable ex) {
            PlatformAdapter.logListenerException(ex);
        }
    }
}
