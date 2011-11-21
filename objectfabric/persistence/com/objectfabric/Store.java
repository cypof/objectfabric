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

import java.util.concurrent.Future;

import com.objectfabric.TObject.UserTObject;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.List;
import com.objectfabric.misc.RuntimeIOException;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

/**
 * A store can persist or cache objects to a durable medium like a disk or database. A
 * store contains a root and all objects referenced by the root. When an object is
 * modified in memory, it is automatically updated in the store. The in-memory transaction
 * is only acknowledged when the store is done with the commit, which for a file-backed
 * store means after a successful java.io.FileDescriptor.sync(). Each trunk can have only
 * one store, but a store can contain many trunks.
 * <nl>
 * TODO: hide and unify stores & remote shares
 */
public abstract class Store extends Acknowledger {

    private DefaultRunnable _run;

    protected DefaultRunnable getRun() {
        return _run;
    }

    protected void setRun(DefaultRunnable value) {
        _run = value;
    }

    @SingleThreaded
    protected abstract void getRootAsync(FutureWithCallback<Object> future);

    @SingleThreaded
    protected abstract void setRootAsync(Object value, FutureWithCallback<Void> future);

    @SingleThreaded
    protected abstract void getAsync(UserTObject object, Object key, FutureWithCallback<Object> future);

    // For indexes

    @SingleThreaded
    protected abstract void insertAsync(Object object, AsyncCallback<byte[]> ref);

    @SingleThreaded
    protected abstract void fetchAsync(List<byte[]> refs, AsyncCallback<Object[]> objects);

    //

    /**
     * Blocks current thread to until store is done with all pending operations.
     */
    public final void flush() {
        OF.updateAsync();

        Future<Void> flush = _run.startFlush();

        if (requestRun())
            OF.getConfig().wait(flush);
    }

    public final Object getRoot() throws RuntimeIOException {
        FutureWithCallback<Object> result = getRootAsync();

        try {
            return result.get();
        } catch (Exception e) {
            throw new RuntimeIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public final FutureWithCallback<Object> getRootAsync() {
        return getRootAsync(FutureWithCallback.NOP_CALLBACK);
    }

    public final FutureWithCallback<Object> getRootAsync(AsyncCallback<Object> callback) {
        return getRootAsync(callback, null);
    }

    public final FutureWithCallback<Object> getRootAsync(AsyncCallback<Object> callback, AsyncOptions options) {
        final FutureWithCallback<Object> future = new FutureWithCallback<Object>(callback, options);

        getRun().execute(new Runnable() {

            public void run() {
                getRootAsync(future);
            }
        });

        return future;
    }

    public final void setRoot(Object value) throws RuntimeIOException {
        FutureWithCallback<Void> result = setRootAsync(value);

        try {
            result.get();
        } catch (Exception e) {
            ExpectedExceptionThrower.throwRuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public final FutureWithCallback<Void> setRootAsync(Object value) {
        return setRootAsync(value, FutureWithCallback.NOP_CALLBACK);
    }

    public final FutureWithCallback<Void> setRootAsync(Object value, AsyncCallback<Void> callback) {
        return setRootAsync(value, callback, null);
    }

    public final FutureWithCallback<Void> setRootAsync(final Object value, AsyncCallback<Void> callback, AsyncOptions options) {
        final FutureWithCallback<Void> future = new FutureWithCallback<Void>(callback, options);

        getRun().execute(new Runnable() {

            public void run() {
                setRootAsync(value, future);
            }
        });

        return future;
    }

    /**
     * WARNING: Do not use. Stores record changes on objects after they have been loaded,
     * so they must run as long as objects are live. This method is for testing purposes
     * only for now.
     * <nl>
     * TODO Disconnect and block branches, abort pending gets etc.
     */
    public void close() {
        flush();

        _run.add(new Runnable() {

            public void run() {
                ExpectedExceptionThrower.expectException();
                ExpectedExceptionThrower.throwExtensionShutdownException();
            }
        });

        // Another flush to wait until files closed etc.
        flush();
    }
}
