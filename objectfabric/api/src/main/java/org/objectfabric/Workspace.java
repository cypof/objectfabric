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

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.objectfabric.Actor.Flush;
import org.objectfabric.CloseCounter.Callback;
import org.objectfabric.Notifier.CustomExecutorListener;
import org.objectfabric.Range.Id;
import org.objectfabric.Snapshot.SlowChanging;
import org.objectfabric.TObject.Transaction;

/**
 * Consistent view of a set of resources.<br>
 * <br>
 * <b>Like a Browser</b> <br>
 * <br>
 * A workspace loads resources from URIs. It is similar to a browser in that it manages
 * connections to servers and can maintain caches. A resource loaded in a workspace is
 * like a Google Docs page loaded in a browser, it remains in sync with the server and can
 * be used off-line. Closing a workspace flushes pending updates and allows disconnection
 * from remote servers.<br>
 * <br>
 * <b>Like a Source Control Working Tree</b> <br>
 * <br>
 * This class is also inspired by distributed version control systems. It maintains local
 * representations of resources for better read and write performance, but tracks changes
 * to be committed back. Like most source control systems, workspaces allow multiple
 * changes to be committed as an atomic operation, which simplifies exception handling as
 * compensating code is unnecessary. This aspect is implemented as a Transactional Memory
 * mechanism. A thread running in the context of a transaction sees a stable consistent
 * snapshot of all objects loaded in the workspace. Transaction are automatically retried
 * if a conflict occurred with another thread.<br>
 * <br>
 * <b>Eventual Consistency</b> <br>
 * <br>
 * A workspace is responsible for ordering versions of resources as they are received from
 * caches and servers. Ordering is deterministic so that all sites can eventually reach
 * the same view of each resource. Eventual consistency helps with application
 * scalability, and allows off-line modification of resources.
 */
public abstract class Workspace implements URIHandlersSet, Closeable {

    /**
     * Defines how the workspace keeps track of changes.
     */
    enum Granularity {
        /**
         * If a field changes several times in a row, the workspace is allowed to skip
         * some intermediary values and process directly the last value of the field. If
         * extensions cannot process changes fast enough intermediary values will be
         * missed but the workspace is guaranteed to always provide the most up to date
         * consistent view of memory.
         * <nl>
         * This is the default value.
         */
        COALESCE,

        /**
         * Forces the workspace to process every change. If the workspace is persistent,
         * every change will be recorded, indexed by time stamps.
         * <nl>
         * WARNING: Processing all changes keeps changes in memory until all extensions
         * are done with them. If extensions do not process changes fast enough, the
         * throughput of other threads will be slowed down to prevent memory growth. In
         * this case, the workspace throughput will be lowered to the throughput of the
         * slowest extension.
         * <nl>
         * REMARK: Change notifications are run in the context of the transaction which
         * made them. E.g. when a workspace calls a listener after a field has changed,
         * your listener will execute in the context of the transaction that committed
         * this change. Fields read in the listener will not return the most up to date
         * value but the one they had when the transaction committed. This allows correct
         * logging of each change, but prevents code in callbacks to perform new changes.
         * It is the same as running code in an {@link atomicRead}.
         */
        ALL
    }

    private static volatile Serializer _serializer;

    private final AtomicReference<Snapshot> _snapshot = new AtomicReference<Snapshot>();

    private final PlatformThreadLocal<Transaction> _transaction = new PlatformThreadLocal<Transaction>();

    private final PlatformThreadLocal<List<Transaction>> _threadTransactions = new PlatformThreadLocal<List<Transaction>>();

    private final PlatformConcurrentQueue<List<Transaction>> _sharedTransactions = new PlatformConcurrentQueue<List<Transaction>>();

    /*
     * TODO: remove by making the second thread finish the merge that has been delayed.
     */
    private final PlatformConcurrentQueue<VersionMap> _toMerge = new PlatformConcurrentQueue<VersionMap>();

    private final Granularity _granularity;

    private final Resource _emptyResource;

    private final URIResolver _resolver;

    // TODO use weak references + GCQueue or a CustomConcurrentHashMap
    // TODO partition in security domains
    // TODO per URI?
    private final PlatformConcurrentMap<Id, Range> _ranges = new PlatformConcurrentMap<Id, Range>();

    private final Watcher _watcher;

