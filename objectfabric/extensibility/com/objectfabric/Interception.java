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

import java.util.concurrent.atomic.AtomicReference;

import com.objectfabric.OverloadHandler;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.WritableFuture;

/**
 * Transactions can be intercepted by extensions before they commit.
 */
abstract class Interception {

    private final WritableFuture<CommitStatus> _async;

    private byte _id;

    static {
        if (Debug.ENABLED) {
            int maxId = 0xff;
            Debug.assertion(OverloadHandler.MAP_QUEUE_SIZE_MAXIMUM < maxId);
        }
    }

    protected Interception(WritableFuture<CommitStatus> async) {
        if (async == null)
            throw new IllegalArgumentException();

        _async = async;
    }

    public final WritableFuture<CommitStatus> getAsync() {
        return _async;
    }

    public final byte getId() {
        return _id;
    }

    public final void setId(byte value) {
        _id = value;
    }

    public static final class SingleMapInterception extends Interception {

        public SingleMapInterception(WritableFuture<CommitStatus> async) {
            super(async);
        }
    }

    public static final class MultiMapInterception extends Interception {

        private final AtomicReference<Object> _callbacks = new AtomicReference<Object>();

        public MultiMapInterception() {
            // Create default for transaction without callback
            super(PlatformAdapter.createCommitStatusFuture());
        }

        public CommitStatus tryToAddCallback(WritableFuture<CommitStatus> async) {
            for (;;) {
                Object current = _callbacks.get();

                if (current instanceof CommitStatus) {
                    // Already acknowledged
                    return (CommitStatus) current;
                }

                CallBackQueue updated = new CallBackQueue(async, (CallBackQueue) current);

                if (_callbacks.compareAndSet(current, updated))
                    return null;
            }
        }

        public void setCallbacks(CommitStatus result, Throwable throwable) {
            Object current = null;

            for (;;) {
                current = _callbacks.get();

                if (current instanceof CommitStatus)
                    // Should be the only thread raising
                    throw new IllegalStateException();

                if (_callbacks.compareAndSet(current, result))
                    break;
            }

            CallBackQueue queue = (CallBackQueue) current;

            while (queue != null) {
                if (throwable == null)
                    queue.getAsync().set(result);
                else
                    queue.getAsync().setException(throwable);

                queue = queue.getNext();
            }
        }
    }

    private static final class CallBackQueue {

        private final WritableFuture<CommitStatus> _async;

        private final CallBackQueue _next;

        public CallBackQueue(WritableFuture<CommitStatus> async, CallBackQueue next) {
            if (async == null)
                throw new IllegalArgumentException();

            _async = async;
            _next = next;
        }

        public WritableFuture<CommitStatus> getAsync() {
            return _async;
        }

        public CallBackQueue getNext() {
            return _next;
        }
    }
}