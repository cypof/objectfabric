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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import com.objectfabric.Extension.ExtensionShutdownException;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformAdapter;

/**
 * Full text search over objects. Does everything on a dedicated thread to avoid
 * "Locked DB" exceptions. Performance for write heavy workloads are not impacted as
 * SQLite locks the entire table anyway for each operation. Performance for reads would be
 * improved by allowing concurrent queries.
 * <nl>
 * TODO allow concurrent reads.
 * <nl>
 * Warning: Early implementation for a client, work in progress.
 */
public class SQLiteFullTextIndex extends Index {

    private final String _file;

    private final Store _store;

    private final Executor _executor;

    private final Run _run = new Run();

    /**
     * TODO: Support multiple stores, and find them automatically.
     */
    public SQLiteFullTextIndex(String file, Store store) {
        _file = file;
        _store = store;

        /**
         * SQLite binds thread to connection, so cannot use regular pool.
         */
        _executor = Executors.newFixedThreadPool(1, new ThreadFactory() {

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                String process = Debug.ProcessName.length() > 0 ? Debug.ProcessName + " " : "";
                thread.setName(process + "ObjectFabric SQLiteFullTextIndex " + thread.getName());
                thread.setDaemon(true);
                return thread;
            }
        });

        onStarting();
        onStarted();
    }

    /**
     * Private as does not work with inserts. Data might be pending on the store.
     */
    private final void flush() {
        Future<Void> flush = _run.startFlush();

        if (requestRun())
            OF.getConfig().wait(flush);
    }

    public void clear() {
        _run.add(new Runnable() {

            @Override
            public void run() {
                try {
                    _run.getConnection().exec("DELETE FROM main");
                } catch (SQLiteException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        flush();
    }

    public void close() {
        _run.add(new Runnable() {

            @Override
            public void run() {
                _run.getConnection().dispose();
            }
        });

        flush();
    }

    public Future<Void> insertAsync(TObject object, AsyncCallback<Void> callback, AsyncOptions asyncOptions, String... keywords) {
        // Do as much computation as possible on this thread

        final String prefixes = getPrefixes(keywords);
        final FutureWithCallback<Void> future = new FutureWithCallback<Void>(callback, asyncOptions);

        // Then store object, and update index if success

        Store store = object.getTrunk().getStore();

        store.getRun().execute(new Insert(object) {

            @Override
            public void onSuccess(final byte[] result) {
                _run.add(new Runnable() {

                    @Override
                    public void run() {
                        SQLiteStatement st = null;

                        try {
                            st = _run.getConnection().prepare("INSERT INTO main VALUES(?, ?)");
                            st.bind(1, result);
                            st.bind(2, prefixes);
                            st.stepThrough();
                            future.set(null);
                        } catch (SQLiteException ex) {
                            future.setException(ex);
                        } finally {
                            if (st != null)
                                st.dispose();
                        }
                    }
                });

                requestRun();
            }

            @Override
            public void onFailure(Exception e) {
                future.setException(e);
            }
        });

        return future;
    }

    private abstract class Insert implements AsyncCallback<byte[]>, Runnable {

        private final Object _object;

        public Insert(Object object) {
            _object = object;
        }

        @Override
        public void run() {
            _store.insertAsync(_object, this);
        }
    }

    public Object[] get(String letters, int offset, int count) {
        Future<Object[]> future = getAsync(letters, offset, count, null);

        try {
            return future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException) e.getCause();

            throw new RuntimeException(e.getCause());
        }
    }

    public Future<Object[]> getAsync(final String letters, final int offset, final int count, AsyncCallback<Object[]> callback) {
        return getAsync(letters, offset, count, callback, null);
    }

    public Future<Object[]> getAsync(final String letters, final int offset, final int count, AsyncCallback<Object[]> callback, AsyncOptions asyncOptions) {
        final FutureWithCallback<Object[]> future = new FutureWithCallback<Object[]>(callback, asyncOptions);

        _run.add(new Runnable() {

            @Override
            public void run() {
                SQLiteStatement st = null;

                try {
                    st = _run.getConnection().prepare("SELECT object FROM main WHERE prefixes MATCH ?");
                    st.bind(1, letters.toLowerCase().replace("(", "").replace(")", ""));
                    List<byte[]> refs = new List<byte[]>();

                    for (int i = 0; st.step() && i < offset + count; i++) {
                        if (i >= offset) {
                            byte[] ref = st.columnBlob(0);
                            refs.add(ref);
                        }
                    }

                    _store.getRun().execute(new Fetch(refs) {

                        @Override
                        public void onSuccess(Object[] result) {
                            future.set(result);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            future.setException(e);
                        }
                    });
                } catch (SQLiteException e) {
                    throw new RuntimeException(e);
                } finally {
                    if (st != null)
                        st.dispose();
                }
            }
        });

        requestRun();
        return future;
    }

    private abstract class Fetch implements AsyncCallback<Object[]>, Runnable {

        private final List<byte[]> _refs;

        public Fetch(List<byte[]> refs) {
            _refs = refs;
        }

        @Override
        public void run() {
            _store.fetchAsync(_refs, this);
        }
    }

    @Override
    protected void startRun() {
        _executor.execute(_run);
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

    private final class Run extends Actor {

        private SQLiteConnection _db;

        public SQLiteConnection getConnection() {
            return _db;
        }

        @Override
        public void onException(Exception e) {
            super.onException(e);

            // TODO use OF.Config when Extension merged with Walker
            if (!(e instanceof ExtensionShutdownException))
                PlatformAdapter.onFatalError(Strings.FATAL_ERROR + " (" + SQLiteFullTextIndex.this + ")", e);
        }

        @Override
        protected void checkedRun() {
            if (_db == null) {
                try {
                    _db = new SQLiteConnection(new File(_file));
                    _db.open();

                    // _db.exec("PRAGMA synchronous = OFF");

                    SQLiteStatement st = _db.prepare("SELECT DISTINCT tbl_name from sqlite_master WHERE tbl_name = 'main'");
                    boolean exists;

                    try {
                        exists = st.step();
                    } finally {
                        st.dispose();
                    }

                    if (!exists)
                        _db.exec("CREATE VIRTUAL TABLE main USING fts4(object, prefixes)");
                } catch (SQLiteException e) {
                    onException(e);
                }
            }

            try {
                _db.exec("BEGIN");
            } catch (SQLiteException e) {
                onException(e);
            }

            onRunStarting();
            runTasks();
            setFlushes();
            onRunEnded();

            if (!_db.isDisposed()) {
                try {
                    _db.exec("COMMIT");
                } catch (SQLiteException e) {
                    onException(e);
                }
            }
        }
    }
}
