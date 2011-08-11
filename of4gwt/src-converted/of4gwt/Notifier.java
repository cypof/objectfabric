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

import of4gwt.TObject.UserTObject.Method;
import of4gwt.Transaction.Granularity;
import of4gwt.misc.Debug;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformSet;
import of4gwt.misc.SparseArrayHelper;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.AllowSharedRead;
import of4gwt.misc.ThreadAssert.SingleThreaded;

/**
 * Invokes listeners which are registered on transactional objects, and raise callbacks
 * for asynchronous operations. The notifier maintains ordering, and executes all
 * callbacks on the notification executor. If the executor maintains order too, listeners
 * are invoked in the same order as transactions that made those changes were committed.
 */
@SingleThreaded
class Notifier extends Walker {

    @AllowSharedRead
    private final Executor _executor;

    private final Visitor _visitor;

    @AllowSharedRead
    private final AsyncOptions _asyncOptions;

    private final boolean _notifyLocalEvents;

    @AllowSharedRead
    private final Run _run = new Run();

    @SuppressWarnings("unchecked")
    private TObjectMapEntry<PlatformSet<TObjectListener>>[] _listeners = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

    public Notifier(AsyncOptions options) {
        super(options.getForcedGranularity());

        _asyncOptions = options;
        _executor = options.getExecutor();
        _notifyLocalEvents = options.notifyLocalEvents();

        _visitor = new Visitor();
        Privileged.init(_visitor, this, true);
        _visitor.registerClassVisitor(Visitor.INDEXED_VISITOR_ID, new TIndexedVisitor());
        _visitor.registerClassVisitor(Visitor.KEYED_VISITOR_ID, new TKeyedVisitor());
        _visitor.registerClassVisitor(Visitor.LIST_VISITOR_ID, new TListVisitor());

        if (!end())
            requestRunOnce();

        if (Debug.THREADS) {
            ThreadAssert.exchangeGive(_run, _visitor);
            ThreadAssert.exchangeGive(_run, this);
        }
    }

    final Visitor getVisitor() {
        return _visitor;
    }

    public final void flush() {
        Flush flush = _run.startFlush();

        if (requestRun())
            OF.getConfig().wait(flush);
    }

    public final void stop() {
        flush();

        _run.add(new Runnable() {

            public void run() {
                unregisterFromAllBranches(null);
            }
        });

        flush();

        if (Debug.THREADS) {
            ThreadAssert.suspend(this);
            ThreadAssert.resume(_run);
            ThreadAssert.removePrivate(_visitor);
            ThreadAssert.removePrivate(this);
            ThreadAssert.resume(this);
        }
    }

    //

    @Override
    protected final void requestRunOnce() {
        _executor.execute(_run);
    }

    private final class Run extends DefaultRunnable implements Runnable {

        public Run() {
            super(Notifier.this);
        }

        @SuppressWarnings("fallthrough")
        public void run() {
            if (Debug.ENABLED)
                ThreadAssert.resume(this, false);

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(this);

            for (;;) {
                boolean resumed = false;

                if (getVisitor().interrupted()) {
                    getVisitor().resume();
                    resumed = true;
                }

                if (!resumed)
                    before();

                walk(getVisitor());

                if (getVisitor().interrupted()) {
                    getVisitor().interrupt(null);

                    if (Debug.ENABLED)
                        ThreadAssert.suspend(this);

                    return;
                }

                if (Debug.ENABLED)
                    ThreadAssert.suspend(this);

                if (after())
                    return;

                if (Debug.ENABLED)
                    ThreadAssert.resume(this);
            }
        }
    }

    //

    public final void addListener(TObject object, TObjectListener listener) {
        _run.add(new AddListener(object, listener));

        if (_asyncOptions.waitForListenersRegistration())
            flush();
        else
            requestRun();
    }

    public final void removeListener(TObject object, TObjectListener listener) {
        _run.add(new RemoveListener(object, listener));

        if (_asyncOptions.waitForListenersRegistration())
            flush();
        else
            requestRun();
    }

