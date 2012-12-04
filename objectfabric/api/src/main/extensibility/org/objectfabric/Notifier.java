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

import java.util.concurrent.Executor;

import org.objectfabric.Actor.Message;
import org.objectfabric.TObject.Transaction;
import org.objectfabric.ThreadAssert.AllowSharedRead;
import org.objectfabric.ThreadAssert.SingleThreaded;
import org.objectfabric.Workspace.Granularity;

/**
 * Invokes listeners which are registered on transactional objects, and raise callbacks
 * for asynchronous operations. The notifier maintains ordering, and executes all
 * callbacks on the notification executor. If the executor maintains order too, listeners
 * are invoked in the same order as transactions that made those changes were committed.
 */
@SuppressWarnings({ "serial", "rawtypes" })
@SingleThreaded
class Notifier extends Dispatcher {

    @AllowSharedRead
    private final Run _run;

    private Transaction _committed, _previous;

    Notifier(Workspace workspace) {
        super(workspace, false);

        _run = new Run(workspace.callbackExecutor());
    }

    final void start() {
        workspace().register(this, _run);
        init(true, false);

        if (Debug.THREADS)
            ThreadAssert.exchangeGive(_run, this);

        _run.onStarted();
    }

    final Run run() {
        return _run;
    }

    final class Run extends Actor implements Runnable {

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

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(this);

            onRunStarting();
            runMessages(false);
            walk();

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(false);
        }
    }

    //

    static final class CustomExecutorListener {

        final Object Listener;

        final Executor Executor;

        CustomExecutorListener(Object listener, Executor executor) {
            Listener = listener;
            Executor = executor;
        }

        @Override
        public int hashCode() {
            return Listener.hashCode() ^ Executor.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;

            CustomExecutorListener other = (CustomExecutorListener) obj;
            return Listener.equals(other.Listener) && Executor.equals(other.Executor);
        }

    }

    final void addListener(TObject object, Object listener) {
        _run.addAndRun(new AddListener(object, listener));
    }

    final void removeListener(TObject object, Object listener) {
        _run.addAndRun(new RemoveListener(object, listener));
    }

    final void raiseFieldListener(TObject object, int fieldIndex) {
        _run.addAndRun(new RaiseIndexListener(object, fieldIndex));
    }

    final void raisePropertyListener(TObject object, String propertyName) {
        _run.addAndRun(new RaisePropertyListener(object, propertyName));
    }

    //

    @Override
    Action onVisitingMap(int mapIndex) {
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
    final void releaseSnapshot(int start, int end) {
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

    @Override
    final Action onVisitingTObject(TObject object) {
        Action action = super.onVisitingTObject(object);

        if (action == Action.VISIT)
            if (object.listeners() == null)
                return Action.SKIP;

        return action;
    }

    //

    private static abstract class Invocation {

        abstract void run(Object listener);
    }

    private static void invoke(TObject object, final Invocation invocation) {
        for (Object listener : object.listeners()) {
            if (listener instanceof CustomExecutorListener) {
                final CustomExecutorListener cel = (CustomExecutorListener) listener;

                cel.Executor.execute(new Runnable() {

                    @Override
                    public void run() {
                        invocation.run(cel.Listener);
                    }
                });
            } else {
                try {
                    invocation.run(listener);
                } catch (Exception e) {
                    Log.userCodeException(e);
                }
            }
        }
    }

    /*
     * Indexed.
     */

    @Override
    protected final void onIndexedRead(TObject object, int index) {
    }

    @Override
    protected final void onIndexedWrite(TObject object, final int index) {
        if (object.listeners() != null) {
            invoke(object, new Invocation() {

                @Override
                void run(Object listener) {
                    ((IndexListener) listener).onSet(index);
                }
            });
        }
    }

    /*
     * TKeyed.
     */

    @Override
    protected final void onKeyedRead(TObject object, Object key) {
        // TODO
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final void onKeyedPut(TObject object, final Object key, Object value) {
        if (object.listeners() != null) {
            invoke(object, new Invocation() {

                @Override
                void run(Object listener) {
                    ((KeyListener) listener).onPut(key);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final void onKeyedRemoval(TObject object, final Object key) {
        if (object.listeners() != null) {
            invoke(object, new Invocation() {

                @Override
                void run(Object listener) {
                    ((KeyListener) listener).onRemove(key);
                }
            });
        }
    }

    @Override
    protected final void onKeyedClear(TObject object) {
        if (object.listeners() != null) {
            invoke(object, new Invocation() {

                @Override
                void run(Object listener) {
                    ((KeyListener) listener).onClear();
                }
            });
        }
    }

    /*
     * Resources.
     */

    @Override
    protected final void onResourcePut(final TObject object) {
        if (object.listeners() != null) {
            invoke(object, new Invocation() {

                @Override
                void run(Object listener) {
                    ((ResourceListener) listener).onSet();
                }
            });
        }
    }

    @Override
    protected final void onResourceDelete(final TObject object) {
        if (object.listeners() != null) {
            invoke(object, new Invocation() {

                @Override
                void run(Object listener) {
                    ((ResourceListener) listener).onDelete();
                }
            });
        }
    }

    //

    private final class AddListener extends Message {

        TObject _object;

        Object _listener;

        AddListener(TObject object, Object listener) {
            _object = object;
            _listener = listener;
        }

        @Override
        void run() {
            if (_object.listeners() == null)
                _object.listeners(new PlatformSet<Object>());

            _object.listeners().add(_listener);
        }
    }

    private final class RemoveListener extends Message {

        TObject _object;

        Object _listener;

        RemoveListener(TObject object, Object listener) {
            _object = object;
            _listener = listener;
        }

        @Override
        void run() {
            if (_object.listeners() != null) {
                _object.listeners().remove(_listener);

                if (_object.listeners().isEmpty())
                    _object.listeners(null);
            }
        }
    }

    private final class RaiseIndexListener extends Message {

        TObject _object;

        int _index;

        RaiseIndexListener(TObject object, int index) {
            _object = object;
            _index = index;
        }

        @Override
        void run() {
            if (_object instanceof TIndexed) {
                if (_object.listeners() != null) {
                    invoke(_object, new Invocation() {

                        @Override
                        void run(Object listener) {
                            if (listener instanceof IndexListener)
                                ((IndexListener) listener).onSet(_index);
                        }
                    });
                }
            }
        }
    }

    private final class RaisePropertyListener extends Message {

        TObject _object;

        String _propertyName;

        RaisePropertyListener(TObject object, String propertyName) {
            _object = object;
            _propertyName = propertyName;
        }

        @Override
        void run() {
            if (_object instanceof TIndexed) {
                if (_object.listeners() != null) {
                    invoke(_object, new Invocation() {

                        @Override
                        void run(Object listener) {
                            if (listener instanceof PropertyListener)
                                ((PropertyListener) listener).onPropertyChanged(_propertyName);
                        }
                    });
                }
            }
        }
    }
}
