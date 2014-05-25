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
import org.objectfabric.CloseCounter.Callback;
import org.objectfabric.Counter.CounterRead;
import org.objectfabric.Counter.CounterSharedVersion;
import org.objectfabric.Counter.CounterVersion;
import org.objectfabric.Resource.NewBlock;
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

    private final PlatformMap<Resource, Resource> _hasPendingAcks = new PlatformMap<Resource, Resource>();

    private final List<WriteFlush> _flushes = new List<WriteFlush>();

    private Clock _clock;

    private Version[] _versions;

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

    final Clock clock() {
        return _clock;
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
            if (_clock != null)
                ThreadAssert.removePrivate(_clock);

            ThreadAssert.exchangeTake(_run);
            ThreadAssert.removePrivateList(_writer.getThreadContextObjects());
            ThreadAssert.removePrivate(Watcher.this);
        }

        if (Debug.ENABLED)
            ThreadAssert.resume(key);
    }

    final void run() {
        if (Debug.ENABLED)
            Debug.assertion(Platform.get().value() == Platform.GWT);

        _run.run();
    }

    private final class Run extends Actor implements Runnable {

        @Override
        protected void enqueue() {
            Platform.get().execute(this);
        }

        private static final int WALK = 0;

        private static final int COMMIT = 1;

        @Override
        public void run() {
            if (Debug.ENABLED)
                ThreadAssert.resume(this, false);

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(this);

            onRunStarting();
            runMessages(interrupted());

            int step = WALK;

            if (interrupted())
                step = resumeInt();
            else {
                if (_clock != null)
                    _clock.start();
            }

            switch (step) {
                case WALK: {
                    walk();

                    if (interrupted()) {
                        interruptInt(WALK);
                        break;
                    }
                }
                case COMMIT: {
                    if (_clock != null)
                        _clock.commit();

                    if (interrupted()) {
                        interruptInt(COMMIT);
                        break;
                    }
                }
            }

            boolean interrupted = interrupted();

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(interrupted);
        }

        @Override
        void onClose(Callback closeCallback) {
            super.onClose(null);
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

    private final Clock createClock() {
        Clock clock = request(workspace().caches());

        if (clock == null)
            clock = request(workspace().uriHandlers());

        if (clock == null) {
            for (Location location : workspace().resolver().origins().keySet()) {
                clock = location.newClock(this);

                if (clock != null)
                    break;
            }
        }

        if (clock == null)
            clock = workspace().newDefaultClock();

        return clock;
    }

    private final Clock request(Object[] array) {
        for (int i = 0; array != null && i < array.length; i++) {
            if (array[i] instanceof Location) {
                Clock clock = ((Location) array[i]).newClock(this);

                if (clock != null)
                    return clock;
            }
        }

        return null;
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
    void onVisitingResources(Resources resources) {
        super.onVisitingResources(resources);

        if (resources.size() > 0) {
            if (_clock == null)
                _clock = createClock();

            _clock.writing(resources);
        }
    }

    @Override
    final void onVisitingResource(Resource resource) {
        super.onVisitingResource(resource);

        if (interrupted())
            resume();

        if (_clock.peer() == null) {
            interrupt(null);
            return;
        }

        _writer.reset();

        if (_buffs.size() == 0) {
            Buff buff = addBuffer();
            buff.putByte(TObject.SERIALIZATION_VERSION);
        } else {
            if (Debug.ENABLED)
                Debug.assertion(_buffs.size() == 1 && _buffs.get(0).position() == 1);
        }
    }

    @Override
    final void onVisitingVersion(Version version) {
        super.onVisitingVersion(version);

        if (_versions == null)
            _versions = new Version[OpenMap.CAPACITY];

        _versions = TransactionBase.putVersion(_versions, version);
    }

    final void onWriting(TObject object) {
        if (!object.isReferencedByURI()) {
            object.setReferencedByURI();
            _added.add(object);
        }
    }

    @Override
    final void onVisitedResource(Resource resource) {
        super.onVisitedResource(resource);

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

        if (_buffs.size() > 1 || _buffs.get(0).position() > 1)
            _clock.onBlock(resource, _versions);

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
            if (!Tick.isNull(ticks[i]) && Tick.peer(ticks[i]) != _clock.peer().index()) {
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

    final boolean hasPendingAcks(Resource resource) {
        return _hasPendingAcks.containsKey(resource);
    }

    final void addHasPendingAcks(Resource resource) {
        Resource previous = _hasPendingAcks.put(resource, resource);

        if (Debug.ENABLED)
            Debug.assertion(previous == null);
    }

    final boolean removeHasPendingAcks(Resource resource, boolean checkChanged) {
        Resource previous = _hasPendingAcks.remove(resource);

        if (Debug.ENABLED && checkChanged)
            Debug.assertion(previous != null);

        return previous != null;
    }

    //

    final void startFlush(FutureWithCallback<Void> future) {
        _run.addAndRun(new WriteFlush(future));
    }

    final void onBlockAck(NewBlock block) {
        for (int i = _flushes.size() - 1; i >= 0; i--)
            _flushes.get(i).onBlockAck(block, i);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final class WriteFlush extends Flush {

        private final FutureWithCallback<Void> _future;

        private PlatformMap<NewBlock, NewBlock> _pending;

        WriteFlush(FutureWithCallback<Void> future) {
            _future = future;
        }

        @Override
        final void done() {
            if (Debug.ENABLED)
                ThreadAssert.resume(_run);

            if (_hasPendingAcks.size() == 0)
                _future.set(null);
            else {
                _flushes.add(this);
                _pending = new PlatformMap<NewBlock, NewBlock>();

                for (Resource resource : _hasPendingAcks.keySet()) {
                    if (Debug.ENABLED)
                        Debug.assertion(resource.pendingAcks().size() > 0);

                    for (NewBlock block : resource.pendingAcks().values())
                        _pending.put(block, block);
                }
            }

            if (Debug.ENABLED)
                ThreadAssert.suspend(_run);
        }

        final void onBlockAck(NewBlock block, int index) {
            NewBlock removed = _pending.remove(block);

            if (Debug.ENABLED)
                Debug.assertion(removed != null);

            if (_pending.size() == 0) {
                _future.set(null);
                _flushes.remove(index);
            }
        }
    }

    // Resource

    @Override
    final void visit(ResourceRead version) {
        for (;;) {
            _writer.writeRootRead();

            if (!interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(ResourceVersion version) {
        for (;;) {
            _writer.writeRootVersion(version.getValue() != Resource.NULL ? version.getValue() : null);

            if (!interrupted())
                break;

            addBuffer();
        }
    }

    // Indexed32

    @Override
    final void visit(TIndexed32Read version) {
        for (;;) {
            _writer.write(version);

            if (!interrupted())
                break;

            addBuffer();
        }
    }

    // IndexedN

    @Override
    final void visit(TIndexedNRead version) {
        for (;;) {
            _writer.write(version);

            if (!interrupted())
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

            if (!interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(TKeyedVersion version) {
        TObject object = version.object();

        for (;;) {
            _writer.writeTKeyed(object, version.getEntries(), version.getCleared(), false);

            if (!interrupted())
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

            if (!interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(CounterVersion version) {
        for (;;) {
            _writer.writeCounter(version.object(), true, version.getDelta(), version.getReset());

            if (!interrupted())
                break;

            addBuffer();
        }
    }

    @Override
    final void visit(CounterSharedVersion shared) {
        throw new IllegalStateException();
    }

    // Debug

    private final void assertIdle() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_writer != null) {
            Debug.assertion(_buffs.size() == 0);
        }
    }
}
