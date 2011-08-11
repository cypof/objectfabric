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

import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;

final class AsyncAtomic extends FutureWithCallback<CommitStatus> {

    private final Transaction _outer;

    private final Runnable _runnable;

    private final Executor _executor;

    private final int _flags;

    public AsyncAtomic(Transaction outer, Runnable runnable, AsyncCallback<CommitStatus> callback, AsyncOptions asyncOptions, Executor executor, int flags) {
        super(callback, asyncOptions);

        _outer = outer;
        _runnable = runnable;
        _executor = executor;
        _flags = flags;

        if (!_outer.isPublic())
            Transaction.setCurrentAssertNotAlready(_outer.getBranch());
    }

    public void start() {
        _executor.execute(this);
    }

    @Override
    public void run() {
        if (Debug.ENABLED)
            Debug.assertion(Transaction.getCurrent() == null);

        // If done, raise the callback
        if (isDone())
            super.run();
        else // Else, run the code
        {
            Transaction transaction = _outer.startChild(_flags);

            try {
                _runnable.run();

                for (;;) {
                    Transaction current = Transaction.getCurrent();

                    if (current == transaction)
                        break;

                    if (current == null) {
                        Transaction.setCurrent(transaction);
                        break;
                    }

                    Log.write(Strings.USER_CODE_CHANGED_CURRENT_TRANSACTION);
                    current.abort();
                }

                transaction.commitWithoutCurrentCheck(this);
            } catch (Throwable t) {
                /*
                 * Transaction might have been aborted already.
                 */
                if (transaction.getSnapshot() != null)
                    transaction.abort();

                setException(t);
            }
        }
    }

    @Override
    public void set(CommitStatus value) {
        // If conflict, intercept and retry
        if (value == CommitStatus.CONFLICT)
            start();
        else
            super.set(value);
    }
}
