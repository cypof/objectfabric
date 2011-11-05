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
import of4gwt.misc.AtomicInteger;

import of4gwt.Extension.TObjectMapEntry;
import of4gwt.OF.AutoCommitPolicy;
import of4gwt.OF.Config;
import of4gwt.TObject.UserTObject;
import of4gwt.Transaction.CommitStatus;
import com.google.gwt.user.client.rpc.AsyncCallback;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformThreadLocal;
import of4gwt.misc.SparseArrayHelper;
import of4gwt.misc.ThreadAssert.SingleThreaded;

/**
 * Stores transactions created automatically when TObjects are accessed without starting a
 * transaction first.
 */
@SingleThreaded
final class ThreadContext { // TODO merge with transactions' sets

    // TODO remove by deriving from Thread for server with threads created by OF
    private static final PlatformThreadLocal<ThreadContext> _current = new PlatformThreadLocal<ThreadContext>();

    private final List<Transaction> _branches = new List<Transaction>();

    @SuppressWarnings("unchecked")
    private TObjectMapEntry<Transaction>[] _transactions = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

    private Config _config;

    private AutoCommitPolicy _autoCommitPolicy;

    private boolean _autoCommitPending, _notifying;

    private final Run _run = new Run();

    public static final ThreadContext getCurrent() {
        ThreadContext current = _current.get();

        if (current == null)
            _current.set(current = new ThreadContext());

        return current;
    }

    public AutoCommitPolicy getPolicy() {
        return _autoCommitPolicy;
    }

    public void forceAutoCommitPolicy(AutoCommitPolicy value) {
        _autoCommitPolicy = value;
    }

    public boolean notifying() {
        return _notifying;
    }

    public void setNotifying(boolean value) {
        _notifying = value;
    }

    private void checkAutoCommitPolicy() {
        if (_autoCommitPolicy == null) {
            _config = OF.getConfig();
            _autoCommitPolicy = _config.getAutoCommitPolicy();

            if (_autoCommitPolicy == AutoCommitPolicy.NONE)
                throw new RuntimeException(Strings.AUTO_COMMIT_DISABLED);

            if (_autoCommitPolicy == AutoCommitPolicy.IMMEDIATE)
                if (CompileTimeSettings.DISALLOW_THREAD_BLOCKING)
                    throw new RuntimeException(Strings.THREAD_BLOCKING_DISALLOWED);
        }
    }

    public Transaction startAccess(Transaction outer, UserTObject object, boolean read) {
        checkAutoCommitPolicy();
        int flags = Transaction.FLAG_AUTO | Transaction.FLAG_NO_READS;

        if (read && (_autoCommitPolicy == AutoCommitPolicy.IMMEDIATE || _autoCommitPolicy == AutoCommitPolicy.IMMEDIATE_ASYNC))
            flags |= Transaction.FLAG_NO_WRITES;

        return saveCurrentAndStartTransaction(outer, object.getTrunk(), flags);
    }

    public void endAccess(Transaction inner, boolean commit) {
        checkAutoCommitPolicy();

        if (_autoCommitPolicy == AutoCommitPolicy.IMMEDIATE || _autoCommitPolicy == AutoCommitPolicy.IMMEDIATE_ASYNC) {
            if (commit) {
                Future<CommitStatus> result = inner.commitWithoutCurrentCheck(null);
                CommitStatus status = null;

                if (_autoCommitPolicy == AutoCommitPolicy.IMMEDIATE) {
                    try {
                        status = result.get();
                    } catch (Exception e) {
                        ExpectedExceptionThrower.throwRuntimeException(e);
                    }

                    if (status != CommitStatus.SUCCESS)
                        ExpectedExceptionThrower.throwRuntimeException(Strings.AUTO_COMMIT_FAILED);
                }
            } else
                inner.abortWithoutCurrentCheckNoStats();
        }
    }

    public Transaction startIteration(Transaction outer, UserTObject object) {
        checkAutoCommitPolicy();

        if (_autoCommitPolicy != AutoCommitPolicy.DELAYED_MANUAL && _autoCommitPolicy != AutoCommitPolicy.DELAYED)
            throw new RuntimeException(outer == null ? Strings.ITERATORS : Strings.WRONG_TRUNK);

        return startAccess(outer, object, false);
    }

