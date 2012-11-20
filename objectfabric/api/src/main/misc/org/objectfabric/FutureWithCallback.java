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
 * Combines a callback and a future. When the async event occurs, the future is set and
 * callback raised with the result.
 */
class FutureWithCallback<V> extends PlatformFuture<V> {

    /**
     * Avoids logging an exception when it would be raised by waiting on the future.
     */
    @SuppressWarnings("rawtypes")
    public static final AsyncCallback NOP_CALLBACK = new AsyncCallback() {

        @Override
        public void onSuccess(Object result) {
        }

        @Override
        public void onFailure(Exception e) {
        }
    };

    private AsyncCallback<V> _callback;

    private Executor _callbackExecutor;

    FutureWithCallback(AsyncCallback<V> callback, Executor callbackExecutor) {
        if (Debug.ENABLED)
            if (callback != null && callback != NOP_CALLBACK)
                Helper.instance().addCallback(callback);

        _callback = callback;
        _callbackExecutor = callbackExecutor;
    }

    @Override
    public void setException(Exception e) {
        super.setException(e);

        if (_callback != null) {
            if (Debug.ENABLED)
                if (!(e instanceof ClosedException) && !(e instanceof SecurityException))
                    Log.write(e);
        } else {
            Platform platform = Platform.get();
            Log.write(Strings.NO_CALLBACK_FOR_EXCEPTION + platform.lineSeparator() + platform.getStackAsString(e));
        }
    }

    @Override
    protected void done() {
        super.done();

        if (_callback != null && _callback != NOP_CALLBACK) {
            Executor executor;

            if (_callback instanceof AsyncCallbackWithExecutor)
                executor = ((AsyncCallbackWithExecutor<V>) _callback).executor();
            else
                executor = _callbackExecutor;

            executor.execute(this);
        }
    }

    @Override
    public void run() {
        if (Debug.ENABLED) {
            Debug.assertion(isDone());
            Debug.assertion(_callback != null);
            Helper.instance().removeCallback(_callback);
        }

        invoke(_callback);
    }

    final void invoke(AsyncCallback<V> callback) {
        V result = null;
        Exception ex = null;

        try {
            result = (V) get();
        } catch (Exception e) {
            ex = e;
        }

        invoke(callback, result, ex);
    }

    static final <V> void invoke(AsyncCallback<V> callback, V result, Exception ex) {
        try {
            if (ex == null)
                callback.onSuccess(result);
            else
                callback.onFailure(ex);
        } catch (Exception e) {
            Log.userCodeException(e);
        }
    }
}
