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

final class MemoryView extends View {

    private long[] _ticks;

    private Buff[] _buffs;

    MemoryView(Location location) {
        super(location);
    }

    final void dispose() {
        Object key;

        if (Debug.THREADS) {
            ThreadAssert.suspend(key = new Object());
            ThreadAssert.resume(this, false);
        }

        for (int i = 0; _buffs != null && i < _buffs.length; i++)
            if (_buffs[i] != null)
                _buffs[i].recycle();

        if (Debug.THREADS)
            ThreadAssert.resume(key);
    }

    @Override
    void getKnown(URI uri) {
        long[] ticks;

        synchronized (this) {
            ticks = _ticks != null ? _ticks.clone() : null;
        }

        if (ticks == null && !location().isCache())
            ticks = Tick.EMPTY;

        if (ticks != null)
            uri.onKnown(this, ticks);
    }

    @Override
    void onKnown(URI uri, long[] ticks) {
        boolean all = false;
        long[] list = null;
        int count = 0;

        synchronized (this) {
            if (_ticks == null || _ticks.length == 0)
                all = true;
            else {
                for (int i = 0; i < ticks.length; i++) {
                    if (!Tick.isNull(ticks[i])) {
                        if (!Tick.contains(_ticks, ticks[i])) {
                            if (list == null)
                                list = new long[ticks.length];

                            list[count++] = ticks[i];
                        }
                    }
                }
            }
        }

        if (all) {
            for (int i = 0; i < ticks.length; i++)
                if (!Tick.isNull(ticks[i]))
                    uri.getBlock(this, ticks[i]);
        } else if (list != null) {
            for (int i = 0; i < count; i++)
                uri.getBlock(this, list[i]);
        }
    }

    @Override
    void getBlock(URI uri, long tick) {
        Buff duplicate = null;

        synchronized (this) {
            if (Debug.THREADS)
                ThreadAssert.resume(this, false);

            int index = Tick.indexOf(_ticks, tick);

            if (index >= 0) {
                Buff buff = _buffs[index];

                if (buff != null) {
                    duplicate = buff.duplicate();

                    if (Debug.THREADS)
                        ThreadAssert.exchangeGive(duplicate, duplicate);
                }
            }

            if (Debug.THREADS)
                ThreadAssert.suspend(this);
        }

        if (duplicate != null) {
            Buff[] buffs = new Buff[] { duplicate };

            if (Debug.THREADS) {
                ThreadAssert.exchangeTake(duplicate);
                ThreadAssert.exchangeGive(buffs, duplicate);
            }

            uri.onBlock(this, tick, buffs, null, true, null, false);

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(buffs);

            duplicate.recycle();
        }
    }

    @Override
    void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        int capacity = 0;

        if (Debug.THREADS)
            ThreadAssert.exchangeTake(buffs);

        for (int i = 0; i < buffs.length; i++)
            capacity += buffs[i].remaining();

        Buff buff = Buff.createCustom(capacity, false);

        if (Stats.ENABLED)
            Stats.Instance.MemoryBlocksCreated.incrementAndGet();

        for (int i = 0; i < buffs.length; i++) {
            buff.putImmutably(buffs[i]);

            if (Debug.THREADS)
                ThreadAssert.exchangeGive(buffs, buffs[i]);
        }

        if (Debug.ENABLED)
            Debug.assertion(buff.remaining() == 0);

        buff.position(0);
        buff.mark();

        List<Buff> recycle = null;

        if (Debug.THREADS)
            ThreadAssert.exchangeGive(buff, buff);

        synchronized (this) {
            if (Debug.THREADS) {
                ThreadAssert.resume(this, false);
                ThreadAssert.exchangeTake(buff);
            }

            int index = Tick.indexOf(_ticks, tick);

            if (index < 0) {
                index = add(tick);
                _buffs[index] = buff;

                if (Stats.ENABLED)
                    Stats.Instance.MemoryBlocksLive.incrementAndGet();
            } else {
                if (Debug.ENABLED)
                    Debug.assertion(_buffs[index] != null);

                recycle = new List<Buff>();
                recycle.add(buff);
            }

            for (int i = 0; removals != null && i < removals.length; i++) {
                if (!Tick.isNull(removals[i])) {
                    index = Tick.remove(_ticks, removals[i]);

                    if (index >= 0) {
                        if (recycle == null)
                            recycle = new List<Buff>();

                        recycle.add(_buffs[index]);
                        _buffs[index] = null;

                        if (Stats.ENABLED)
                            Stats.Instance.MemoryBlocksLive.decrementAndGet();
                    }
                }
            }

            if (Debug.THREADS) {
                for (int i = 0; recycle != null && i < recycle.size(); i++)
                    ThreadAssert.exchangeGive(recycle, recycle.get(i));

                ThreadAssert.suspend(this);
            }
        }

        if (!location().isCache()) {
            uri.onAck(this, tick);

            if (Stats.ENABLED)
                Stats.Instance.AckCreated.incrementAndGet();
        }

        if (recycle != null) {
            if (Debug.THREADS)
                ThreadAssert.exchangeTake(recycle);

            for (int i = 0; i < recycle.size(); i++)
                recycle.get(i).recycle();
        }
    }

    private final int add(long tick) {
        long[] ticks = _ticks;
        Buff[] buffs = _buffs;

        if (ticks == null || ticks.length == 0) {
            ticks = new long[OpenMap.CAPACITY];
            buffs = new Buff[OpenMap.CAPACITY];
        } else if (Debug.THREADS)
            Tick.checkSet(ticks);

        int hash = Tick.hashTick(tick);
        int index;

        while ((index = Tick.tryToAdd(ticks, tick, hash)) < 0) {
            long[] previousTicks = ticks;
            Buff[] previousBuffs = buffs;

            for (;;) {
                ticks = new long[ticks.length << OpenMap.TIMES_TWO_SHIFT];
                buffs = new Buff[buffs.length << OpenMap.TIMES_TWO_SHIFT];

                if (rehash(previousTicks, ticks, previousBuffs, buffs))
                    break;
            }
        }

        if (Debug.ENABLED)
            Tick.checkSet(ticks);

        _ticks = ticks;
        _buffs = buffs;
        return index;
    }

    private final boolean rehash(long[] previousTicks, long[] ticks, Buff[] previousBuffs, Buff[] buffs) {
        for (int i = previousTicks.length - 1; i >= 0; i--) {
            if (!Tick.isNull(previousTicks[i])) {
                int index = Tick.tryToAdd(ticks, previousTicks[i], Tick.hashTick(previousTicks[i]));

                if (index < 0)
                    return false;

                buffs[index] = previousBuffs[i];
            }
        }

        return true;
    }
}