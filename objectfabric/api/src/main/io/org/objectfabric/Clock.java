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

import org.objectfabric.Range.Id;
import org.objectfabric.Range.Ref;
import org.objectfabric.Resource.NewBlock;
import org.objectfabric.TObject.Version;
import org.objectfabric.ThreadAssert.AllowSharedRead;
import org.objectfabric.ThreadAssert.SingleThreaded;

/**
 * Generates vector clock ids.
 */
@SingleThreaded
class Clock {

    @AllowSharedRead
    private final Watcher _watcher;

    private final List<NewBlock> _blocks = new List<NewBlock>();

    private Peer _peer;

    private long _time;

    private long _object;

    //

    private boolean _tickUsed;

    private Range _range;

    Clock(Watcher watcher) {
        _watcher = watcher;
    }

    final Watcher watcher() {
        return _watcher;
    }

    final List<NewBlock> blocks() {
        return _blocks;
    }

    final Peer peer() {
        return _peer;
    }

    final long time() {
        return _time;
    }

    final long object() {
        return _object;
    }

    final void start() {
        if (peer() != null)
            _time = time(_time, _tickUsed);
    }

    static long time(long last, boolean used) {
        // In case machine clock moved back or writing too fast (TODO avoid drift)
        long min = used ? last + 1 : last;

        // 1/125s since 1970, stored on 5 bytes -> good until 2248
        long time = System.currentTimeMillis() / 8;

        if (time < min)
            time = min;

        return time;
    }

    void writing(Resources resources) {
    }

    final void init(Peer peer, long time, long object) {
        _peer = peer;
        _time = time;
        _object = object;
        _tickUsed = false;
    }

    final void onBlock(Resource resource, Version[] versions) {
        _blocks.add(resource.writeNewBlock(Tick.get(_peer.index(), _time), versions));
        _tickUsed = true;
    }

    void commit() {
        while (_blocks.size() > 0) {
            NewBlock block = _blocks.removeLast();
            publish(block.Resource, block.Tick, block.Buffs, block.Removals, null);
        }
    }

    static final void publish(Resource resource, long tick, Buff[] buffs, long[] removals, Location cache) {
        Object key;

        if (Debug.THREADS) {
            for (int i = 0; i < buffs.length; i++)
                ThreadAssert.exchangeGive(buffs, buffs[i]);

            ThreadAssert.suspend(key = new Object());
        }

        Exception exception = resource.uri().onBlock(resource, tick, buffs, removals, false, null, true, cache);

        if (Debug.THREADS) {
            ThreadAssert.resume(key);
            ThreadAssert.exchangeTake(buffs);
        }

        if (Debug.ENABLED)
            Debug.assertion(exception == null);
    }

    //

    final void assignId(TObject object) {
        long range = ++_object >>> Range.SHIFT;

        if (_range == null || range != _range.id().Value) {
            Id id = new Id(_peer, range);
            _range = _watcher.workspace().getOrCreateRange(id);
        }

        object.range(_range);
        object.id((int) _object & 0xff);
        _range.set(object.id(), new Ref(object));
    }
}