    public Transaction saveCurrentAndStartTransaction(Transaction current, Transaction trunk, int flags) {
        if (!_autoCommitPending) {
            if (_autoCommitPolicy == AutoCommitPolicy.DELAYED)
                _config.delayCommit(_run);

            _autoCommitPending = true;
        }

        if (current != null) {
            if (!current.isAuto())
                ExpectedExceptionThrower.throwRuntimeException(Strings.WRONG_TRUNK);

            if (Debug.ENABLED)
                Debug.assertion(trunk != current.getTrunk());

            TObjectMapEntry<Transaction> entry = TObjectMapEntry.getEntry(_transactions, current.getTrunk());

            if (entry == null) {
                entry = new TObjectMapEntry<Transaction>(current.getTrunk(), null);
                _transactions = TObjectMapEntry.put(_transactions, entry);
            }

            if (entry.getValue() == null) {
                if (Debug.ENABLED)
                    Debug.assertion(!_branches.contains(current.getTrunk()));

                entry.setValue(current);
                _branches.add(current.getTrunk());
            } else if (Debug.ENABLED)
                Debug.assertion(entry.getValue() == current);
        }

        Transaction transaction = TObjectMapEntry.get(_transactions, trunk);

        if (transaction != null)
            return transaction;

        return trunk.startChild(flags);
    }

    private final class Run implements Runnable {

        public void run() {
            updateAsync(null);
        }
    }

    public final Future<CommitStatus> updateAsync(AsyncCallback<CommitStatus> callback, AsyncOptions asyncOptions) {
        UpdateFuture update = new UpdateFuture(callback, asyncOptions);
        updateAsync(update);
        return update;
    }

    public final void updateAsync(UpdateFuture update) {
        checkAutoCommitPolicy();
        _config.onCommit();

        if (_autoCommitPending) { // Avoids reentrancy
            _autoCommitPending = false;
            int count = _branches.size() + 1;

            Transaction current = Transaction.getCurrent();

            if (current != null && current.isAuto()) {
                TObjectMapEntry<Transaction> entry = TObjectMapEntry.getEntry(_transactions, current.getTrunk());

                if (entry != null && entry.getValue() != null) {
                    if (Debug.ENABLED)
                        Debug.assertion(entry.getValue() == current);

                    entry.setValue(null);
                    count--;
                }
            } else
                count--;

            if (update != null) {
                if (count == 0)
                    update.setDirectly(CommitStatus.SUCCESS);
                else
                    update.Count.set(count);
            }

            if (current != null && current.isAuto()) {
                TransactionManager.commit(current, update);
                Transaction.setCurrentUnsafe(null);
            }

            for (int i = _branches.size() - 1; i >= 0; i--) {
                Transaction branch = _branches.removeLast();
                TObjectMapEntry<Transaction> entry = TObjectMapEntry.getEntry(_transactions, branch);

                if (entry.getValue() != null) {
                    TransactionManager.commit(entry.getValue(), update);
                    entry.setValue(null);
                }
            }
        }
    }

    public final void abortAll() {
        Transaction current = Transaction.getCurrent();

        if (current != null) {
            TObjectMapEntry<Transaction> entry = TObjectMapEntry.getEntry(_transactions, current.getTrunk());

            if (entry != null) {
                if (entry.getValue() != null) {
                    if (Debug.ENABLED)
                        if (current.isAuto())
                            Debug.assertion(entry.getValue() == current);

                    entry.setValue(null);
                }
            }

            abort(current);
            Transaction.setCurrentUnsafe(null);
        }

        for (int i = 0; i < _branches.size(); i++) {
            Transaction branch = _branches.get(i);
            TObjectMapEntry<Transaction> entry = TObjectMapEntry.getEntry(_transactions, branch);

            if (entry != null && entry.getValue() != null) {
                abort(entry.getValue());
                entry.setValue(null);
            }
        }

        _branches.clear();
    }

    private final void abort(Transaction transaction) {
        for (;;) {
            Transaction parent = transaction.getParent();
            TransactionManager.abort(transaction);

            if (parent.isPublic())
                break;

            transaction = parent;
        }
    }

    private static final class UpdateFuture extends FutureWithCallback<CommitStatus> {

        public final AtomicInteger Count = new AtomicInteger();

        private Exception _exception;

        public UpdateFuture(AsyncCallback<CommitStatus> callback, AsyncOptions options) {
            super(callback, options);
        }

        @Override
        public void set(CommitStatus value) {
            if (Count.decrementAndGet() == 0) {
                if (_exception == null)
                    super.set(value);
                else
                    super.setException(_exception);
            }
        }

        public void setDirectly(CommitStatus value) {
            super.set(value);
        }

        @Override
        public void setException(Exception e) {
            if (Count.decrementAndGet() == 0)
                super.setException(e);
            else
                _exception = e;
        }
    }

    // Debug

    public final void assertEmpty() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Transaction.assertCurrentNull();

        for (int i = 0; i < _branches.size(); i++) {
            Transaction branch = _branches.get(i);
            TObjectMapEntry<Transaction> entry = TObjectMapEntry.getEntry(_transactions, branch);
            Debug.assertion(entry == null || entry.getValue() == null);
        }
    }
}
