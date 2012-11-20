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

import org.objectfabric.Actor.Flush;
import org.objectfabric.Actor.Message;
import org.objectfabric.CloseCounter.Callback;
import org.objectfabric.Counter.CounterRead;
import org.objectfabric.Counter.CounterSharedVersion;
import org.objectfabric.Counter.CounterVersion;
import org.objectfabric.Resource.ResourceRead;
import org.objectfabric.Resource.ResourceVersion;
import org.objectfabric.TObject.Version;
import org.objectfabric.ThreadAssert.AllowSharedRead;
import org.objectfabric.ThreadAssert.SingleThreaded;

/**
 * Interface between a workspace and the IO systems. Listen to updates and handles
 * serialization/deserialization.
 */
@SuppressWarnings("serial")
@SingleThreaded
final class Watcher extends Extension {

    @AllowSharedRead
    private final Run _run = new Run();

    private final Writer _writer = new Writer(this);

    private final List<Buff> _buffs = new List<Buff>();

    private final Queue<TObject> _added = new Queue<TObject>();

    private static final int NULL = -1, LOADING = -2;

    private int _peer = NULL;

    private long _time;

    private boolean _timeUsed;

    private Version[] _versions;

    //

    private final PlatformSet<Resource> _hasPendingAcks = new PlatformSet<Resource>();

    private final List<WriteFlush> _flushes = new List<WriteFlush>();

    Watcher(Workspace workspace) {
        super(workspace, true);

        if (Debug.THREADS) {
            ThreadAssert.exchangeGiveList(_run, _writer.getThreadContextObjects());
            ThreadAssert.exchangeGive(_run, this);
        }
    }

    final Actor actor() {
        return _run;
    }

    final void start() {
        if (Debug.THREADS)
            ThreadAssert.exchangeTake(_run);

        workspace().register(this, _run);

        if (Debug.THREADS) {
            ThreadAssert.exchangeGiveList(_run, _writer.getThreadContextObjects());
            ThreadAssert.exchangeGive(_run, this);
        }

        _run.onStarted();
    }

    @Override
    final boolean casSnapshotWithoutThis(Snapshot expected, Snapshot update, Exception exception) {
        boolean value = super.casSnapshotWithoutThis(expected, update, exception);

        if (value) {
            while (_buffs.size() > 0) {
                Buff buff = _buffs.removeLast();
                buff.recycle();
            }

            if (Debug.ENABLED)
                assertIdle();
        }

        return value;
    }

    final void cleanThreadContext() {
        Object key;

        if (Debug.ENABLED) {
            ThreadAssert.suspend(key = new Object());
            ThreadAssert.resume(_run, false);
        }

        if (Debug.THREADS) {
            ThreadAssert.exchangeTake(_run);
            ThreadAssert.removePrivateList(_writer.getThreadContextObjects());
            ThreadAssert.removePrivate(Watcher.this);
        }

        if (Debug.ENABLED)
            ThreadAssert.resume(key);
    }

    private final class Run extends Actor implements Runnable {

        @Override
        protected void enqueue() {
            Platform.get().execute(this);
        }

        @Override
        public void run() {
            if (Debug.ENABLED)
                ThreadAssert.resume(this, false);

            onRunStarting();
            runMessages();

            if (_peer >= 0) {
                // TODO test moving machine clock
                long min = _timeUsed ? _time + 1 : _time;

                // 1/125s since 1970, stored on 5 bytes -> good until 2248
                _time = System.currentTimeMillis() / 8;

                if (_time <= min)
                    _time = min;

                _timeUsed = false;
            }

            walk();

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded();
        }

        @Override
        void onClose(Callback closeCallback) {
            super.onClose(null);

            Object key;

            if (Debug.ENABLED) {
                ThreadAssert.suspend(key = new Object());
                ThreadAssert.resume(Run.this, false);
            }

            boolean noSave = _peer < 0;

            if (Debug.ENABLED) {
                ThreadAssert.suspend(Run.this);
                ThreadAssert.resume(key);
            }

            if (noSave)
                onClosed(closeCallback);
            else
                saveWorkspace(closeCallback);
        }

        final void onClosed(Callback closeCallback) {
            Object key;

            if (Debug.ENABLED) {
                ThreadAssert.suspend(key = new Object());
                ThreadAssert.resume(Run.this, false);
            }

            workspace().unregister(Watcher.this, _run, null);

            if (Debug.ENABLED)
                ThreadAssert.suspend(Run.this);

            closeCallback.call();

            if (Debug.ENABLED)
                ThreadAssert.resume(key);
        }
    }

    //

    @Override
    final Action onVisitingTObject(TObject object) {
        Action action = super.onVisitingTObject(object);

        if (action == Action.VISIT)
            if (object.isReferencedByURI())
                return Action.VISIT;

        return Action.SKIP;
    }

