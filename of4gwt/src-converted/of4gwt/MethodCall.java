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

import of4gwt.TObject.UserTObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Method calls on transactional objects. Call methods <code>set</code> and
 * <code>setException</code> to report the result of an asynchronous method.
 */
public abstract class MethodCall extends MethodCallBase {

    private final UserTObject _target;

    private final UserTObject _method;

    // TODO: Replace by method index on ObjectModel
    private final int _index;

    private final Transaction _transaction;

    // To identify the call in distributed settings
    private Transaction _fakeTransaction;

    private TObject.Version _methodVersion;

    private TObject.Version[] _callbackWrites;

    /**
     * For local methods.
     */
    protected MethodCall(UserTObject target, UserTObject method, int index, AsyncCallback callback, AsyncOptions asyncOptions) {
        super(callback, asyncOptions);

        if (target == null || method == null)
            throw new IllegalArgumentException();

        _target = target;
        _method = method;
        _index = index;

        OF.updateAsync();

        _transaction = Transaction.getCurrent();
    }

    /**
     * For remote methods.
     */
    protected MethodCall(UserTObject target, UserTObject method, int index, Transaction transaction) {
        super(FutureWithCallback.NOP_CALLBACK, null);

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

    /**
     * TODO: replace by branches
     */
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
}