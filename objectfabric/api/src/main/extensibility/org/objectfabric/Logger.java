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

import org.objectfabric.Actor.Flush;
import org.objectfabric.TObject.Transaction;
import org.objectfabric.ThreadAssert.AllowSharedRead;
import org.objectfabric.ThreadAssert.SingleThreaded;
import org.objectfabric.Workspace.Granularity;

@SingleThreaded
public class Logger extends Dispatcher implements Closeable {

    @AllowSharedRead
    private final Run _run;

    private Transaction _committed, _previous;

    public Logger(Workspace workspace) {
        this(workspace, workspace.callbackExecutor());
    }

    public Logger(Workspace workspace, Executor executor) {
        super(workspace, false);

        _run = new Run(executor);

        init(true, false);
        workspace.register(this, _run);

        if (Debug.THREADS)
            ThreadAssert.exchangeGive(_run, this);

        _run.onStarted();
    }

    /**
     * Block the current thread until all events which occurred up to now have been
     * logged.
     */
    public void flush() {
        flush(false);
    }

    public Future<Void> flushAsync(AsyncCallback<Void> callback) {
        return flushAsync(callback, false);
    }

    @Override
    public void close() {
        flush(true);
    }

    public Future<Void> closeAsync(AsyncCallback<Void> callback) {
        return flushAsync(callback, true);
    }

    private final void flush(boolean stop) {
        @SuppressWarnings("unchecked")
        Future<Void> future = flushAsync(FutureWithCallback.NOP_CALLBACK, stop);

        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final Future<Void> flushAsync(AsyncCallback<Void> callback, final boolean stop) {
        final FutureWithCallback<Void> future = new FutureWithCallback<Void>(callback, workspace().callbackExecutor());

        _run.addAndRun(new Flush() {

            @Override
            void done() {
                if (Debug.ENABLED)
                    ThreadAssert.resume(_run);

                if (stop) {
                    workspace().unregister(Logger.this, _run, null);

                    if (Debug.THREADS)
                        ThreadAssert.removePrivate(Logger.this);
                }

                future.set(null);

                if (Debug.ENABLED)
                    ThreadAssert.suspend(_run);
            }
        });

        return future;
    }

    //

    @Override
    protected Action onVisitingMap(int mapIndex) {
        Action action = super.onVisitingMap(mapIndex);

        if (action == Action.VISIT && workspace().granularity() == Granularity.ALL) {
            VersionMap map = snapshot().getVersionMaps()[mapIndex];

            if (Debug.ENABLED) {
                Debug.assertion(map.getTransaction().getVersionMap() == map);
                Debug.assertion(map.getTransaction().getSnapshot().last() == map);
            }

            _committed = map.getTransaction();
            _previous = workspace().transaction();
            workspace().setTransaction(_committed);
        }

        return action;
    }

    @Override
    protected void releaseSnapshot(int start, int end) {
        if (workspace().granularity() == Granularity.ALL) {
            VersionMap map = snapshot().getVersionMaps()[start];

            if (Debug.ENABLED) {
                Debug.assertion(_committed == map.getTransaction());
                Debug.assertion(workspace().transaction() == _committed);
            }

            workspace().setTransaction(_previous);
            _committed = null;
            _previous = null;
        }

        super.releaseSnapshot(start, end);
    }

    //

    protected void onChange(TObject object, String change) {
        String a = Utils.padRight(workspace().toString() + ", ", 30);

        if (Debug.ENABLED)
            Helper.instance().disableEqualsOrHashCheck();

        String b = Utils.padRight(object.toString() + ", ", 60);

        if (Debug.ENABLED)
            Helper.instance().enableEqualsOrHashCheck();

        Log.write(a + b + change);
    }

    /*
     * Indexed.
     */

    @Override
    protected void onIndexedRead(TObject object, int index) {
    }

    @Override
    protected void onIndexedWrite(TObject object, int index) {
        String message;

        if (object instanceof TGenerated) {
            TGenerated generated = (TGenerated) object;
            message = generated.getFieldName(index);

            if (workspace().granularity() == Granularity.ALL) {
                String before = writeField(generated.getOldField(index, _committed));
                String after = writeField(generated.getField(index));
                message += ": " + before + " -> " + after;
            } else
                message += " = " + writeField(generated.getField(index));
        } else
            message = "Field " + index + " changed";

        onChange(object, message);
    }

    private String writeField(Object value) {
        if (value != null) {
            if (Debug.ENABLED)
                Helper.instance().disableEqualsOrHashCheck();

            String result = value.toString();

            if (Debug.ENABLED)
                Helper.instance().enableEqualsOrHashCheck();

            return result;
        }

        return "null";
    }

    /*
     * TKeyed.
     */

    @Override
    protected void onKeyedRead(TObject object, Object key) {
        // TODO
    }

    @Override
    protected void onKeyedPut(TObject object, Object key, Object value) {
        onChange(object, "Put " + key + ", " + value);
    }

    @Override
    protected void onKeyedRemoval(TObject object, Object key) {
        onChange(object, "Removal " + key);
    }

    @Override
    protected void onKeyedClear(TObject object) {
        onChange(object, "Clear");
    }

    // TODO counter

    @SuppressWarnings("serial")
    private final class Run extends Actor implements Runnable {

        private final Executor _executor;

        Run(Executor executor) {
            _executor = executor;
        }

        @Override
        protected void enqueue() {
            _executor.execute(this);
        }

        @Override
        public void run() {
            if (Debug.ENABLED)
                ThreadAssert.resume(this, false);

            onRunStarting();
            runMessages(false);
            walk();

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(false);
        }
    }
}