    public final void raiseFieldListener(TObject object, int fieldIndex) {
        _run.add(new RaiseFieldListener(object, fieldIndex));

        if (_asyncOptions.waitForListenersRegistration())
            flush();
        else
            requestRun();
    }

    public final void raisePropertyListener(TObject object, String propertyName) {
        _run.add(new RaisePropertyListener(object, propertyName));

        if (_asyncOptions.waitForListenersRegistration())
            flush();
        else
            requestRun();
    }

    //

    @Override
    protected Action onVisitingMap(Visitor visitor, int mapIndex) {
        Action action = super.onVisitingMap(visitor, mapIndex);
        VersionMap map = null;

        if (!_notifyLocalEvents) {
            map = visitor.getSnapshot().getVersionMaps()[mapIndex];

            if (map.getSource() == null || map.getSource().Connection == null)
                action = Action.SKIP;
        }

        if (action == Action.VISIT && getGranularity(visitor.getBranch()) == Granularity.ALL) {
            OF.updateAsync();

            if (map == null)
                map = visitor.getSnapshot().getVersionMaps()[mapIndex];

            if (Debug.ENABLED) {
                Debug.assertion(map.getTransaction().getVersionMap() == map);
                Debug.assertion(map.getTransaction().getSnapshot().getLast() == map);
            }

            Transaction.setCurrentUnsafe(map.getTransaction());
        }

        return action;
    }

    @Override
    protected void onVisitedMap(Visitor visitor, int mapIndex) {
        super.onVisitedMap(visitor, mapIndex);

        if (getGranularity(visitor.getBranch()) == Granularity.ALL) {
            VersionMap map = visitor.getSnapshot().getVersionMaps()[mapIndex];

            if (Transaction.getCurrent() == map.getTransaction())
                Transaction.setCurrentUnsafe(null);

            OF.updateAsync();
        }
    }

    @Override
    protected Action onVisitingTObject(Visitor visitor, TObject object) {
        Action action = super.onVisitingTObject(visitor, object);

        if (action == Action.VISIT)
            if (!TObjectMapEntry.contains(_listeners, object))
                return Action.SKIP;

        return action;
    }

    //

    @Override
    protected void onGarbageCollected(TObject shared) {
        super.onGarbageCollected(shared);

        _run.execute(new RemoveAllListeners(shared));
    }

    //

    private final class TIndexedVisitor extends TIndexed.Visitor {

        protected TIndexedVisitor() {
            super(_visitor);
        }

        @Override
        protected void onWrite(TObject object, int index) {
            if (Debug.ENABLED)
                Debug.assertion(!(object instanceof Method));

            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, object);

