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

import of4gwt.misc.Executor;
import of4gwt.misc.Future;



import of4gwt.TObject.UserTObject.SystemClass;
import com.google.gwt.user.client.rpc.AsyncCallback;
import of4gwt.misc.Debug;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformThreadLocal;
import of4gwt.misc.PlatformThreadPool;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreadedOrShared;
import of4gwt.misc.WritableFuture;

/**
 * Beware that transactions instances can be reused after commit or abort.
 */
@SingleThreadedOrShared
public final class Transaction extends TransactionPublic implements SystemClass {

    /**
     * Result of a transaction commit.
     */
    public enum CommitStatus {

        /**
         * Transaction committed successfully.
         */
        SUCCESS,

        /**
         * Transaction could not commit due to a conflict with another transaction.
         * Operation should succeed if retried.
         */
        CONFLICT,

        /**
         * Transaction was aborted, either by user, or by an extension, possibly due to an
         * error like network or store failure. Operation might not succeed even if
         * retried several times.
         */
        ABORT
    }

    /**
     * When creating a branch, you can specify a how conflicts should be detected. The
     * default is READ_WRITE_CONFLICTS.
     */
    public enum ConflictDetection {
        /**
         * Default policy is to validate reads of committing transactions against writes
         * of committed ones. This offers the easiest model to work with: view-isolation.
         */
        READ_WRITE_CONFLICTS,

        /**
         * TODO: Do not use, not finished! Validates only writes of committing
         * transactions. This model is used by most data replication mechanisms and only
         * ensure writes from a user are not overriden by another. This reduces conflicts
         * and might allow for better throughput, particularly in distributed settings,
         * but can be harder to program with.
         */
        WRITE_WRITE_CONFLICTS,

        /**
         * Does not check for conflicts. Transactions always succeed, which should allow
         * for maximum throughput, but updates can be lost.
         */
        LAST_WRITE_WINS
    }

    public static final ConflictDetection DEFAULT_CONFLICT_DETECTION = ConflictDetection.READ_WRITE_CONFLICTS;

    /**
     * Consistency is only relevant in a distributed setting. Transactions are always
     * fully consistent in a single process. Default is FULL.
     */
    public enum Consistency {
        /**
         * Default consistency ensures reads and writes are fully ordered on a trunk. It
         * requires all writes to be ordered by a master which can become a bottleneck.
         */
        FULL,

        /**
         * TODO: Do not use, not finished! Allows transactions to be acknowledged to the
         * client after only a subset of machines of a cluster have stored it. Conflicts
         * can occur later and must be resolved.
         */
        EVENTUAL
    }

    public static final Consistency DEFAULT_CONSISTENCY = Consistency.FULL;

    /**
     * Defines how extensions keep track of changes occurring on transactional objects for
     * this branch.
     */
    public enum Granularity {
        /**
         * If a field changes several times in a row, the extension is allowed to skip
         * some intermediary values and process directly the last value of the field. If
         * the extension cannot process transactions fast enough it will miss intermediary
         * values but is guaranteed to always see the most up to date consistent view of
         * memory.<br>
         * <br>
         * This is the default value when creating a branch.
         */
        COALESCE,

        /**
         * Forces extensions to process all changes.<br>
         * <br>
         * WARNING: Processing all changes keeps committed transactions in memory until
         * the extension is done with them. If extensions do not process changes fast
         * enough, they might slow down the throughput of other threads on the branch to
         * prevent memory growth. In this case, the branch throughput will be lowered to
         * the throughput of the slowest extension configured with ALL granularity.<br>
         * <br>
         * REMARK: Changes will be processed in the context of the transaction which made
         * them. E.g. when a ChangeNotifier calls a listener after a field has changed,
         * your listener will execute in the context of the transaction that committed
         * this change. Fields read in the listener will not return the most up to date
         * value but the one they had when the transaction committed. You can retrieve the
         * transaction which did the change by calling Transaction.getCurrent() in the
         * listener. This allows you e.g., in a distributed setting to get the Site from
         * which the change has been replicated by calling
         * Transaction.getCurrent().getOrigin(). It is necessary to set the current
         * transaction to the object's trunk to update fields in your listener, as the
         * current transaction is already committed.
         */
        ALL
    }

    public static final Granularity DEFAULT_GRANULARITY = Granularity.COALESCE;

    @SuppressWarnings("hiding")
    public static final TType TYPE = new TType(DefaultObjectModelBase.COM_OBJECTFABRIC_TRANSACTION_CLASS_ID);

