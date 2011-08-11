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

package of4gwt;

import of4gwt.misc.Future;


import of4gwt.TObject.UserTObject;
import com.google.gwt.user.client.rpc.AsyncCallback;
import of4gwt.misc.CompletedFuture;

/**
 * Method calls on transactional objects. Call methods <code>set</code> and
 * <code>setException</code> to report the result of an asynchronous method.
 */
public abstract class MethodCall extends FutureWithCallback {

    private static final Future CANCELLED = new CompletedFuture<Void>(null);

    private static final Future CANCELLED_INTERRUPT = new CompletedFuture<Void>(null);

    private final UserTObject _target;

    private final UserTObject _method;

    // TODO: Replace by method index on ObjectModel
    private final int _index;

    private final Transaction _transaction;

    // To identify the call in distributed settings
    private Transaction _fakeTransaction;

    private TObject.Version _methodVersion;

    private volatile Future _userFuture;

    

    private TObject.Version[] _callbackWrites;

    static {
        
    }

    /**
     * For local methods.
     */
    @SuppressWarnings("unchecked")
    protected MethodCall(UserTObject target, UserTObject method, int index, AsyncCallback callback, AsyncOptions options) {
        super(callback, options);

        if (target == null || method == null)
            throw new IllegalArgumentException();

        _target = target;
        _method = method;
        _index = index;

        _transaction = Transaction.getCurrent();
    }

    /**
     * For remote methods.
     */
    @SuppressWarnings("unchecked")
    protected MethodCall(UserTObject target, UserTObject method, int index, Transaction transaction) {
        super(NOP_CALLBACK, null);

        if (target == null || method == null)
            throw new IllegalArgumentException();

        _target = target;
        _method = method;
        _index = index;
        _transaction = transaction;
    }

    final UserTObject getTarget() {
        return _target;
    }

    final UserTObject getMethod() {
        return _method;
    }

    final int getIndex() {
        return _index;
    }

    final Transaction getTransaction() {
        return _transaction;
    }

    final Transaction getFakeTransaction() {
        return _fakeTransaction;
    }

    final void setFakeTransaction(Transaction value) {
        _fakeTransaction = value;
    }

    final TObject.Version getMethodVersion() {
        return _methodVersion;
    }

    final void setMethodVersion(TObject.Version value) {
        _methodVersion = value;
    }

    final TObject.Version[] getCallbackWrites() {
        return _callbackWrites;
    }

    final void setCallbackWrites(TObject.Version[] value) {
        _callbackWrites = value;
    }

    @Override
    public final void set(Object value) {
        set(value, false);
    }

    @SuppressWarnings("unchecked")
    protected void set(Object value, boolean direct) {
        super.set(value);
    }

    @Override
    public final void setException(Throwable t) {
        setException(t, false);
    }

    protected void setException(Throwable t, boolean direct) {
        super.setException(t);
    }

    /**
     * If your method code is represented by a future, assign it using this method so the
     * system can cancel the computation if requested by the caller.
     */
    public final void setFuture(Future future) {
        for (;;) {
            Future current = _userFuture;

            if (current != null) {
                if (current == CANCELLED) {
                    future.cancel(false);
                    break;
                }

                if (current == CANCELLED_INTERRUPT) {
                    future.cancel(true);
                    break;
                }

                throw new RuntimeException(Strings.ONLY_ONCE);
            }

            if ((_userFuture == null ? ((_userFuture = future) == future) : false))
                break;
        }
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        for (;;) {
            Future current = _userFuture;

            if (current != null)
                return current.cancel(mayInterruptIfRunning);

            if ((_userFuture == null ? ((_userFuture = (mayInterruptIfRunning ? CANCELLED_INTERRUPT : CANCELLED)) == (mayInterruptIfRunning ? CANCELLED_INTERRUPT : CANCELLED)) : false))
                return false;
        }
    }
}