    private final AtomicBoolean _watching = new AtomicBoolean();

    private final AtomicReference<Notifier> _notifier = new AtomicReference<Notifier>();

    private final Executor _callbackExecutor;

    private boolean _loggedOverload;

    Workspace(Granularity granularity) {
        _granularity = granularity;

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        _watcher = new Watcher(this);

        if (Debug.THREADS)
            ThreadAssert.assertCurrentIsEmpty();

        _snapshot.set(Snapshot.createInitial(this));
        _emptyResource = newResource(null);
        _resolver = new URIResolver();
        _callbackExecutor = createCallbackExecutor();

        if (Debug.ENABLED && Platform.get().value() == Platform.JVM) {
            for (int i = 0; i < BuiltInClass.ALL.length; i++) {
                Debug.assertion(BuiltInClass.ALL[i].id() == i);
                String name = Platform.get().name(Platform.get().defaultObjectModel().getClass(i, null));
                Debug.assertion(name.replace('$', '.').equals(BuiltInClass.ALL[i].name()));
            }
        }
    }

    final Granularity granularity() {
        return _granularity;
    }

    final Watcher watcher() {
        return _watcher;
    }

    protected abstract Executor createCallbackExecutor();

    final Executor callbackExecutor() {
        return _callbackExecutor;
    }

    final URIResolver resolver() {
        return _resolver;
    }

    //

    @Override
    public URIHandler[] uriHandlers() {
        return _resolver.uriHandlers();
    }

    @Override
    public void addURIHandler(URIHandler handler) {
        _resolver.addURIHandler(handler);
    }

    @Override
    public void addURIHandler(int index, URIHandler handler) {
        _resolver.addURIHandler(index, handler);
    }

    @Override
    public Location[] caches() {
        return _resolver.caches();
    }

    @Override
    public void addCache(Location location) {
        _resolver.addCache(location);
    }