    /**
     * If this flag is set, the transaction will not record its reads, allowing higher
     * performance for read only code. Commit is still possible but it will fail if any
     * other transaction has committed since it started, regardless of which objects have
     * been modified.
     */
    public static final int FLAG_NO_READS = 1 << 0;

    /**
     * If this flag is set, a transaction will not record writes. An exception will be
     * raised it code tries to modify an object in its context. This can be useful to
     * enforce a transaction is read only.
     */
    public static final int FLAG_NO_WRITES = 1 << 1;

    /**
     * Some transactions cannot be committed immediately, e.g. because a modified object
     * is shared with a server, or is persistent. In this case, its writes are marked as
     * speculative until the remote server or the store acknowledges it. If another
     * transaction is started on the client before the previous transaction was
     * acknowledged, by default it sees this speculative data, and becomes dependent on
     * the previous transaction. If the server aborts the first transaction, the second
     * one will also be aborted. By setting this flag when starting a transaction, it will
     * ignore speculative data. In our example, the second transaction will see objects as
     * they were before the first transaction committed. It will not abort if the previous
     * one does, but might conflict with it if they both read and write the same objects.
     */
    public static final int FLAG_IGNORE_SPECULATIVE_DATA = 1 << 2;

    static final int FLAG_COMMITTED = 1 << 3;

    static final int FLAG_PUBLIC = 1 << 4;

    static final int FLAG_REMOTE = 1 << 5;

    static final int FLAG_AUTO = 1 << 6;

    static final int FLAG_REMOTE_METHOD_CALL = 1 << 7;

    //

    public static final int DEFAULT_FLAGS = 0;

    /**
     * Flags that can be specified when starting a transaction, other flags are set by
     * ObjectFabric during the lifetime of the transaction.
     */
    public static final int START_FLAGS = FLAG_NO_READS | FLAG_NO_WRITES | FLAG_IGNORE_SPECULATIVE_DATA;

    //

    /*
     * TODO merge with thread context to keep cache of recent objects & remove other
     * PlatformThreadLocal.
     */
    private static final PlatformThreadLocal<Transaction> _current;

    private static final Transaction _localTrunk;

    private static volatile Transaction _defaultTrunk;

    //

    private Transaction _parent;

    // !! Add new fields to reset()

    //

    private static final int STATUS_DEFAULT = 0;

    private static final int STATUS_SUSPENDED = 1;

    private static final int STATUS_PUBLISHED = 2;

    private volatile int _status;

    

    // Trunk specific

    private final Store _store;

    // TODO Thread local sessions?
    // TODO Store between runs to lower ids sparsity
    private volatile Session _currentSession;

    

    // !! Add new fields to reset()

    /**
     * Constructor for generated object model
     * 
     * @param trunk
     * @param parentImpl
     * @param type
     * @param conflictDetection
     * @param consistency
     * @param granularity
     */
    protected Transaction(Transaction trunk, Transaction parentImpl, int type, ConflictDetection conflictDetection, Consistency consistency, Granularity granularity) {
        this(trunk, null);
    }

    protected Transaction(Transaction trunk, Store store) {
        super(new Transaction.Version(null), trunk);

        _store = store;

        if (Stats.ENABLED)
            Stats.getInstance().Created.incrementAndGet();
    }

    static {
        _localTrunk = createTrunk(DEFAULT_CONFLICT_DETECTION, DEFAULT_CONSISTENCY, DEFAULT_GRANULARITY, null);
        _defaultTrunk = _localTrunk;
        _current = new PlatformThreadLocal<Transaction>();
        
        
    }

    final boolean isInitializedAsPublic() {
        if (Debug.ENABLED)
            Debug.assertion(_parent == null);

        return _status == STATUS_PUBLISHED;
    }

    final void initializeAsPublic(boolean trunk) {
        if (trunk)
            setTrunk(this);

        setFlags(FLAG_PUBLIC);
        setSharedSnapshot(Snapshot.createInitial(this));
        int type = trunk ? Transaction.Version.TYPE_TRUNK : Transaction.Version.TYPE_BRANCH;
        ((Transaction.Version) getSharedVersion_objectfabric())._type = type;
        ((Transaction.Version) getSharedVersion_objectfabric()).setBit(TransactionBase.TYPE_INDEX);
        _status = STATUS_PUBLISHED;

        if (_store != null) {
            Interceptor.intercept(this);

            _store.getRun().execute(new Runnable() {

                public void run() {
                    if (!_store.registered(Transaction.this))
                        _store.register(Transaction.this);
                }
            });
        }

        if (Debug.THREADS) {
            ThreadAssert.removePrivate(this);

            if (trunk)
                ThreadAssert.addSharedDefinitively(this);
            else
                ThreadAssert.addShared(this);
        }
    }

