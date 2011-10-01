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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.CompletedFuture;
import com.objectfabric.misc.TransparentExecutor;

/**
 * By default an object sent to another process or persisted to a store is serialized with
 * all its references. Content of lazy objects is instead retrieved on demand by the
 * remote process. Clients can fetch entries from a lazy map one key at a time.
 * <nl>
 * LazyMap holds keys and values through SoftReferences. The JVM behavior for
 * SoftReferences can be modified using the flag -XX:SoftRefLRUPolicyMSPerMB. You might
 * also want to tune the memory limit using -Xmx. More information at
 * <link>http://www.oracle.com/technetwork/java/hotspotfaq-138619.html#gc_softrefs</link>.
 * <nl>
 * TODO: Caching only works for map loaded from a store. It is currently disabled for maps
 * replicated from a remote site. In this case, entries will be fetched every time.
 */
@SuppressWarnings("unchecked")
public class LazyMap<K, V> extends LazyMapBase {

    public LazyMap() {
        this(Transaction.getDefaultTrunk());
    }

    /**
     * This constructor is only useful if the object might get replicated to a .NET
     * process, to specify which type would be instantiated by the remote runtime.
     */
    public LazyMap(TType genericParamKey, TType genericParamValue) {
        this(Transaction.getDefaultTrunk(), genericParamKey, genericParamValue);
    }

    public LazyMap(Transaction trunk, TType genericParamKey, TType genericParamValue) {
        this(trunk);

        TType[] genericParams = new TType[] { genericParamKey, genericParamValue };
        ((TKeyedSharedVersion) getSharedVersion_objectfabric()).setGenericParameters(genericParams);
    }

    public LazyMap(Transaction trunk) {
        /*
         * Without a store, keep in memory.
         */
        this(new LazyMapSharedVersion(trunk.getStore() == null), trunk);
    }

    protected LazyMap(TObject.Version shared, Transaction trunk) {
        super(shared, trunk);
    }

    /**
     * Looks for the entry in the current process memory. It not found, fetches it from
     * the remote site or store this map has been loaded from.
     */
    public final V get(K key) {
        TKeyedEntry entry = getEntry(key);

        if (entry != null) {
            if (entry.isRemoval())
                return null;

            V value = (V) entry.getValue();

            if (value != null) // Might have been GCed
                return value;
        }

        Future<V> future = (Future) fetchAsync(key, FutureWithCallback.NOP_CALLBACK);

        try {
            if (CompileTimeSettings.DISALLOW_THREAD_BLOCKING)
                throw new RuntimeException(Strings.THREAD_BLOCKING_DISALLOWED);

            return future.get();
        } catch (java.lang.InterruptedException ex) {
            ExpectedExceptionThrower.throwRuntimeException(ex);
        } catch (ExecutionException ex) {
            ExpectedExceptionThrower.throwRuntimeException(ex);
        }

        throw new IllegalStateException();
    }

    public final Future<V> getAsync(K key, AsyncCallback<V> callback) {
        return getAsync(key, callback, getDefaultAsyncOptions_objectfabric());
    }

    public final Future<V> getAsync(K key, AsyncCallback<V> callback, AsyncOptions asyncOptions) {
        TKeyedEntry entry = getEntry(key);

        if (entry != null) {
            if (entry.isRemoval())
                return setResult(null, callback, asyncOptions);

            V value = (V) entry.getValue();

            if (value != null) // Might have been GCed
                return setResult(value, callback, asyncOptions);
        }

        return (Future) fetchAsync(key, (AsyncCallback) callback, asyncOptions);
    }

    /**
     * Looks for the entry in the current process memory. If not found, returns null.
     */
    public final V getIfInMemory(K key) {
        TKeyedEntry entry = getEntry(key);

        if (entry != null) {
            if (entry.isRemoval())
                return null;

            V value = (V) entry.getValue();

            if (value != null) // Might have been GCed
                return value;
        }

        return null;
    }

    private final Future<V> setResult(V result, AsyncCallback<V> callback, AsyncOptions asyncOptions) {
        if (asyncOptions.getExecutor() == TransparentExecutor.getInstance()) {
            callback.onSuccess(result);
            return new CompletedFuture<V>(result);
        }

        FutureWithCallback<V> future = new FutureWithCallback<V>(callback, asyncOptions);
        future.set(result);
        return future;
    }

    private final TKeyedEntry getEntry(K key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        int hash = hash(key);
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        TKeyedEntry entry;

        try {
            entry = getEntry(inner, key, hash);
        } finally {
            Transaction.endRead(outer, inner, this);
        }

        return entry;
    }

    public final void put(K key, V value) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(key, hash(key), value, false);
        putEntry(key, entry, false);
    }

    public final void remove(K key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(key, hash(key), TKeyedEntry.REMOVAL, false);
        putEntry(key, entry, false);
    }

    //

    @Override
    protected Object fetchImplementation(Object key) {
        TKeyedEntry entry = getEntry((K) key);

        if (entry != null)
            return !entry.isRemoval() ? entry.getValue() : null;

        // If got there, transparent executor so no store
        return null;
    }

    @Override
    protected void fetchImplementationAsync(Object key, MethodCall call) {
        TKeyedEntry entry = getEntry((K) key);

        if (entry != null)
            call.set(!entry.isRemoval() ? entry.getValue() : null);
        else
            load(key, call);
    }

    protected void load(Object key, MethodCall call) {
        Store store = getTrunk().getStore();

        if (store != null)
            store.getAsync(this, key, call);
        else
            call.set(null);
    }

    @Override
    protected Executor getDefaultMethodExecutor_objectfabric() {
        Executor executor = super.getDefaultMethodExecutor_objectfabric();

        if (executor == TransparentExecutor.getInstance()) {
            Store store = getTrunk().getStore();

            if (store != null)
                executor = store.getRun();
        }

        return executor;
    }
}