    /**
     * Establishes connections to origin and caches, and loads resource in the workspace
     * so that its content can be accessed, modified, or listened to for changes. Caller
     * thread blocks until resource has been loaded. The empty URI ("") can be used for
     * in-memory only objects that do not need to be part of a resource.
     */
    public Resource open(String uri) {
        if (uri.length() == 0)
            return _emptyResource;

        @SuppressWarnings("unchecked")
        Future<Resource> future = openAsync(uri, FutureWithCallback.NOP_CALLBACK);

        try {
            return future.get();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Future<Resource> openAsync(String uri, AsyncCallback<Resource> callback) {
        if (uri.length() == 0) {
            if (callback == null)
                return new CompletedFuture<Resource>(_emptyResource);

            FutureWithCallbacks<Resource> future = new FutureWithCallbacks<Resource>(callback, _callbackExecutor);
            future.set(_emptyResource);
            return future;
        }

        URI resolved = Platform.get().resolve(uri, _resolver);

        if (resolved == null)
            throw new RuntimeException(Strings.URI_UNRESOLVED + uri);

        startWatcher();
        FutureWithCallbacks<Resource> future = resolved.open(this);
        Executor executor = callbackExecutor();
        future.addCallback(callback, executor);
        return future;
    }

    final void startWatcher() {
        if (!_watching.get() && _watching.compareAndSet(false, true))
            _watcher.start();
    }

    Resource newResource(URI uri) {
        return new Resource(this, uri);
    }

    final Resource emptyResource() {
        return _emptyResource;
    }

    //

    void onClosed() {
    }

    /**
     * Prevents access to workspace objects, calls {@link Workspace#flush()}, and
     * unsubscribe from any remote object to allow connections to close. This also helps
     * GC as objects receiving updates have a small probability to survive a collection.
     */
    @Override
    public void close() {
        @SuppressWarnings("unchecked")
        Future<Void> future = closeAsync(FutureWithCallback.NOP_CALLBACK);

        if (Platform.get().value() != Platform.GWT) {
            try {
                future.get();
            } catch (Exception e) {
                Log.write(e);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Future<Void> closeAsync(AsyncCallback<Void> callback) {
        if (Debug.ENABLED)
            Debug.assertion(transaction() == null);

        for (;;) {
            FutureWithCallbacks<Void> future = (FutureWithCallbacks<Void>) _resolver.closing().get();

            if (future != null) {
                if (callback != null)
                    future.addCallback(callback, callbackExecutor());

                return future;
            }

            future = new FutureWithCallbacks(callback, callbackExecutor()) {

                @Override
                public void set(Object value) {
                    if (!_watching.get())
                        set();
                    else {
                        _watcher.actor().requestClose(new Callback() {

                            @Override
                            public void call() {
                                set();
                            }
                        });
                    }
                }

                private void set() {
                    _watcher.cleanThreadContext();
                    onClosed();

                    for (Origin origin : _resolver.origins().values())
                        for (URI uri : origin.uris().values())
                            uri.onClose(Workspace.this);

                    if (Debug.ENABLED)
                        Helper.instance().assertWorkspaceIdle(Workspace.this);

                    super.set(null);
                }
            };

            if (_resolver.closing().compareAndSet(null, future)) {
                casSnapshotClose();
                final Notifier notifier = _notifier.get();

                if (notifier == null)
                    startFlush(future);
                else {
                    final FutureWithCallbacks<Void> future_ = future;

                    Flush flush = new Flush() {

                        @Override
                        void done() {
                            if (Debug.THREADS)
                                ThreadAssert.assertCurrentIsEmpty();

                            if (Debug.ENABLED)
                                ThreadAssert.resume(notifier.run());

                            unregister(notifier, notifier.run(), null);

                            if (Debug.THREADS)
                                ThreadAssert.removePrivate(notifier);

                            startFlush(future_);
                        }
                    };

                    notifier.run().addAndRun(flush);
                }

                return future;
            }
        }
    }

    //

    /**
     * Blocks until workspace's pending writes have been synchronized to all caches, or
     * sent to resource origins if no cache is registered.
     */
    public void flush() {
        @SuppressWarnings("unchecked")
        Future<Void> future = flushAsync(FutureWithCallback.NOP_CALLBACK);

        try {
            future.get();
        } catch (Exception e) {
            Log.write(e);
        }
    }

    public Future<Void> flushAsync(AsyncCallback<Void> callback) {
        FutureWithCallback<Void> future = new FutureWithCallback<Void>(callback, callbackExecutor());
        startFlush(future);
        return future;
    }

    private final void startFlush(FutureWithCallback<Void> future) {
        if (_watching.get())
            _watcher.startFlush(future);
        else
            future.set(null);
    }

    //

    final void addListener(TObject object, Object listener, Executor executor) {
        if (!_resolver.isClosing()) {
            Notifier notifier = _notifier.get();

            if (notifier == null) {
                notifier = new Notifier(this);

                if (_notifier.compareAndSet(null, notifier))
                    notifier.start();
                else
                    notifier = _notifier.get();
            }

            if (callbackExecutor().equals(executor))
                notifier.addListener(object, listener);
            else
                notifier.addListener(object, new CustomExecutorListener(listener, executor));
        }
    }

    final void removeListener(TObject object, Object listener, Executor executor) {
        if (!_resolver.isClosing()) {
            Notifier notifier = _notifier.get();

            if (notifier != null) {
                if (callbackExecutor().equals(executor))
                    notifier.removeListener(object, listener);
                else
                    notifier.removeListener(object, new CustomExecutorListener(listener, executor));
            }
        }
    }

    final void raiseFieldListener(TObject object, int fieldIndex) {
        Notifier notifier = _notifier.get();

        if (notifier != null)
            notifier.raiseFieldListener(object, fieldIndex);
    }

    final void raisePropertyListener(TObject object, String propertyName) {
        Notifier notifier = _notifier.get();

        if (notifier != null)
            notifier.raisePropertyListener(object, propertyName);
    }

    /**
     * Blocks the current thread until notifications have been raised for all changes that
     * occurred until now.
     */
    // TODO public, async?
    void flushNotifications() {
        @SuppressWarnings("unchecked")
        final FutureWithCallback<Void> future = new FutureWithCallback<Void>(FutureWithCallback.NOP_CALLBACK, null);
        Notifier notifier = _notifier.get();

        if (notifier != null) {
            Flush flush = new Flush() {

                @Override
                void done() {
                    future.set(null);
                }
            };

            if (notifier.run().addAndRun(flush)) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /*
     * Transactions.
     */

    /**
     * Executes the runnable in the context of a transaction, which is a stable snapshot
     * of all workspace objects. If a transaction is already running, the new one is
     * nested. If commit fails due to a conflict with another transaction, all changes are
     * discarded and the runnable is executed again. The process is repeated until the
     * transaction commits or an exception occurs. <br>
     * <br>
     * Consistency and conflict detection are currently not configurable, and set to the
     * following. After executing the runnable, conflicts are detected between reads of
     * the current transaction and writes of other workspace transactions that occurred
     * since the runnable started. If no conflicts are found, the read set is discarded
     * and the write set is propagated to other workspaces and locations in an eventually
     * consistent manner. Writes are applied atomically to each remote resource. No
     * guaranty is made for transactions that span multiple resources.
     */
    public void atomic(Runnable runnable) {
        Transaction current = transaction();

        if (current != null) {
            Transaction inner = current.startChild(0);
            boolean result = ExpectedExceptionThrower.executeTransaction(this, inner, runnable);

            if (Debug.ENABLED)
                Debug.assertion(result);

            return;
        }

        int retryCount = 0;

        for (;;) {
            Transaction transaction = startImpl(0);

            if (ExpectedExceptionThrower.executeTransaction(this, transaction, runnable))
                break;

            if (Stats.ENABLED) {
                retryCount++;

                Stats.Instance.TransactionRetries.incrementAndGet();

                for (;;) {
                    long max = Stats.Instance.TransactionRetriesMax.get();

                    if (retryCount <= max)
                        break;

                    if (Stats.Instance.TransactionRetriesMax.compareAndSet(max, retryCount))
                        break;
                }
            }
        }
    }

    /**
     * Same as atomic, but the transaction is only allowed to read objects. An exception
     * is thrown if invoking an object setter or modifier. This allows higher performance
     * for read-only code, and guarantees the transaction will succeed on first run. For
     * this reason it is safe to modify non-transactional objects or perform operations
     * like writing to the console.
     */
    public void atomicRead(Runnable runnable) {
        Transaction current = transaction();
        Transaction transaction;

        if (current != null)
            transaction = current.startChild(TransactionBase.FLAG_NO_WRITES);
        else
            transaction = startImpl(TransactionBase.FLAG_NO_WRITES);

        ExpectedExceptionThrower.executeRead(this, transaction, runnable);
    }

    /**
     * Same as atomic, but the transaction does not keep track of its reads. It will
     * successfully commit even if values that were read are in conflict with another
     * transaction's writes. This allows higher performance for code that does not need to
     * check for conflicts, and guarantees the transaction will succeed on first run. For
     * this reason it is safe to modify non-transactional objects or perform operations
     * like writing to the console.
     */
    public void atomicWrite(Runnable runnable) {
        Transaction current = transaction();
        Transaction transaction;

        if (current != null)
            transaction = current.startChild(TransactionBase.FLAG_IGNORE_READS);
        else
            transaction = startImpl(TransactionBase.FLAG_IGNORE_READS);

        boolean result = ExpectedExceptionThrower.executeTransaction(this, transaction, runnable);

        if (Debug.ENABLED)
            Debug.assertion(result);
    }

    //

    final Snapshot snapshot() {
        return _snapshot.get();
    }

    final Snapshot snapshotWithoutClosing() {
        Snapshot snapshot = snapshot();

        if (snapshot.last() == VersionMap.CLOSING) {
            Snapshot newSnapshot = new Snapshot();
            newSnapshot.setVersionMaps(Helper.removeVersionMap(snapshot.getVersionMaps(), snapshot.lastIndex()));
            newSnapshot.writes(Helper.removeVersions(snapshot.writes(), snapshot.lastIndex()));

            if (snapshot.getReads() != null)
                newSnapshot.setReads(Helper.removeVersions(snapshot.getReads(), snapshot.lastIndex()));

            newSnapshot.slowChanging(snapshot.slowChanging());
            snapshot = newSnapshot;
        }

        return snapshot;
    }

    final boolean casSnapshot(Snapshot expected, Snapshot update) {
        if (Debug.ENABLED)
            update.checkInvariants(this);

        return _snapshot.compareAndSet(expected, update);
    }

    private final void casSnapshotClose() {
        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            Snapshot snapshot = snapshot();
            newSnapshot.setVersionMaps(Helper.addVersionMap(snapshot.getVersionMaps(), VersionMap.CLOSING));
            newSnapshot.writes(Helper.addVersions(snapshot.writes(), null));

            if (snapshot.getReads() != null)
                newSnapshot.setReads(Helper.addVersions(snapshot.getReads(), null));
            else
                newSnapshot.setReads(null);

            newSnapshot.slowChanging(snapshot.slowChanging());

            if (casSnapshot(snapshot, newSnapshot))
                break;
        }
    }

    //

    final PlatformThreadLocal<Transaction> transactionThreadLocal() {
        return _transaction;
    }

    final Transaction transaction() {
        return _transaction.get();
    }

    final void setTransaction(Transaction transaction) {
        if (Debug.ENABLED) {
            if (transaction != null) {
                checkNotCached(transaction);
                transaction.checkInvariants();
            }
        }

        _transaction.set(transaction);
    }

    //

    final Transaction startImpl(int flags) {
        Transaction transaction = getOrCreateTransaction();
        Snapshot snapshot;

        for (;;) {
            /*
             * Volatile read ensures sync with shared view.
             */
            snapshot = _snapshot.get();

            if (snapshot.last() == VersionMap.CLOSING) {
                if (Debug.THREADS) { // To assert empty thread context
                    ThreadAssert.removePrivate(transaction);
                    recycle(transaction);
                }

                ExpectedExceptionThrower.throwClosedObjectException();
            }

            /*
             * Increment watchers count to prevent the map we are using as our snapshot
             * from merging with future commits.
             */
            if (snapshot.last().tryToAddWatchers(1))
                break;
        }

        if (Debug.ENABLED) {
            Helper.instance().addWatcher(snapshot.last(), transaction, snapshot, "View.startImpl (last)");
            checkGoodToStart(transaction);
        }

        startImpl(transaction, flags, snapshot);
        return transaction;
    }

    final void startImpl(Transaction transaction, int flags, Snapshot snapshot) {
        transaction.setSnapshot(snapshot);
        transaction.setPublicSnapshotVersions(snapshot.writes());

        if (Debug.ENABLED)
            transaction.checkInvariants();

        transaction.onStart(flags);

        if (Stats.ENABLED)
            Stats.Instance.Started.incrementAndGet();
    }

    final void releaseSnapshotDebug(Snapshot snapshot, Object watcher, String context) {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        Helper.instance().removeWatcher(snapshot.last(), watcher, snapshot, context);
    }

    final void releaseSnapshot(Snapshot snapshot) {
        snapshot.last().removeWatchers(this, 1, false, snapshot);
    }

    //

    final void keepToMergeLater(VersionMap map) {
        if (Debug.ENABLED)
            Debug.assertion(!_toMerge.contains(map));

        _toMerge.add(map);
    }

    final VersionMap pollToMergeLater() {
        return _toMerge.poll();
    }

    //

    final Transaction getOrCreateTransaction() {
        List<Transaction> thread = _threadTransactions.get();
        List<Transaction> previous = InstanceCache.getOrCreateList(thread, _sharedTransactions);

        if (previous != thread)
            _threadTransactions.set(thread = previous);

        Transaction transaction = null;

        if (thread.size() != 0)
            transaction = thread.removeLast();

        if (transaction == null) {
            if (Stats.ENABLED)
                Stats.Instance.Created.incrementAndGet();

            transaction = new Transaction(Workspace.this, null);
        } else {
            if (Debug.THREADS)
                ThreadAssert.addPrivate(transaction);
        }

        if (Debug.ENABLED)
            Helper.instance().toRecycle(transaction);

        return transaction;
    }

    final void recycle(Transaction transaction) {
        if (Debug.ENABLED) {
            if (Platform.get().value() != Platform.GWT) {
                // To avoid thread check
                Debug.assertion(Platform.get().getPrivateField(transaction, "_workspace", Platform.get().transactionBaseClass()) == this);
                Debug.assertion(Platform.get().getPrivateField(transaction, "_parent", Platform.get().transactionBaseClass()) == null);
            }

            Helper.instance().checkFieldsHaveDefaultValues(transaction);

            if (Debug.THREADS)
                ThreadAssert.assertCleaned(transaction);

            Helper.instance().onRecycled(transaction);
        }

        List<Transaction> thread = _threadTransactions.get();
        List<Transaction> previous = InstanceCache.recycle(thread, _sharedTransactions, transaction);

        if (previous != thread)
            _threadTransactions.set(previous);
    }

    final void checkNotCached(Transaction transaction) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        InstanceCache.checkNotCached(_threadTransactions.get(), _sharedTransactions, transaction);
    }

    /*
     * Extensions.
     */

    final boolean registered(Extension extension) {
        Snapshot snapshot = snapshot();
        Extension[] extensions = snapshot.slowChanging() != null ? snapshot.slowChanging().Extensions : null;
        return Helper.contains(extensions, extension);
    }

    final void register(Extension extension, Actor actor) {
        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            Snapshot snapshot = snapshot();

            if (snapshot.last() == VersionMap.CLOSING)
                throw new ClosedException();

            Actor[] actors = snapshot.slowChanging() != null ? snapshot.slowChanging().Actors : null;

            if (actor != null)
                actors = Helper.add(actors, actor);

            Extension[] extensions = snapshot.slowChanging() != null ? snapshot.slowChanging().Extensions : null;
            extensions = Helper.add(extensions, extension);
            SlowChanging slowChanging = new SlowChanging(actors, extensions);
            snapshot.copyWithNewSlowChanging(newSnapshot, slowChanging);

            if (extension.casSnapshotWithThis(snapshot, newSnapshot))
                break;
        }
    }

    final void unregister(Extension extension, Actor actor, Exception exception) {
        Snapshot newSnapshot = new Snapshot();

        for (;;) {
            Snapshot snapshot = snapshot();
            Actor[] actors = snapshot.slowChanging() != null ? snapshot.slowChanging().Actors : null;

            if (actor != null)
                actors = Helper.remove(actors, actor);

            Extension[] extensions = snapshot.slowChanging().Extensions;
            extensions = Helper.remove(extensions, extension);
            SlowChanging slowChanging = new SlowChanging(actors, extensions);
            snapshot.copyWithNewSlowChanging(newSnapshot, slowChanging);

            if (extension.casSnapshotWithoutThis(snapshot, newSnapshot, exception))
                break;
        }
    }

    //

    /**
     * Called when data is written to the workspace faster than extensions (e.g. a logger
     * or persistence backend) can process it. This should only happen if workspace
     * granularity is {@link Granularity#ALL}.
     * <nl>
     * Default behavior is to block the current thread for a small amount of time. This
     * slows writer threads, and helps resorb the overload.
     */
    protected void onOverloading() {
        if (Platform.get().value() != Platform.GWT)
            Platform.get().sleep(1);

        if (!_loggedOverload) {
            _loggedOverload = true;
            Log.write("Warning: " + this + " is overloading.");
        }
    }

    /**
     * Called when the workspace is overloaded to the maximum allowed. This method is
     * called from the thread that is trying to perform a new change.
     */
    protected void onOverloaded() {
        throw new RuntimeException(this + " is overloaded.");
    }

    //

    final Range getOrCreateRange(Id id) {
        Range range = _ranges.get(id);

        if (range == null) {
            range = new Range(this, id);
            Range previous = _ranges.putIfAbsent(id, range);

            if (previous != null)
                range = previous;
        }

        return range;
    }

    // TODO Fallback to save ids in cookie or something if nothing else possible
    Clock newDefaultClock() {
        Clock clock = new Clock(_watcher);
        Peer peer = Peer.get(new UID(Platform.get().newUID()));
        long time = Clock.time(0, false);
        clock.init(peer, time, 0);
        return clock;
    }

    /*
     * Custom serialization.
     */

    public static Serializer getSerializer() {
        return _serializer;
    }

    public static void setSerializer(Serializer value) {
        _serializer = value;
    }

    @Override
    public final int hashCode() {
        // Final as used as key in several locations.
        return super.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    // Debug

    void forceChangeNotifier(Notifier notifier) {
        _notifier.set(notifier);
        notifier.start();
    }

    final void assertIdle() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(_toMerge.isEmpty());

        if (_watcher != null)
            _watcher.actor().assertNoMessages();
    }

    private final void checkGoodToStart(Transaction transaction) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        Debug.assertion(transaction.workspace() == this);
        Debug.assertion(transaction.parent() == null);
        checkNotCached(transaction);
        Helper.instance().checkFieldsHaveDefaultValues(transaction);

        if (!Helper.instance().LastResetFailed)
            Debug.assertion(transaction.getPrivateSnapshotVersions() == null);
    }
}
