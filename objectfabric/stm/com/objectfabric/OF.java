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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformConcurrentMap;

/**
 * Global ObjectFabric configuration and methods.
 */
public class OF {

    /**
     * ObjectFabric objects must be accessed in the context of a transaction. For
     * convenience and cases where transactions cannot be started (e.g. UI data binding),
     * this policy defines how transactions should be started automatically at the
     * beginning of an operation. By default, transactions are started and committed in a
     * blocking way at the end of each operation. Other settings can be preferable, e.g.
     * asynchronously for performance, or using long running transactions, for iterators
     * on collections, which need a stable transaction.
     * <nl>
     * This method is called once per thread, each thread can have a different policy.
     */
    public enum AutoCommitPolicy {
        /**
         * The user has to start transactions manually before any operation.
         */
        NONE,

        /**
         * A transaction will be started and committed immediately after the operation is
         * done. This mode can be very slow as each write might have to block until
         * acknowledged, e.g. by a store or remote server.
         */
        IMMEDIATE,

        /**
         * A transaction will be started and committed asynchronously immediately after
         * the operation is done. Writes will not block, but the user does not get
         * confirmation that the operation succeeded.
         */
        IMMEDIATE_ASYNC,

        /**
         * A transaction will be started for each branch that is accessed, and kept in the
         * current thread. The user needs to call <code>ObjectModel.update()</code> later
         * to flush writes and discard read snapshots.
         */
        DELAYED_MANUAL,

        /**
         * A transaction will be started for each branch that is accessed, and a
         * <code>ObjectModel.update</code> operation will be invoked on the executor
         * returned by getAutoCommitter(). It is still possible for the user to call
         * <code>ObjectModel.update</code> manually any time.
         */
        DELAYED
    }

    public static final AutoCommitPolicy DEFAULT_AUTO_COMMIT_POLICY = AutoCommitPolicy.IMMEDIATE;

    public static class Config {

        public AutoCommitPolicy getAutoCommitPolicy() {
            return DEFAULT_AUTO_COMMIT_POLICY;
        }

        protected AsyncOptions createAsyncOptions() {
            return new AsyncOptions();
        }

        /**
         * Wait for a future to get done.
         */
        protected <V> void wait(Future<V> future) {
            try {
                future.get();
            } catch (java.lang.InterruptedException e) {
            } catch (ExecutionException ex) {
                throw new RuntimeException(ex.getCause());
            }
        }

        /**
         * This is only called when the policy is DELAYED. Warning: Commits must be
         * executed on the same thread transactions were created on.
         */
        protected void delayCommit(Runnable runnable) {
            throw new UnsupportedOperationException();
        }

        /**
         * OF catches all exceptions of type Exception. This method is only called if an
         * exception does not derive from Exception, so it is probably an Error like
         * OutOfMemoryError. The default reaction is to kill the process to prevent data
         * corruption.
         */
        protected void onThrowable(Throwable t) {
            Log.write(t);
            Log.write(Strings.FATAL_ERROR);
            PlatformAdapter.exit(1);
        }

        /**
         * Internal.
         */
        void onCommit() {
        }
    }

    private static Config _config;

    private static AsyncOptions _defaultAsyncOptions;

    private static final PlatformConcurrentMap<AsyncOptions, Notifier> _map = new PlatformConcurrentMap<AsyncOptions, Notifier>();

    private static Serializer _customSerializer;

    static {
        PlatformAdapter.init();
    }

    public static Config getConfig() {
        return _config;
    }

    public static void setConfig(Config value) {
        _config = value;
        _defaultAsyncOptions = value.createAsyncOptions();
    }

    static final AsyncOptions getDefaultAsyncOptions() {
        return _defaultAsyncOptions;
    }

    /**
     * Commits changes done by the current thread since the last call to update() or
     * transaction. Internally, this method commits transactions that are automatically
     * started when reading or writing fields without a current transaction. This also
     * release memory snapshots associated to those transactions, so reads will reflect
     * latest values.
     */
    public static final void update() {
        /*
         * TODO: what about speculative transactions, and the one committed directly by
         * the user?
         */
        ThreadContext context = ThreadContext.getCurrent();
        Future<CommitStatus> result = context.updateAsync(null, null);

        try {
            if (CompileTimeSettings.DISALLOW_THREAD_BLOCKING)
                throw new RuntimeException(Strings.THREAD_BLOCKING_DISALLOWED);

            result.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Asynchronous version of update().
     */
    public static final void updateAsync() {
        ThreadContext context = ThreadContext.getCurrent();
        context.updateAsync(null);
    }

    static final void updateAsyncIfDelayed() {
        ThreadContext context = ThreadContext.getCurrent();

        if (context.getPolicy() == AutoCommitPolicy.DELAYED || context.getPolicy() == AutoCommitPolicy.DELAYED_MANUAL)
            context.updateAsync(null);
    }

    /**
     * Asynchronous version of update().
     */
    public static final Future<CommitStatus> updateAsync(AsyncCallback<CommitStatus> callback, AsyncOptions asyncOptions) {
        ThreadContext context = ThreadContext.getCurrent();
        return context.updateAsync(callback, asyncOptions);
    }

    /**
     * Blocks the current thread until notifications have been raised for all changes that
     * occurred until now. Calling this method ensures that the notifiers are up to date
     * with at least the current thread.
     */
    public static void flushNotifications() {
        for (Notifier notifier : _map.values())
            notifier.flush();
    }

    static void addListener(TObject object, TObjectListener listener, AsyncOptions options) {
        Notifier notifier = _map.get(options);

        if (notifier == null) {
            notifier = new Notifier(options);
            Notifier previous = _map.putIfAbsent(options, notifier);

            if (previous != null)
                notifier = previous;
        }

        notifier.addListener(object, listener);
    }

    static void removeListener(TObject object, TObjectListener listener, AsyncOptions options) {
        Notifier notifier = _map.get(options);
        notifier.removeListener(object, listener);
    }

    static void raiseFieldListener(TObject object, int fieldIndex, AsyncOptions options) {
        Notifier notifier = _map.get(options);
        notifier.raiseFieldListener(object, fieldIndex);
    }

    static void raisePropertyListener(TObject object, String propertyName, AsyncOptions options) {
        Notifier notifier = _map.get(options);
        notifier.raisePropertyListener(object, propertyName);
    }

    //

    public static interface Serializer {

        boolean canSerialize(Object object);

        byte[] serialize(Object object);

        Object deserialize(byte[] bytes);
    }

    /**
     * This serializer will be called if an object to replicate or persist is not an
     * immutable class, nor a TObject. It allows an application to use e.g. Java or JSON
     * serialization instead of OF format.
     */
    public static Serializer getCustomSerializer() {
        return _customSerializer;
    }

    public static void setCustomSerializer(Serializer value) {
        _customSerializer = value;
    }

    // Debug

    public static final void reset() {
        if (!Debug.TESTING)
            throw new RuntimeException();

        updateAsync();

        for (Notifier notifier : _map.values())
            notifier.stop();

        if (Debug.ENABLED)
            for (Notifier notifier : _map.values())
                notifier.assertIdle();

        _map.clear();

        setConfig(new Config());
    }

    static final void forceChangeNotifier(Notifier notifier) {
        if (!Debug.TESTING)
            throw new RuntimeException();

        _map.put(getDefaultAsyncOptions(), notifier);
    }
}
