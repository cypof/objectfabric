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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@SuppressWarnings("serial")
class JVMBuff extends Buff {

    // TODO x86 specific version with Unsafe?
    private final ByteBuffer _buffer;

    JVMBuff(int capacity, boolean recycle) {
        super(recycle);

        _buffer = ByteBuffer.allocateDirect(capacity);
        _buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    private JVMBuff(Buff parent, ByteBuffer buffer) {
        super(parent);

        _buffer = buffer;
        _buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    static JVMBuff getWithPosition(int position) {
        JVMBuff buff = (JVMBuff) Buff.getOrCreate();
        buff.position(position);

        if (Debug.RANDOMIZE_TRANSFER_LENGTHS) {
            int limit = Math.min(buff.remaining(), 200);
            int rand = Platform.get().randomInt(limit - 1) + 1;
            buff.limit(position + rand);
        }

        return buff;
    }

    @Override
    Buff duplicateInternals(Buff parent) {
        JVMBuff buff = new JVMBuff(parent, _buffer.duplicate());

        if (Debug.ENABLED)
            check(false);

        return buff;
    }

    final ByteBuffer getByteBuffer() {
        return _buffer;
    }

    @Override
    final void destroy() {
        // TODO something better
        try {
            Field field = _buffer.getClass().getDeclaredField("cleaner");
            field.setAccessible(true);
            Object cleaner = field.get(_buffer);
            Method method = cleaner.getClass().getMethod("clean");
            method.setAccessible(true);
            method.invoke(cleaner);
        } catch (Exception e) {
            Log.write(e);
        }
    }

    //

    @Override
    final int capacity() {
        if (Debug.ENABLED)
            check(false);

        int value = _buffer.capacity();

        if (Debug.ENABLED)
            check(false);

        return value;
    }

    @Override
    final int position() {
        if (Debug.ENABLED)
            check(false);

        int value = _buffer.position();

        if (Debug.ENABLED)
            check(false);

        return value;
    }

    @Override
    final void position(int value) {
        if (Debug.ENABLED)
            check(false);

        _buffer.position(value);

        if (Debug.ENABLED)
            check(false);
    }

    @Override
    final int limit() {
        if (Debug.ENABLED)
            check(false);

        int value = _buffer.limit();

        if (Debug.ENABLED)
            check(false);

        return value;
    }

    @Override
    final void limit(int value) {
        if (Debug.ENABLED)
            check(false);

        _buffer.limit(value);

        if (Debug.ENABLED)
            check(false);
    }

    @Override
    void mark() {
        if (Debug.ENABLED)
            check(false);

        _buffer.mark();

        if (Debug.ENABLED)
            check(false);
    }

    @Override
    final void reset() {
        if (Debug.ENABLED)
            check(false);

        _buffer.reset();

        if (Debug.ENABLED)
            check(false);
    }

    //

    @Override
    final byte getByte() {
        if (Debug.ENABLED)
            check(false);

        return _buffer.get();
    }

    @Override
    final void putByte(byte value) {
        if (Debug.ENABLED)
            check(true);

        _buffer.put(value);
    }

    @Override
    final short getShort() {
        if (Debug.ENABLED)
            check(false);

        return _buffer.getShort();
    }

    @Override
    final void putShort(short value) {
        if (Debug.ENABLED)
            check(true);

        _buffer.putShort(value);
    }

    @Override
    final char getChar() {
        if (Debug.ENABLED)
            check(false);

        return _buffer.getChar();
    }

    @Override
    final void putChar(char value) {
        if (Debug.ENABLED)
            check(true);

        _buffer.putChar(value);
    }

    @Override
    final int getInt() {
        if (Debug.ENABLED)
            check(false);

        return _buffer.getInt();
    }

    @Override
    final void putInt(int value) {
        if (Debug.ENABLED)
            check(true);

        _buffer.putInt(value);
    }

    @Override
    final long getLong() {
        if (Debug.ENABLED)
            check(false);

        return _buffer.getLong();
    }

    @Override
    final void putLong(long value) {
        if (Debug.ENABLED)
            check(true);

        _buffer.putLong(value);
    }

    //

    @Override
    final void getImmutably(byte[] bytes, int offset, int length) {
        if (Debug.ENABLED)
            check(false);

        _buffer.duplicate().get(bytes, offset, length);
    }

    @Override
    final void putImmutably(byte[] bytes, int offset, int length) {
        if (Debug.ENABLED)
            check(true);

        _buffer.duplicate().put(bytes, offset, length);
    }

    @Override
    final void putImmutably(Buff source) {
        _buffer.put(((JVMBuff) source)._buffer.duplicate());
    }

    @Override
    final void putLeftover(Buff source) {
        int remaining = source.remaining();
        position(position() - remaining);
        _buffer.put(((JVMBuff) source)._buffer);
        position(position() - remaining);
    }

    // Debug

    // For Chronon recording
    static final class TestBuff extends JVMBuff {

        int _position, _limit, _mark;

        TestBuff(int capacity, boolean recycle) {
            super(capacity, recycle);
        }

        @Override
        void check(boolean write) {
            super.check(write);

            _position = getByteBuffer().position();
            _limit = getByteBuffer().limit();
        }

        @Override
        void mark() {
            super.mark();

            _mark = getByteBuffer().position();
        }
    }
}
