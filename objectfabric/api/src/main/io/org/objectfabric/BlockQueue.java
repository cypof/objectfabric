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

import org.objectfabric.Connection.Write;
import org.objectfabric.ThreadAssert.SingleThreaded;

@SuppressWarnings("serial")
@SingleThreaded
abstract class BlockQueue extends Actor {

    private static final Block REMOVED = new Block();

    private Block[] _blocks = new Block[OpenMap.CAPACITY];

    private final int[] _indexes = new int[16]; // TODO tune

    private int _size = 0;

    private int _first = 0;

    private int _last = 0;

    final void enqueueBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        if (Debug.THREADS)
            ThreadAssert.exchangeTake(buffs);

        final Buff[] duplicates = new Buff[buffs.length];

        for (int i = 0; i < buffs.length; i++) {
            duplicates[i] = buffs[i].duplicate();

            if (Debug.THREADS) {
                ThreadAssert.exchangeGive(this, duplicates[i]);
                ThreadAssert.exchangeGive(buffs, buffs[i]);
            }
        }

        final Block block = new Block(uri, tick, duplicates, requested, removals);

        addAndRun(new Message() {

            @Override
            void run(Actor actor) {
                // TODO why can be added twice?
                if (index(block.URI, block.Tick) >= 0) {
                    block.cancel();
                    return;
                }

                if (block.Removals != null) {
                    long[] value = block.Removals;

                    for (int removal = 0; removal < block.Removals.length; removal++) {
                        if (!Tick.isNull(block.Removals[removal])) {
                            int index = index(block.URI, block.Removals[removal]);

                            if (index >= 0) {
                                if (value == block.Removals)
                                    value = Platform.get().clone(block.Removals);

                                value[removal] = Tick.REMOVED;

                                if (_blocks[index].Removals != null)
                                    for (int i = 0; i < _blocks[index].Removals.length; i++)
                                        if (!Tick.isNull(_blocks[index].Removals[i]))
                                            value = Tick.add(value, _blocks[index].Removals[i]);

                                _blocks[index].cancel();
                                _blocks[index] = REMOVED;

                                if (_size <= _indexes.length)
                                    remove(index);

                                onRemove();

                                if (Debug.ENABLED)
                                    check();
                            }
                        }
                    }

                    block.Removals = value;
                }

                int index = add(block);

                if (_size < _indexes.length)
                    enqueue(index);

                if (_size == _indexes.length)
                    _last &= _blocks.length - 1;

                _size++;

                if (Stats.ENABLED)
                    Stats.Instance.BlockQueues.incrementAndGet();

                if (Debug.ENABLED)
                    check();
            }
        });
    }

    private final void onRemove() {
        _size--;

        if (Stats.ENABLED)
            Stats.Instance.BlockQueues.decrementAndGet();

        if (_size == _indexes.length) {
            _first = 0;
            _last = 0;

            for (int i = 0; i < _blocks.length; i++)
                if (_blocks[i] != null && _blocks[i] != REMOVED)
                    enqueue(i);
        }
    }

    final Block nextBlock() {
        Block block = null;

        if (_size > 0) {
            if (_size <= _indexes.length) {
                int index = dequeue();
                block = _blocks[index];
                _blocks[index] = REMOVED;
            } else {
                while (_blocks[_last] == null || _blocks[_last] == REMOVED)
                    _last = _last + 1 < _blocks.length ? _last + 1 : 0;

                block = _blocks[_last];
                _blocks[_last] = REMOVED;
            }

            onRemove();
        }

        if (Debug.ENABLED) {
            Debug.assertion(block != REMOVED);
            check();
        }

        return block;
    }

    final void recycleBlocks() {
        for (int i = 0; i < _blocks.length; i++)
            if (_blocks[i] != null && _blocks[i] != REMOVED)
                for (int j = 0; j < _blocks[i].Buffs.length; j++)
                    _blocks[i].Buffs[j].recycle();
    }

    static final class Block extends Write {

        final URI URI;

        final long Tick;

        final int Hash;

        final Buff[] Buffs;

        final boolean Requested;

        long[] Removals;

        Block(URI uri, long tick, Buff[] buffs, boolean requested, long[] removals) {
            URI = uri;
            Tick = tick;
            Buffs = buffs;
            Requested = requested;
            Removals = removals;

            Hash = hash(uri, tick);
        }

        Block() {
            URI = null;
            Tick = 0;
            Buffs = null;
            Requested = false;
            Removals = null;
            Hash = 0;
        }

        static int hash(URI uri, long tick) {
            return TKeyed.rehash(uri.hashCode() ^ (int) (tick ^ (tick >>> 32)));
        }

        @Override
        void run(Connection connection) {
            connection.write(Connection.COMMAND_ON_BLOCK, URI.path(), Tick, null);
        }

        @Override
        int runEx(Connection connection, Queue<Buff> queue, int room) {
            room = Serialization.writeBlock(connection.writer(), queue, room, Buffs, Removals, Requested);
            return room;
        }

        final void cancel() {
            if (Debug.THREADS)
                ThreadAssert.exchangeTake(Buffs);

            for (int i = 0; i < Buffs.length; i++)
                Buffs[i].recycle();
        }
    }

    private final int index(URI uri, long tick) {
        int index = Block.hash(uri, tick) & (_blocks.length - 1);

        for (int i = OpenMap.attemptsStart(_blocks.length); i >= 0; i--) {
            if (_blocks[index] == null)
                break;

            if (_blocks[index].URI == uri && _blocks[index].Tick == tick)
                return index;

            index = (index + 1) & (_blocks.length - 1);
        }

        return -1;
    }

    private final int add(Block block) {
        int index;

        while ((index = tryToAdd(block)) < 0) {
            Block[] previous = _blocks;

            for (;;) {
                _blocks = new Block[_blocks.length << OpenMap.TIMES_TWO_SHIFT];

                if (rehash(previous)) {
                    if (_size <= _indexes.length) {
                        _first = 0;
                        _last = 0;

                        for (int i = 0; i < _blocks.length; i++)
                            if (_blocks[i] != null)
                                enqueue(i);
                    }

                    break;
                }
            }
        }

        return index;
    }

    private final int tryToAdd(Block block) {
        if (Debug.ENABLED)
            Debug.assertion(index(block.URI, block.Tick) < 0);

        int index = block.Hash & (_blocks.length - 1);

        for (int i = OpenMap.attemptsStart(_blocks.length); i >= 0; i--) {
            if (_blocks[index] == null || _blocks[index] == REMOVED) {
                _blocks[index] = block;
                return index;
            }

            if (Debug.ENABLED)
                Debug.assertion(_blocks[index].URI != block.URI || _blocks[index].Tick != block.Tick);

            index = (index + 1) & (_blocks.length - 1);
        }

        return -1;
    }

    private final boolean rehash(Block[] previous) {
        for (int i = previous.length - 1; i >= 0; i--)
            if (previous[i] != null && previous[i] != REMOVED)
                if (tryToAdd(previous[i]) < 0)
                    return false;

        return true;
    }

    // C.f. Queue

    private final void enqueue(int index) {
        _indexes[_last] = index;
        _last = (_last + 1) & (_indexes.length - 1);
    }

    private final void remove(int item) {
        int index;

        for (index = 0; index < _size; index++)
            if (_indexes[(_first + index) & (_indexes.length - 1)] == item)
                break;

        if (Debug.ENABLED)
            Debug.assertion(index < _size);

        for (int i = index - 1; i >= 0; i--) {
            int a = (_first + i + 0) & (_indexes.length - 1);
            int b = (_first + i + 1) & (_indexes.length - 1);
            _indexes[b] = _indexes[a];
        }

        _first = (_first + 1) & (_indexes.length - 1);
    }

    private final int dequeue() {
        int ret = _indexes[_first];
        _first = (_first + 1) & (_indexes.length - 1);
        return ret;
    }

    private final void check() {
        if (_size <= _indexes.length) {
            PlatformSet<Integer> queued = new PlatformSet<Integer>();

            for (int i = 0; i < _size; i++)
                queued.add(_indexes[(_first + i) & (_indexes.length - 1)]);

            for (int i = 0; i < _blocks.length; i++) {
                boolean empty = _blocks[i] == null || _blocks[i] == REMOVED;
                Debug.assertion(queued.contains(i) != empty);
            }
        }
    }
}
