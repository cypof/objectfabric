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
class FutureWithCallback<V> extends PlatformFuture<V> implements WritableFuture<V> {

    /**
     * Avoids logging an exception when it would be raised by waiting on the future.
     */
    public static final AsyncCallback NOP_CALLBACK = new AsyncCallback() {

        public void onSuccess(Object result) {
        }

        public void onFailure(Exception e) {
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
    }

    @Override
    protected final void setException(Throwable t) {
        setException((Exception) t);
    }

    public void setException(Exception e) {
        super.setException(e);

        if (_callback != null) {
            if (Debug.ENABLED)
                Log.write(Strings.EXCEPTION_ON_USER_METHOD, e);
        } else
            Log.write(Strings.NO_CALLBACK_FOR_EXCEPTION + Utils.NEW_LINE + PlatformAdapter.getStackAsString(e));
    }

    @Override
    protected void done() {
        super.done();

        if (_callback != null && _callback != NOP_CALLBACK) {
            Executor executor = getAsyncOptions().getExecutor();
            executor.execute(this);
        }
    }

    @Override
    public void run() {
        try {
            if (Debug.ENABLED) {
                Debug.assertion(isDone());
                Debug.assertion(_callback != null);
                Helper.getInstance().removeCallback(_callback);
            }

            V result = null;
            Exception ex = null;

            try {
                result = get();
            } catch (Exception e) {
                ex = e;
            }

            try {
                if (ex == null)
                    _callback.onSuccess(result);
                else
                    _callback.onFailure(ex);
            } catch (Exception e) {
                PlatformAdapter.logUserCodeException(e);
            }
        } catch (Throwable t) {
            OF.getConfig().onThrowable(t);
        }
    }
}
