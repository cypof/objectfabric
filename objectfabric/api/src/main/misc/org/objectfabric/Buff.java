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

import java.util.concurrent.atomic.AtomicInteger;

import org.objectfabric.Continuation.IntBox;

@SuppressWarnings("serial")
abstract class Buff extends AtomicInteger {

    private static final int LARGEST_UNSPLITABLE = Long.SIZE / 8;

    static int getLargestUnsplitable() {
        int value = LARGEST_UNSPLITABLE;

        if (Debug.COMMUNICATIONS)
            if (ImmutableWriter.getCheckCommunications())
                value += ImmutableWriter.DEBUG_OVERHEAD;

        return value;
    }

    interface Recycler {

        void recycle(Buff buff);
    }

    private static final int LENGTH = 8192; // TODO tune

    // TODO a second cache for read Buff at socket size + LARGEST_UNSPLITABLE
    private static final PlatformConcurrentQueue<List<Buff>> _shared = new PlatformConcurrentQueue<List<Buff>>();

    /*
     * TODO have only one instance, only increment counter. Duplicate buffer at socket
     * write time.
     */
    private final Buff _parent;

    private final boolean _recycle;

    // TODO
    // String _debug_stack;

    Buff(boolean recycle) {
        super(1);

        _parent = null;
        _recycle = recycle;

        if (Stats.ENABLED && recycle)
            Stats.Instance.BuffCount.incrementAndGet();
    }

    Buff(Buff parent) {
        _parent = parent;
        _recycle = true;
    }

    static Buff getOrCreate() {
        ThreadContext context = ThreadContext.get();
        context.Buffs = InstanceCache.getOrCreateList(context.Buffs, _shared);
        Buff buff = null;

        if (context.Buffs.size() != 0)
            buff = context.Buffs.removeLast();

        if (buff == null) {
            int capacity = LENGTH;

            if (Debug.RANDOMIZE_TRANSFER_LENGTHS)
                capacity = getLargestUnsplitable() + Platform.get().randomInt(80000);

            buff = Platform.get().newBuff(capacity, true);
        } else {
            if (Debug.ENABLED)
                Debug.assertion(buff.get() == 0);

            buff.set(1);
        }

        if (Debug.THREADS)
            ThreadAssert.addPrivate(buff);

        buff.position(0);
        buff.limit(buff.capacity());
        buff.mark();

        if (Debug.ENABLED) {
            Debug.assertion(buff._parent == null);
            Helper.instance().toRecycle(buff);
            Debug.assertion(!Helper.instance().getLocks().containsKey(buff));
        }

        // instance._debug_stack = Platform.get().getStackAsString(new Exception());
        return buff;
    }

    static Buff createCustom(int capacity, boolean recycle) {
        Buff instance = Platform.get().newBuff(capacity, recycle);

        if (Debug.THREADS)
            ThreadAssert.addPrivate(instance);

        return instance;
    }

    /*
     * TODO remove, increment and decrement same instance and only duplicate ByteBuffer
     * when passing to channels.
     */
    final Buff duplicate() {
        if (Debug.ENABLED) {
            check(false);
            InstanceCache.checkNotCached(ThreadContext.get().Buffs, _shared, this);
            Debug.assertion(remaining() > 0);
        }

        Buff buff = this;

        if (_parent != null) {
            buff = _parent;

            if (Debug.ENABLED)
                Debug.assertion(buff._parent == null);
        }

        Buff duplicate = duplicateInternals(buff);

        if (Debug.ENABLED)
            Debug.assertion(buff.get() > 0);

        buff.incrementAndGet();

        if (Debug.THREADS)
            ThreadAssert.addPrivate(duplicate);

        if (Debug.ENABLED)
            check(false);

        return duplicate;
    }

    abstract Buff duplicateInternals(Buff parent);

    final void recycle() {
        if (Debug.THREADS)
            ThreadAssert.removePrivate(this);

        Buff buff = this;

        if (_parent != null) {
            buff = _parent;

            if (Debug.ENABLED)
                Debug.assertion(buff._parent == null);
        }

        buff.recycleImpl();
    }

    abstract void destroy();

    private final void recycleImpl() {
        if (Debug.ENABLED) {
            Debug.assertion(get() > 0);
            InstanceCache.checkNotCached(ThreadContext.get().Buffs, _shared, this);
        }

        if (decrementAndGet() == 0) {
            if (!_recycle)
                destroy();
            else {
                if (Debug.ENABLED) {
                    Helper.instance().onRecycled(this);
                    IntBox previous = Helper.instance().getLocks().remove(this);
                    Debug.assertion(previous != null);
                }

                ThreadContext context = ThreadContext.get();
                context.Buffs = InstanceCache.recycle(context.Buffs, _shared, this);
            }
        }
    }

    @Override
    public String toString() {
        return Platform.get().defaultToString(this);
    }

    //

    abstract int capacity();

    abstract int position();

    abstract void position(int value);

    abstract int limit();

    abstract void limit(int value);

    abstract void mark();

    abstract void reset();

    final int remaining() {
        return limit() - position();
    }

    //

    abstract byte getByte();

    abstract void putByte(byte value);

    abstract short getShort();

    abstract void putShort(short value);

    abstract char getChar();

    abstract void putChar(char value);

    abstract int getInt();

    abstract void putInt(int value);

    abstract long getLong();

    abstract void putLong(long value);

    //

    abstract void getImmutably(byte[] bytes, int offset, int length);

    abstract void putImmutably(byte[] bytes, int offset, int length);

    abstract void putImmutably(Buff source);

    abstract void putLeftover(Buff source);

    // Debug

    void check(boolean write) {
        if (Debug.THREADS)
            ThreadAssert.assertPrivate(this);

        Debug.assertion(getDuplicates() > 0);

        if (write) {
            Debug.assertion(_parent == null);
            IntBox lock = Helper.instance().getLocks().get(this);
            Debug.assertion(lock == null || position() <= lock.Value);
        }
    }

    final void lock(int position) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(_parent == null);
        IntBox update = new IntBox();
        update.Value = position;
        IntBox previous = Helper.instance().getLocks().put(this, update);
        Debug.assertion(previous == null || previous.Value <= position);
    }

    final int getDuplicates() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_parent != null)
            return _parent.get();

        return get();
    }
}
