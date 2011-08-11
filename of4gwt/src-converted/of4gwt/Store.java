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

import of4gwt.Index.Insert;
import of4gwt.TObject.UserTObject;
import of4gwt.misc.ThreadAssert.SingleThreaded;

/**
 * A store can persist or cache objects to a durable medium like a disk or database. A
 * store contains a root and all objects referenced by the root. When an object is
 * modified in memory, it is automatically updated in the store. The in-memory transaction
 * is only acknowledged when the store is done with the commit, which for a file-backed
 * store means after a successful java.io.FileDescriptor.sync(). Each trunk can have only
 * one store, but a store can contain many trunks.
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
    protected abstract void getAsync(byte[] ref, FutureWithCallback<Object> future);

    @SingleThreaded
    protected abstract void getAsync(UserTObject object, Object key, FutureWithCallback<Object> future);

    @SingleThreaded
    protected abstract void insert(Insert insert);

    /**
     * Does not influence the behavior of the store, simply allows the current thread to
     * wait until store is done with all pending operations.
     */
    public final void flush() {
        Flush flush = _run.startFlush();

        if (requestRun())
            OF.getConfig().wait(flush);
    }

    /**
     * WARNING: Do not use. Stores record changes on objects after they have been loaded,
     * so they must run as long as objects are live. This method is mostly for testing
     * purposes for now.
     * <nl>
     * TODO Disconnect and block branches, abort pending gets etc.
     */
    public void close() {
        flush();

        _run.add(new Runnable() {

            public void run() {
                ExpectedExceptionThrower.expectException();
                ExpectedExceptionThrower.throwStoreCloseException();
            }
        });

        // Another flush to wait until files closed etc.
        flush();
    }
}