    @Override
    final Action onVisitingMap(int mapIndex) {
        Action action = super.onVisitingMap(mapIndex);

        if (snapshot().getVersionMaps()[mapIndex].isRemote())
            action = Action.SKIP;

        return action;
    }

    @Override
    final void onVisitingResource(Resource resource) {
        super.onVisitingResource(resource);

        if (interrupted())
            resume();

        if (_peer < 0) {
            if (_peer != LOADING) {
                _peer = LOADING;
                loadWorkspace();
            }

            interrupt(null);
            return;
        }

        newBlock();
    }

    @Override
    final void onVisitedResource(Resource resource) {
        super.onVisitedResource(resource);

        endBlock(resource);
    }

    @Override
    final void onVisitingVersion(Version version) {
        super.onVisitingVersion(version);

        if (_versions == null)
            _versions = new Version[OpenMap.CAPACITY];

        _versions = TransactionBase.putVersion(_versions, version);
    }

    //

    final boolean hasPendingAcks(Resource resource) {
        return _hasPendingAcks.contains(resource);
    }

    final void addHasPendingAcks(Resource resource) {
        boolean changed = _hasPendingAcks.add(resource);

        if (Debug.ENABLED)
            Debug.assertion(changed);
    }

    final boolean removeHasPendingAcks(Resource resource, boolean checkChanged) {
        boolean changed = _hasPendingAcks.remove(resource);

        if (Debug.ENABLED && checkChanged)
            Debug.assertion(changed);

        return changed;
    }

    //

    final void writeChangesUntilUpToDate(Resource resource, Object value) {
        newBlock();

        ResourceVersion version = resource.createVersion_();
        version.setValue(value);

        if (Debug.ENABLED)
            Debug.assertion(_versions == null);

        _versions = new Version[OpenMap.CAPACITY];
        _versions = TransactionBase.putVersion(_versions, version);

        for (;;) {
            _writer.writeRootVersion(value);

            if (!interrupted())
                break;

            addBuffer();
        }

        endBlock(resource);
    }

    //

    final void newBlock() {
        _writer.reset();

        if (_buffs.size() == 0) {
            Buff buff = addBuffer();
            buff.putByte(TObject.SERIALIZATION_VERSION);
        } else {
            if (Debug.ENABLED)
                Debug.assertion(_buffs.size() == 1 && _buffs.get(0).position() == 1);
        }
    }

    final void onWriting(TObject object) {
        if (!object.isReferencedByURI()) {
            object.setReferencedByURI();
            _added.add(object);
        }
    }

    private final void endBlock(final Resource resource) {
        if (_added.size() > 0) {
            visitingNewObject(true);
            int map1 = mapIndex1();
            int map2 = mapIndex2();
            mapIndex1(TransactionManager.OBJECTS_VERSIONS_INDEX);
            mapIndex2(snapshot().writes().length);

            for (;;) {
                TObject object = _added.poll();

                if (object == null)
                    break;

                if (Debug.ENABLED)
                    Debug.assertion(object.resource() == resource);

                visit(object);
            }

            mapIndex1(map1);
            mapIndex2(map2);
            visitingNewObject(false);
        }

        if (_buffs.size() > 1 || _buffs.get(0).position() > 1) {
            resource.writeNewBlock(Tick.get(_peer, _time), _versions);
            _timeUsed = true;
        }

        _versions = null;
    }

    //

    final void writeDependency(long tick) {
        for (;;) {
            _writer.writePeerTick(Writer.COMMAND_DEPENDENCY, tick);

            if (!interrupted())
                break;

            addBuffer();
        }
    }

    final void writeHappenedBefore(long[] ticks) {
        for (int i = 0; i < ticks.length; i++) {
            if (!Tick.isNull(ticks[i]) && Tick.peer(ticks[i]) != _peer) {
                for (;;) {
                    _writer.writePeerTick(Writer.COMMAND_HAPPENED_BEFORE, ticks[i]);

                    if (!interrupted())
                        break;

                    addBuffer();
                }
            }
        }
    }

    final Buff[] finishTick() {
        for (;;) {
            _writer.writeCommand(Writer.COMMAND_TICK);

            if (!interrupted())
                break;

            addBuffer();
        }

        Buff[] buffs = new Buff[_buffs.size()];
        _buffs.copyToFixed(buffs);
        _buffs.clear();

        for (int i = 0; i < buffs.length; i++) {
            buffs[i].limit(buffs[i].position());
            buffs[i].position(0);
            buffs[i].mark();

            if (Debug.ENABLED)
                buffs[i].lock(buffs[i].limit());
        }

        return buffs;
    }

    private final Buff addBuffer() {
        Buff buff = Buff.getOrCreate();
        _buffs.add(buff);
        _writer.setBuff(buff);
        return buff;
    }

    //

    final void startFlush(FutureWithCallback<Void> future) {
        _run.addAndRun(new WriteFlush(future));
    }