    static final Transaction getLocalTrunk() {
        return _localTrunk;
    }

    static final Transaction createTrunk(ConflictDetection conflictDetection, Consistency consistency, Granularity granularity, Store store) {
        Transaction trunk;

        if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_DOT_NET)
            trunk = new Transaction(null, store);
        else
            trunk = PlatformAdapter.createTrunk(store);

        ((Transaction.Version) trunk.getSharedVersion_objectfabric())._conflictDetection = conflictDetection;
        ((Transaction.Version) trunk.getSharedVersion_objectfabric()).setBit(TransactionBase.CONFLICT_DETECTION_INDEX);
        ((Transaction.Version) trunk.getSharedVersion_objectfabric())._consistency = consistency;
        ((Transaction.Version) trunk.getSharedVersion_objectfabric()).setBit(TransactionBase.CONSISTENCY_INDEX);
        ((Transaction.Version) trunk.getSharedVersion_objectfabric())._granularity = granularity;
        ((Transaction.Version) trunk.getSharedVersion_objectfabric()).setBit(TransactionBase.GRANULARITY_INDEX);
        trunk.initializeAsPublic(true);
        return trunk;
    }

    /**
     * Default trunk used to start transactions and create new transactional objects. This
     * method returns local site's trunk by initially.
     */
    public static Transaction getDefaultTrunk() {
        return _defaultTrunk;
    }

    /**
     * Setting a new default trunk saves you from passing as argument to every
     * transactional object you create or transaction you start.
     */
    public static void setDefaultTrunk(Transaction value) {
        if (!value.isPublic())
            throw new IllegalArgumentException(Strings.NOT_A_TRUNK);

        _defaultTrunk = value;
    }

    public final Store getStore() {
        return _store;
    }

    //

    /**
     * Returns the transaction that was active when this one has been started. Will return
     * null for Trunk.
     */
    public final Transaction getParent() {
        return _parent;
    }

    final void setParent(Transaction value) {
        if (Debug.ENABLED)
            Debug.assertion(_parent == null && value != null);

        _parent = value;
    }

    final Transaction getBranch() {
        Transaction transaction = this;

        for (;;) {
            if (transaction.isPublic())
                return transaction;

            transaction = transaction.getParent();
        }
    }

    /**
     * Transaction must be cleaned because it is about to be reused, e.g. when committing
     * after a method call on same machine. later.
     */
    final void reset() {
        if (Debug.ENABLED) {
            Helper.getInstance().disableEqualsOrHashCheck();
            Helper.getInstance().getAllowExistingReadsOrWrites().remove(this);
            Helper.getInstance().enableEqualsOrHashCheck();
        }

        resetSets();

        if (isPublic()) {
            setSharedSnapshot(null);
            _status = STATUS_DEFAULT;
        } else if (Debug.ENABLED)
            Debug.assertion(_status == STATUS_DEFAULT);

        resetPrivate();
        resetPublic();
    }

    final boolean isCommitted() {
        boolean value = (getFlags() & FLAG_COMMITTED) != 0;

        if (Debug.ENABLED) {
            if (value) {
                Debug.assertion(_status == STATUS_DEFAULT);
                Debug.assertion(getWrites() == null);
            }
        }

        return value;
    }

    final boolean isRemote() {
        boolean value = (getFlags() & FLAG_REMOTE) != 0;

        if (Debug.ENABLED)
            Debug.assertion(value == (getOrigin() != Site.getLocal()));

        return value;
    }

    final boolean isAuto() {
        boolean value = (getFlags() & FLAG_AUTO) != 0;

        if (Debug.ENABLED)
            if (value)
                Debug.assertion(!isRemote());

        return value;
    }

    final boolean isRemoteMethodCall() {
        boolean value = (getFlags() & FLAG_REMOTE_METHOD_CALL) != 0;

        if (Debug.ENABLED)
            if (value)
                Debug.assertion(isRemote());

        return value;
    }

    //

    /**
     * Current transaction for this thread.
     */
    public static final Transaction getCurrent() {
        Transaction current = _current.get();

        if (Debug.ENABLED) {
            Debug.assertion(!Helper.getInstance().getNoTransaction());
            Debug.assertion(current == null || !current.isPublic());
        }

        return current;
    }

    static final boolean currentNull() {
        return _current.get() == null;
    }

    static final void assertCurrentNull() {
        Debug.assertion(currentNull());
    }

    /**
     * Switches current thread's transaction.
     */
    public static final void setCurrent(Transaction transaction) {
        setCurrentImpl(transaction);
    }

    static final void setCurrentAssertNotAlready(Transaction transaction) {
        if (Debug.ENABLED)
            Debug.assertion(transaction != _current.get());

        setCurrentImpl(transaction);
    }

    private static final void setCurrentImpl(Transaction transaction) {
        if (transaction != null) {
            if (Debug.ENABLED) {
                Debug.assertion(!transaction.isPublic());
                transaction.getParent().checkNotCached(transaction);
            }

            if (!transaction.isCommitted()) {
                for (;;) {
                    int status = transaction._status;

                    if (status == STATUS_SUSPENDED) {
                        if ((transaction._status == STATUS_SUSPENDED ? ((transaction._status = STATUS_DEFAULT) == STATUS_DEFAULT) : false))
                            break;
                    } else {
                        if (Debug.ENABLED)
                            Debug.assertion(status == STATUS_DEFAULT || status == STATUS_PUBLISHED);

                        throw new IllegalArgumentException(Strings.CANNOT_SWITCH_NOT_SUSPENDED);
                    }
                }
            }

            if (Debug.ENABLED)
                transaction.checkInvariants();
        }

        Transaction current = getCurrent();

        if (current != null) {
            if (Debug.ENABLED) {
                Debug.assertion(current._status == STATUS_DEFAULT);
                current.getParent().checkNotCached(current);
            }

            if (!current.isCommitted())
                current._status = STATUS_SUSPENDED;
        }

        setCurrentUnsafe(transaction);
    }

    /*
     * TODO: use this form for all internal switches?
     */
    static final void setCurrentUnsafe(Transaction transaction) {
        if (Debug.ENABLED) {
            Debug.assertion(transaction == null || !transaction.isPublic());
            Debug.assertion(!Helper.getInstance().getNoTransaction());
        }

        _current.set(transaction);
    }

    //

    /**
     * Create a child transaction from the current one. If current transaction is null,
     * the transaction will be started from the default trunk.
     */
    public static final Transaction start() {
        return start(DEFAULT_FLAGS);
    }

    /**
     * Create a child transaction from given parent.
     */
    public static final Transaction start(Transaction parent) {
        return start(parent, DEFAULT_FLAGS);
    }

    /**
     * Create a child transaction with given flags. C.f. Transaction.FLAG_* constants.
     */
    public static final Transaction start(int flags) {
        OF.updateAsyncIfDelayed();
        Transaction current = getCurrent();

        if (current == null)
            current = getDefaultTrunk();

        return start(current, flags);
    }

    /**
     * Create a child transaction from given parent and flags.
     */
    public static final Transaction start(Transaction parent, int flags) {
        if (parent == null)
            throw new IllegalArgumentException(Strings.ARGUMENT_NULL);

        if ((flags & ~START_FLAGS) != 0)
            throw new IllegalArgumentException(Strings.INVALID_FLAGS);

        if (Debug.ENABLED)
            if (parent.isPublic())
                Debug.assertion(getCurrent() == null);

        return parent.startChild(flags);
    }

    final Transaction startChild(int flags) {
        if (Stats.ENABLED)
            Stats.getInstance().Started.incrementAndGet();

        Transaction transaction;

        if (isPublic()) {
            transaction = getOrCreateChild();
            Snapshot snapshot;

            if ((flags & Transaction.FLAG_IGNORE_SPECULATIVE_DATA) == 0)
                snapshot = takeSnapshot();
            else
                snapshot = takeAcknowledgedSnapshot();

            if (Debug.ENABLED)
                takeSnapshotDebug(snapshot, transaction, "Transaction.startChild");

            startFromPublic(transaction, snapshot);
        } else {
            transaction = startFromPrivate(flags);
            flags |= getFlags() & ~(FLAG_COMMITTED | FLAG_REMOTE | FLAG_REMOTE_METHOD_CALL);
        }

        transaction.setFlags(flags);
        Transaction.setCurrentUnsafe(transaction);

        if (Debug.ENABLED)
            transaction.checkInvariants();

        return transaction;
    }

    /**
     * Commits transaction synchronously. The thread will block until commits succeeds or
     * fails, which can be determined by the returned status.
     */
    public final CommitStatus commit() {
        Transaction current = getCurrent();

        if (this != current)
            throw new RuntimeException(Strings.TRANSACTION_NOT_CURRENT);

        return commitWithoutCurrentCheck();
    }

    /**
     * Asynchronous version of the commit method.
     */
    public final Future<CommitStatus> commitAsync(AsyncCallback<CommitStatus> callback) {
        return commitAsync(callback, OF.getDefaultAsyncOptions());
    }

    public final Future<CommitStatus> commitAsync(AsyncCallback<CommitStatus> callback, AsyncOptions asyncOptions) {
        WritableFuture<CommitStatus> future = null;

        if (callback != null)
            future = new FutureWithCallback<CommitStatus>(callback, asyncOptions);

        Transaction current = getCurrent();

        if (this != current)
            throw new RuntimeException(Strings.TRANSACTION_NOT_CURRENT);

        if (current == getTrunk())
            throw new RuntimeException(Strings.CANNOT_COMMIT_OR_ABORT_A_TRUNK);

        return commitWithoutCurrentCheck(future);
    }

    public final void abort() {
        Transaction current = getCurrent();

        if (this != current)
            throw new RuntimeException(Strings.TRANSACTION_NOT_CURRENT);

        abortWithoutCurrentCheck();
    }

    //

    /**
     * Commits transaction synchronously. The thread will block until commit succeeds or
     * fails.
     */
    private final CommitStatus commitWithoutCurrentCheck() {
        if (this == getTrunk())
            throw new RuntimeException(Strings.CANNOT_COMMIT_OR_ABORT_A_TRUNK);

        Future<CommitStatus> result = commitWithoutCurrentCheck(null);

        try {
            if (CompileTimeSettings.DISALLOW_THREAD_BLOCKING)
                throw new RuntimeException(Strings.THREAD_BLOCKING_DISALLOWED);

            return result.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    final void commitPrivateAsyncWithoutAnyCheck() {
        if (Debug.ENABLED) {
            Debug.assertion(this == getCurrent());
            Debug.assertion(this != getTrunk());
            Debug.assertion(!isPublic());
        }

        commitWithoutCurrentCheck(null);
    }

    final Future<CommitStatus> commitWithoutCurrentCheck(WritableFuture<CommitStatus> async) {
        if (isPublic()) // TODO
            throw new UnsupportedOperationException("Committing a public transaction is currently unsupported.");

        if (isCommitted())
            throw new RuntimeException(Strings.READ_ONLY_OR_COMMITTED);

        if (isRemoteMethodCall())
            throw new RuntimeException(Strings.REMOTE_METHOD_CALL);

        Transaction parent = getParent();
        assertUpdates();
        Future<CommitStatus> result = TransactionManager.commit(this, async);

        if (parent.isPublic())
            Transaction.setCurrentUnsafe(null);
        else
            Transaction.setCurrentUnsafe(parent);

        return result;
    }

    final void abortWithoutCurrentCheck() {
        abortWithoutCurrentCheckNoStats();

        if (Stats.ENABLED)
            Stats.getInstance().UserAborted.incrementAndGet();
    }

    final void abortWithoutCurrentCheckNoStats() {
        if (this == getTrunk())
            throw new RuntimeException(Strings.CANNOT_COMMIT_OR_ABORT_A_TRUNK);

        if (isPublic())
            throw new IllegalArgumentException("Aborting a public transaction is currently unsupported.");

        if (isCommitted())
            throw new RuntimeException(Strings.READ_ONLY_OR_COMMITTED);

        if (isRemoteMethodCall())
            throw new RuntimeException(Strings.REMOTE_METHOD_CALL);

        Transaction parent = getParent();
        TransactionManager.abort(this);

        if (parent.isPublic())
            Transaction.setCurrentUnsafe(null);
        else
            Transaction.setCurrentUnsafe(parent);
    }

    //

    /**
     * Executes given code in the context of a transaction. If commit fails due to a
     * conflict, the transaction is retried and executes the code again. The process will
     * be repeated until transaction commits or aborts (due to user code or an extension
     * calling abort), or an exception occurs. This method is synchronous: if the objects
     * modified by the transaction are shared, it might be necessary to wait for
     * acknowledgment from a remote coordinator and the current thread will be waiting
     * idle.
     */
    public static final CommitStatus run(Runnable runnable) {
        return run(runnable, DEFAULT_FLAGS);
    }

    /**
     * Same as run(Runnable), but specifying a parent transaction instead of using the
     * current one.
     */
    public static final CommitStatus run(Runnable runnable, Transaction parent) {
        return run(runnable, parent, DEFAULT_FLAGS);
    }

    /**
     * Same as run(Runnable), but specifying flags for the transaction.
     */
    public static final CommitStatus run(Runnable runnable, int flags) {
        return run(runnable, Transaction.getCurrent(), flags);
    }

    public static final CommitStatus run(Runnable runnable, Transaction parent, int flags) {
        if ((flags & ~START_FLAGS) != 0)
            throw new IllegalArgumentException(Strings.INVALID_FLAGS);

        if (parent == null)
            parent = getDefaultTrunk();

        for (;;) {
            Transaction transaction = parent.startChild(flags);
            CommitStatus result = ExpectedExceptionThrower.executeTransactionRunnable(runnable, transaction);

            if (result != CommitStatus.CONFLICT)
                return result;
        }
    }

    /**
     * Asynchronous version of the run method. The Runnable will be executed (Once or
     * multiple times in case of conflicts) on ObjectFabric's default thread pool.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, AsyncCallback<CommitStatus> callback) {
        return runAsync(runnable, callback, OF.getDefaultAsyncOptions());
    }

    /**
     * Asynchronous version of the run method. The Runnable will be executed (Once or
     * multiple times in case of conflicts) on ObjectFabric's default thread pool.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, AsyncCallback<CommitStatus> callback, AsyncOptions options) {
        return runAsync(runnable, DEFAULT_FLAGS, callback, options);
    }

    /**
     * Asynchronous version of the run method. The transaction will be started with
     * default flags.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Transaction parent, AsyncCallback<CommitStatus> callback) {
        return runAsync(runnable, parent, callback, OF.getDefaultAsyncOptions());
    }

    /**
     * Asynchronous version of the run method. The transaction will be started with
     * default flags.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Transaction parent, AsyncCallback<CommitStatus> callback, AsyncOptions options) {
        return runAsync(runnable, parent, DEFAULT_FLAGS, callback, options);
    }

    /**
     * Asynchronous version of the run method. The transaction will be started on the
     * default trunk.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, int flags, AsyncCallback<CommitStatus> callback) {
        return runAsync(runnable, flags, callback, OF.getDefaultAsyncOptions());
    }

    /**
     * Asynchronous version of the run method. The transaction will be started on the
     * default trunk.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, int flags, AsyncCallback<CommitStatus> callback, AsyncOptions options) {
        return runAsync(runnable, getDefaultTrunk(), flags, callback, options);
    }

    /**
     * Asynchronous version of the run method. The transaction will be started on the
     * default trunk and flags.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Executor executor, AsyncCallback<CommitStatus> callback) {
        return runAsync(runnable, executor, callback, OF.getDefaultAsyncOptions());
    }

    /**
     * Asynchronous version of the run method. The transaction will be started on the
     * default trunk and flags.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Executor executor, AsyncCallback<CommitStatus> callback, AsyncOptions options) {
        return runAsync(runnable, getDefaultTrunk(), DEFAULT_FLAGS, executor, callback, options);
    }

    /**
     * Asynchronous version of the run method. The Runnable will be executed (Once or
     * multiple times in case of conflicts) on ObjectFabric's default thread pool.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Transaction parent, int flags, AsyncCallback<CommitStatus> callback) {
        return runAsync(runnable, parent, flags, callback, OF.getDefaultAsyncOptions());
    }

    /**
     * Asynchronous version of the run method. The Runnable will be executed (Once or
     * multiple times in case of conflicts) on ObjectFabric's default thread pool.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Transaction parent, int flags, AsyncCallback<CommitStatus> callback, AsyncOptions options) {
        return runAsync(runnable, parent, flags, PlatformThreadPool.getInstance(), callback, options);
    }

    /**
     * Asynchronous version of the run method.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Transaction parent, int flags, Executor executor, AsyncCallback<CommitStatus> callback) {
        return runAsync(runnable, parent, flags, executor, callback, OF.getDefaultAsyncOptions());
    }

    /**
     * Asynchronous version of the run method.
     */
    public static final Future<CommitStatus> runAsync(Runnable runnable, Transaction parent, int flags, Executor executor, AsyncCallback<CommitStatus> callback, AsyncOptions options) {
        OF.updateAsyncIfDelayed();
        Transaction current = Transaction.getCurrent();

        if (current == null)
            current = parent;

        if ((flags & ~START_FLAGS) != 0)
            throw new IllegalArgumentException(Strings.INVALID_FLAGS);

        AsyncAtomic atomic = new AsyncAtomic(current, runnable, callback, options, executor, flags);
        atomic.start();
        return atomic;
    }

    //

    static final Transaction startRead(Transaction outer, UserTObject object) {
        if (outer == null || outer.getTrunk() != object.getTrunk()) {
            ThreadContext context = ThreadContext.getCurrent();
            outer = context.startAccess(outer, object, true);
        }

        return outer;
    }

    static final void endRead(Transaction outer, Transaction inner) {
        if (inner != outer) {
            ThreadContext context = ThreadContext.getCurrent();
            context.endAccess(inner, false);
        }
    }

    static final Transaction startWrite(Transaction outer, UserTObject object) {
        if (outer == null || outer.getTrunk() != object.getTrunk()) {
            ThreadContext context = ThreadContext.getCurrent();
            outer = context.startAccess(outer, object, false);
        }

        return outer;
    }

    static final void endWrite(Transaction outer, Transaction inner) {
        endWrite(outer, inner, true);
    }

    static final void endWrite(Transaction outer, Transaction inner, boolean ok) {
        if (inner != outer) {
            ThreadContext context = ThreadContext.getCurrent();
            context.endAccess(inner, ok);
        }
    }

    static final Transaction startIteration(Transaction outer, UserTObject object) {
        if (outer == null || outer.getTrunk() != object.getTrunk()) {
            ThreadContext context = ThreadContext.getCurrent();
            outer = context.startIteration(outer, object);
        }

        return outer;
    }

    //

    static void abortsAfterException(Transaction transaction) {
        boolean done = true;

        for (;;) {
            Transaction current = Transaction.getCurrent();

            if (current == null)
                break;

            if (current == transaction)
                done = true;

            Log.write(Strings.USER_CODE_CHANGED_CURRENT_TRANSACTION);

            if (!current.isCommitted())
                current.abort();
            else {
                Transaction parent = current.getParent();

                if (parent.isPublic())
                    Transaction.setCurrent(null);
                else
                    Transaction.setCurrent(parent);
            }
        }

        if (transaction != null && !done) {
            Transaction.setCurrent(transaction);

            if (!transaction.isCommitted())
                transaction.abort();
            else
                Transaction.setCurrent(null);
        }
    }

    // Trunk specific

    final Descriptor assignId(TObject.Version shared) {
        if (Debug.ENABLED) {
            Debug.assertion(getTrunk() == this);
            Debug.assertion(shared.getUID() == null);
            UserTObject object = shared.getReference().get();

            if (object != null)
                Debug.assertion(object.getTrunk() == this);
        }

        Descriptor descriptor;

        synchronized (shared) {
            Object reference = shared.getUnion();

            if (reference instanceof Descriptor)
                descriptor = (Descriptor) reference;
            else {
                if (Debug.ENABLED)
                    Debug.assertion(shared.getUnion().getClass() == Reference.class);

                for (;;) {
                    Session session = _currentSession;

                    if (session != null) {
                        descriptor = session.assignId(shared);

                        if (descriptor != null)
                            break;
                    }

                    byte[] uid = PlatformAdapter.createUID();
                    Session created = new Session(this, Site.getLocal(), this, uid, Record.NOT_STORED);
                    putObjectWithUIDIfAbsent(uid, created.getSharedVersion_objectfabric());
                    if(_currentSession == session) _currentSession = created;
                }
            }

            if (Debug.ENABLED) {
                if (descriptor == null)
                    throw new RuntimeException();

                Debug.assertion(descriptor.getSession().getSharedVersion(descriptor.getId()) == shared);
            }
        }

        return descriptor;
    }

    //

    final TObject.Version[] getImports() {
        if (getPrivateSnapshotVersions() != null)
            return getPrivateSnapshotVersions()[TransactionSets.IMPORTS_INDEX];

        return null;
    }

    final void addImports(TObject.Version[] versions) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic());
            Debug.assertion(versions.length > 0);
        }

        TObject.Version[] imports = getImports();

        if (Debug.ENABLED) {
            Transaction current = this;

            for (;;) {
                Debug.assertion(current.getImports() == imports);
                current = current.getParent();

                if (current.isPublic())
                    break;
            }
        }

        TObject.Version[] result;

        if (imports == null)
            result = versions;
        else {
            result = imports;

            for (int i = versions.length - 1; i >= 0; i--) {
                if (versions[i] != null) {
                    if (Debug.ENABLED) {
                        if (TransactionSets.getVersionFromSharedVersion(imports, versions[i]) != null) {
                            Debug.assertion(versions[i].getShared().getObjectModel() == DefaultObjectModel.getInstance());
                            Debug.assertion(versions[i].getShared().getClassId() == DefaultObjectModelBase.COM_OBJECTFABRIC_LAZYMAP_CLASS_ID);
                        }
                    }

                    /*
                     * Merge == true for lazy objects which are loaded in several parts in
                     * same private transaction.
                     */
                    result = TransactionSets.putForShared(result, versions[i], versions[i].getUnion(), true);
                }
            }
        }

        if (getPrivateSnapshotVersions() == null) {
            TObject.Version[][] temp = new TObject.Version[1][];
            temp[TransactionSets.IMPORTS_INDEX] = result;
            Transaction current = this;

            for (;;) {
                current.setPrivateSnapshotVersions(temp);
                current = current.getParent();

                if (current.isPublic())
                    break;
            }
        } else {
            if (result != imports) {
                Transaction current = this;

                for (;;) {
                    current.getPrivateSnapshotVersions()[TransactionSets.IMPORTS_INDEX] = result;
                    current = current.getParent();

                    if (current.isPublic())
                        break;
                }
            }
        }
    }

    //

    protected static class Version extends TransactionBase.Version {

        public static final int TYPE_DEFAULT = 0;

        public static final int TYPE_TRUNK = 1;

        public static final int TYPE_BRANCH = 2;

        public Version(TransactionBase.Version shared) {
            super(shared, FIELD_COUNT);
        }

        @Override
        public TObject.Version createVersion() {
            return new Transaction.Version(this);
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public void readWrite(of4gwt.Reader reader, int index) {
            super.readWrite(reader, index);

            if (!reader.interrupted()) {
                switch (index) {
                    case PARENT_IMPL_INDEX: {
                        Transaction.Version shared = (Transaction.Version) getShared();

                        if (shared._parentImpl != null) {
                            Transaction transaction = (Transaction) shared.getReference().get();

                            if (transaction.getParent() == null)
                                transaction.setParent((Transaction) shared._parentImpl.getUserTObject_objectfabric());
                            else if (Debug.ENABLED)
                                Debug.assertion(transaction.getParent() == shared._parentImpl.getUserTObject_objectfabric());
                        }

                        break;
                    }
                    case TYPE_INDEX: {
                        Transaction.Version shared = (Transaction.Version) getShared();

                        if (shared._type != TYPE_DEFAULT) {
                            Transaction transaction = (Transaction) shared.getReference().get();

                            if (!transaction.isInitializedAsPublic())
                                transaction.initializeAsPublic(shared._type == TYPE_TRUNK);

                            if (Debug.ENABLED)
                                Debug.assertion((shared._type == TYPE_TRUNK) == (transaction == transaction.getTrunk()));
                        }

                        break;
                    }
                }
            }
        }

        @Override
        public String toString() {
            TObject.Version shared = getShared();
            String info = "";

            if (getLocalTrunk() == null || shared == getLocalTrunk().getSharedVersion_objectfabric())
                info = " (Local Trunk)";
            else {
                Transaction transaction = (Transaction) shared.getReference().get();

                if (transaction == null)
                    info = " (GCed)";
                else if (transaction == transaction.getTrunk())
                    info = " (Trunk)";
            }

            return super.toString() + info;
        }
    }

    // Debug

    static final Transaction getLocalTrunkForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        return _localTrunk;
    }

    final void checkInvariants() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (this != getTrunk() && getParent().isPublic())
            getParent().checkNotCached(this);

        checkInvariantsPublic();
        checkInvariantsSets();

        if (Debug.THREADS) {
            if (isPublic())
                ThreadAssert.assertShared(this);
            else {
                /*
                 * If committed, must be shared, otherwise private.
                 */
                Snapshot snapshot = null;

                if (getParent() != null && getParent().isPublic())
                    snapshot = getParent().getSharedSnapshot();

                if (snapshot != null && getVersionMap() != null && Helper.getIndex(snapshot, getVersionMap()) >= 0)
                    ThreadAssert.assertShared(getVersionMap());
                else
                    ThreadAssert.assertPrivate(this);
            }
        }
    }
}
