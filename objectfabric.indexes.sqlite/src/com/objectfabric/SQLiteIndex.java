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

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.TransparentExecutor;

/**
 * Full text search for stores. The idea is to separate persistence and indexing. Objects
 * are stored using ObjectFabric highly efficient binary format, but can still be queried
 * using a separate index. Index does not need to be durable or even on disk, it can be
 * rebuilt from the store. Early implementation for a client, work in progress.
 */
public class SQLiteIndex extends Index {

    public static final class Context {

        public final SQLiteConnection Db;

        public final HashSet<String> Prefixes = new HashSet<String>();

        public final StringBuilder Sb = new StringBuilder();

        public Context(SQLiteConnection db) {
            Db = db;
        }
    }

    private final String _file;

    private final boolean _batch;

    private final ThreadLocal<Context> _context = new ThreadLocal<Context>();

    private final ConcurrentLinkedQueue<Context> _contexts = new ConcurrentLinkedQueue<Context>();

    private final AtomicInteger _pending = new AtomicInteger();

    public SQLiteIndex(String file, boolean batch) throws SQLiteException {
        this(Transaction.getDefaultTrunk(), file, batch);
    }

    /**
     * Setting 'batch' at true executes SQLite command "PRAGMA synchronous = OFF" and
     * groups inserts in transactions. It should be used only for off-line batch
     * processing, not in production.
     */
    public SQLiteIndex(Transaction trunk, String file, boolean batch) throws SQLiteException {
        super(trunk);

        _file = file;
        _batch = batch;

        Context context = getContext();

        if (_batch)
            context.Db.exec("PRAGMA synchronous = OFF");

        boolean exists;
        SQLiteStatement st = context.Db.prepare("SELECT DISTINCT tbl_name from sqlite_master WHERE tbl_name = 'main'");

        try {
            exists = st.step();
        } finally {
            st.dispose();
        }

        if (!exists)
            context.Db.exec("CREATE VIRTUAL TABLE main USING fts4(object, prefixes)");
    }

    public void clear() throws SQLiteException {
        getContext().Db.exec("DELETE FROM main");
    }

    public void close() {
        for (Context context : _contexts)
            context.Db.dispose();
    }

    private final Context getContext() throws SQLiteException {
        Context context = _context.get();

        if (context == null) {
            SQLiteConnection db = new SQLiteConnection(new File(_file));
            db.open();

            if (_batch)
                db.exec("PRAGMA synchronous = OFF");

            _context.set(context = new Context(db));
            _contexts.add(context);
        }

        return context;
    }

    public Future<Void> insertAsync(TObject object, AsyncCallback<Void> callback, AsyncOptions asyncOptions, String... keywords) {
        // Do as much computation as possible on this thread

        final String prefixes = getPrefixes(keywords);
        final FutureWithCallback<Void> future = new FutureWithCallback<Void>(callback, asyncOptions);

        // Then store object, and update index if success

        Store store = object.getTrunk().getStore();

        while (_pending.get() > 1000)
            PlatformThread.sleep(1);

        _pending.incrementAndGet();

        store.insert(new Insert(object) {

            @Override
            public void begin() {
                if (_batch) {
                    try {
                        getContext().Db.exec("BEGIN");
                    } catch (SQLiteException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

            @Override
            public void commit() {
                if (_batch) {
                    try {
                        getContext().Db.exec("COMMIT");
                    } catch (SQLiteException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

            @Override
            public void onSuccess() {
                SQLiteStatement st = null;

                try {
                    st = getContext().Db.prepare("INSERT INTO main VALUES(?, ?)");
                    st.bind(1, getData());
                    st.bind(2, prefixes);
                    st.stepThrough();
                    future.set(null);
                } catch (SQLiteException ex) {
                    future.setException(ex);
                } finally {
                    if (st != null)
                        st.dispose();
                }

                _pending.decrementAndGet();
            }

            @Override
            public void onFailure(Throwable throwable) {
                future.setException(throwable);
                _pending.decrementAndGet();
            }
        });

        return future;
    }

    public TObjectStatement get(String letters) throws SQLiteException {
        SQLiteStatement st = getContext().Db.prepare("SELECT object FROM main WHERE prefixes MATCH ?");
        st.bind(1, letters.toLowerCase().replace("(", "").replace(")", ""));
        return new TObjectStatement(st);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void fetchImplementationAsync(byte[] ref, MethodCall call) {
        Store store = getTrunk().getStore();
        store.getAsync(ref, call);
    }

    @Override
    protected Executor getDefaultMethodExecutor_objectfabric() {
        Executor executor = super.getDefaultMethodExecutor_objectfabric();

        if (executor == TransparentExecutor.getInstance()) {
            Store store = getTrunk().getStore();
            executor = store.getRun();
        }

        return executor;
    }

    public final class TObjectStatement {

        private final SQLiteStatement _st;

        private final List<Future<TObject>> _futures = new List<Future<TObject>>();

        public TObjectStatement(SQLiteStatement st) {
            _st = st;
        }

        public void dispose() {
            _st.dispose();
        }

        public void startRead(int count, int offset) throws SQLiteException {
            for (int i = 0; _st.step() && i < offset + count; i++) {
                if (i >= offset) {
                    byte[] ref = _st.columnBlob(0);
                    _futures.add(fetchAsync(ref, null));
                }
            }
        }

        public TObject[] get() throws java.lang.InterruptedException, ExecutionException {
            TObject[] result = new TObject[_futures.size()];

            for (int i = 0; i < _futures.size(); i++)
                result[i] = _futures.get(i).get();

            _futures.clear();
            return result;
        }
    }

    private static final String getPrefixes(String[] keywords) {
        HashSet<String> prefixes = new HashSet<String>();

        for (String name : keywords)
            if (name != null)
                for (String word : name.toLowerCase().split(" "))
                    for (int i = 0; i < word.length() - 1; i++)
                        prefixes.add(word.substring(i));

        StringBuilder sb = new StringBuilder();

        for (String suffix : prefixes) {
            sb.append(' ');
            sb.append(suffix);
        }

        return sb.toString();
    }
}
