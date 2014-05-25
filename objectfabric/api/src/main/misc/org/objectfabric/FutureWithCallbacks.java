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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Allows additional callbacks to be registered on a FutureWithCallback.
 */
@SuppressWarnings("rawtypes")
class FutureWithCallbacks<V> extends FutureWithCallback<V> {

    private static final Object DONE = new Object();

    private AtomicReference<Object> _callbacks = new AtomicReference<Object>();

    FutureWithCallbacks(AsyncCallback<V> callback, Executor callbackExecutor) {
        super(callback, callbackExecutor);
    }

    final void addCallback(AsyncCallback<V> callback, Executor callbackExecutor) {
        for (;;) {
            Object expect = _callbacks.get();

            if (expect == DONE) {
                execute(callback, callbackExecutor);
                break;
            }

            CallBackQueue update = new CallBackQueue(callback, callbackExecutor, (CallBackQueue) expect);

            if (_callbacks.compareAndSet(expect, update))
                break;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void done() {
        super.done();

        CallBackQueue queue;

        for (;;) {
            queue = (CallBackQueue) _callbacks.get();

            if (_callbacks.compareAndSet(queue, DONE))
                break;
        }

        while (queue != null) {
            execute(queue.Callback, queue.CallbackExecutor);
            queue = queue.Next;
        }
    }

    private final void execute(AsyncCallback<V> callback, Executor callbackExecutor) {
        V result = null;
        Exception ex = null;

        try {
            result = (V) get();
        } catch (Exception e) {
            ex = e;
        }

        execute(callback, callbackExecutor, result, ex);
    }

    static final <V> void execute(final AsyncCallback<V> callback, Executor callbackExecutor, final V result, final Exception ex) {
        Executor executor;

        if (callback instanceof AsyncCallbackWithExecutor)
            executor = ((AsyncCallbackWithExecutor) callback).executor();
        else
            executor = callbackExecutor;

        executor.execute(new Runnable() {

            @Override
            public void run() {
                invoke(callback, result, ex);
            }
        });
    }

    private static final class CallBackQueue {

        AsyncCallback Callback;

        Executor CallbackExecutor;

        CallBackQueue Next;

        public CallBackQueue(AsyncCallback callback, Executor callbackExecutor, CallBackQueue next) {
            if (callback == null)
                throw new IllegalArgumentException();

            Callback = callback;
            CallbackExecutor = callbackExecutor;
            Next = next;
        }
    }
}
