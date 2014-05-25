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

/**
 * Result of an asynchronous method call.
 */
class MethodFuture<V> extends FutureWithCallback<V> implements IndexListener {

    private final MethodCall<V> _call;

    protected MethodFuture(MethodCall<V> call) {
        this(call, null);
    }

    protected MethodFuture(MethodCall<V> call, AsyncCallback<V> callback) {
        super(callback, call.workspace().callbackExecutor());

        _call = call;

        _call.addListener(this);
        checkDone();
    }

    @Override
    protected void done() {
        _call.removeListener(this);
        super.done();
    }

    public final MethodCall<V> getMethodCall() {
        return _call;
    }

    @Override
    public void onSet(int i) {
        checkDone();
    }

    @SuppressWarnings("unchecked")
    private final void checkDone() {
        if (_call.isDone()) {
            String exception = _call.exception();

            if (exception != null)
                setException(new Exception(exception));
            else
                set((V) _call.result_());
        }
    }
}