    final void onBlockAck(long time) {
        for (int i = _flushes.size() - 1; i >= 0; i--)
            _flushes.get(i).onBlockAck(time, i);
    }

    private final class WriteFlush extends Flush {

        private final FutureWithCallback<Void> _future;

        private long _flushedTime;

        private int _count;

        WriteFlush(FutureWithCallback<Void> future) {
            _future = future;
        }

        @Override
        final void onSuccess() {
            if (Debug.ENABLED)
                ThreadAssert.resume(_run);

            if (_hasPendingAcks.size() == 0)
                _future.set(null);
            else {
                _flushes.add(this);
                _flushedTime = _time;

                for (Resource resource : _hasPendingAcks) {
                    if (resource.pendingAcks().size() > 0)
                        _count += resource.pendingAcks().size();
                    else {
                        if (Debug.ENABLED)
                            Debug.assertion(!resource.isLoaded());

                        _count++;
                    }
                }
            }

            if (Debug.ENABLED)
                ThreadAssert.suspend(_run);
        }

        @Override
        final void onException(Exception e) {
            _future.setException(e);
        }

        final void onBlockAck(long time, int index) {
            if (time <= _flushedTime) {
                _count--;

                if (_count == 0) {
                    _future.set(null);
                    _flushes.remove(index);
                }
            }
        }
    }

    //

    private final void loadWorkspace() {
        WorkspaceLoad load = new WorkspaceLoad(workspace().resolver()) {

            @Override
            void done(final long tick, final byte[] range, final byte id) {
                _run.addAndRun(new Message() {

                    @Override
                    void run(Actor actor) {
                        if (Debug.ENABLED)
                            Debug.assertion(_peer == LOADING);

                        _peer = Tick.peer(tick);
                        _time = Tick.time(tick);
                        _timeUsed = true;
                        _writer.resume(workspace().getOrCreateRange(new UID(range)), id & 0xff);

                        if (Debug.ENABLED)
                            Debug.assertion(interrupted());
                    }
                });
            }
        };

        load.run();
    }

    private final void saveWorkspace(final Callback closeCallback) {
        WorkspaceSave save = new WorkspaceSave() {

            @Override
            void run(WorkspaceSave.Callback saveCallback) {
                Object key;

                if (Debug.ENABLED) {
                    ThreadAssert.suspend(key = new Object());
                    ThreadAssert.resume(_run, false);
                }

                long tick = Tick.get(_peer, _time);
                byte[] range = _writer.nextIdRange().uid();
                byte id = (byte) _writer.nextId();

                if (Debug.ENABLED) {
                    ThreadAssert.suspend(_run);
                    ThreadAssert.resume(key);
                }

                saveCallback.run(tick, range, id);
            }

            @Override
            void done() {
                _run.onClosed(closeCallback);
            }
        };

        save.run(workspace().resolver());
    }

    // Resource

    @Override
    final void visit(ResourceRead version) {
        for (;;) {
            _writer.writeRootRead();

            if (!_writer.interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(ResourceVersion version) {
        Resource resource = (Resource) version.object();

        if (resource.isLoaded()) {
            for (;;) {
                _writer.writeRootVersion(version.getValue() != Resource.NULL ? version.getValue() : null);

                if (!_writer.interrupted())
                    break;

                addBuffer();
            }
        } else
            _hasPendingAcks.add(resource);
    }

    // Indexed32

    @Override
    final void visit(TIndexed32Read version) {
        for (;;) {
            _writer.write(version);

            if (!_writer.interrupted())
                break;

            addBuffer();
        }
    }

    // IndexedN

    @Override
    final void visit(TIndexedNRead version) {
        for (;;) {
            _writer.write(version);

            if (!_writer.interrupted())
                break;

            addBuffer();
        }
    }

    // TKeyed

    @Override
    final void visit(TKeyedRead version) {
        TObject object = version.object();

        for (;;) {
            _writer.writeTKeyed(object, version.getEntries(), false, version.getFullyRead());

            if (!_writer.interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(TKeyedVersion version) {
        TObject object = version.object();

        for (;;) {
            _writer.writeTKeyed(object, version.getEntries(), version.getCleared(), false);

            if (!_writer.interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(TKeyedSharedVersion shared) {
        throw new IllegalStateException();
    }

    // Counter

    @Override
    final void visit(CounterRead version) {
        for (;;) {
            _writer.writeCounter(version.object(), false, 0, false);

            if (!_writer.interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(CounterVersion version) {
        for (;;) {
            _writer.writeCounter(version.object(), true, version.getDelta(), version.getReset());

            if (!_writer.interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(CounterSharedVersion shared) {
        throw new IllegalStateException();
    }

    // Debug

    final void setPeer(Peer peer) {
        _peer = peer.index();
    }

    private final void assertIdle() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_writer != null) {
            Debug.assertion(_buffs.size() == 0);
        }
    }
}