            if (listeners != null) {
                for (TObjectListener listener : listeners) {
                    if (listener instanceof FieldListener) {
                        try {
                            ((FieldListener) listener).onFieldChanged(index);
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            }
        }
    }

    private final class TKeyedVisitor extends TKeyed.Visitor {

        protected TKeyedVisitor() {
            super(_visitor);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void onPut(TObject object, Object key, Object value) {
            if (Debug.ENABLED) {
                // User should not be able to access methods
                Debug.assertion(!(object instanceof Method));
            }

            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, object);

            if (listeners != null) {
                for (TObjectListener listener : listeners) {
                    if (listener instanceof KeyListener) {
                        try {
                            ((KeyListener) listener).onPut(key);
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void onRemoval(TObject object, Object key) {
            if (Debug.ENABLED) {
                // User should not be able to access methods
                Debug.assertion(!(object instanceof Method));
            }

            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, object);

            if (listeners != null) {
                for (TObjectListener listener : listeners) {
                    if (listener instanceof KeyListener) {
                        try {
                            ((KeyListener) listener).onRemoved(key);
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void onClear(TObject object) {
            if (Debug.ENABLED) {
                // User should not be able to access methods
                Debug.assertion(!(object instanceof Method));
            }

            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, object);

            if (listeners != null) {
                for (TObjectListener listener : listeners) {
                    if (listener instanceof KeyListener) {
                        try {
                            ((KeyListener) listener).onCleared();
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            }
        }
    }

    /**
     * TODO: transform events through the subsequent maps so that they correspond to the
     * current state of the trunk. True also for other objects but leads to
     * inconsistencies with list.
     */
    private final class TListVisitor extends TList.Visitor {

        protected TListVisitor() {
            super(_visitor);
        }

        @Override
        protected void onAdd(TObject object, int index) {
            if (Debug.ENABLED) {
                // User should not be able to access methods
                Debug.assertion(!(object instanceof Method));
            }

            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, object);

            if (listeners != null) {
                for (TObjectListener listener : listeners) {
                    if (listener instanceof ListListener) {
                        try {
                            ((ListListener) listener).onAdded(index);
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            }
        }

        @Override
        protected void onRemoval(TObject object, int index) {
            if (Debug.ENABLED) {
                // User should not be able to access methods
                Debug.assertion(!(object instanceof Method));
            }

            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, object);

            if (listeners != null) {
                for (TObjectListener listener : listeners) {
                    if (listener instanceof ListListener) {
                        try {
                            ((ListListener) listener).onRemoved(index);
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            }
        }

        @Override
        protected void onClear(TObject object) {
            if (Debug.ENABLED) {
                // User should not be able to access methods
                Debug.assertion(!(object instanceof Method));
            }

            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, object);

            if (listeners != null) {
                for (TObjectListener listener : listeners) {
                    if (listener instanceof ListListener) {
                        try {
                            ((ListListener) listener).onCleared();
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            }
        }
    }

    private final class AddListener implements Runnable {

        private TObject _object;

        private TObjectListener _listener;

        public AddListener(TObject object, TObjectListener listener) {
            _object = object;
            _listener = listener;
        }

        public void run() {
            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, _object);

            if (listeners == null) {
                listeners = new PlatformSet<TObjectListener>();
                TObjectMapEntry<PlatformSet<TObjectListener>> entry = new TObjectMapEntry<PlatformSet<TObjectListener>>(_object, listeners);
                _listeners = TObjectMapEntry.put(_listeners, entry);
            }

            listeners.add(_listener);

            if (!registered(_object.getTrunk()))
                register(_object.getTrunk());
        }
    }

    private final class RemoveListener implements Runnable {

        private TObject _object;

        private TObjectListener _listener;

        public RemoveListener(TObject object, TObjectListener listener) {
            _object = object;
            _listener = listener;
        }

        public void run() {
            PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, _object);

            if (listeners != null) {
                listeners.remove(_listener);

                if (listeners.size() == 0)
                    TObjectMapEntry.remove(_listeners, _object);
            }
        }
    }

    private final class RaiseFieldListener implements Runnable {

        private TObject _object;

        private int _fieldIndex;

        public RaiseFieldListener(TObject object, int fieldIndex) {
            _object = object;
            _fieldIndex = fieldIndex;
        }

        public void run() {
            if (_object instanceof TIndexed) {
                PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, _object);

                if (listeners != null) {
                    for (TObjectListener listener : listeners) {
                        if (listener instanceof FieldListener) {
                            try {
                                ((FieldListener) listener).onFieldChanged(_fieldIndex);
                            } catch (Throwable t) {
                                PlatformAdapter.logListenerException(t);
                            }
                        }
                    }
                }
            }
        }
    }

    private final class RaisePropertyListener implements Runnable {

        private TObject _object;

        private String _propertyName;

        public RaisePropertyListener(TObject object, String propertyName) {
            _object = object;
            _propertyName = propertyName;
        }

        public void run() {
            if (_object instanceof TIndexed) {
                PlatformSet<TObjectListener> listeners = TObjectMapEntry.get(_listeners, _object);

                if (listeners != null) {
                    for (TObjectListener listener : listeners) {
                        if (listener instanceof PropertyListener) {
                            try {
                                ((PropertyListener) listener).onPropertyChanged(_propertyName);
                            } catch (Throwable t) {
                                PlatformAdapter.logListenerException(t);
                            }
                        }
                    }
                }
            }
        }
    }

    private final class RemoveAllListeners implements Runnable {

        private TObject _object;

        public RemoveAllListeners(TObject object) {
            _object = object;
        }

        public void run() {
            TObjectMapEntry.removeIfPresent(_listeners, _object);
        }
    }
}
