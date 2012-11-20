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

public final class CLRBuff extends Buff {

    // TODO use BitConverter instead of byte per byte copy

    private final byte[] _array;

    private int _position, _limit, _mark;

    CLRBuff(int capacity, boolean recycle) {
        super(recycle);

        _array = new byte[capacity];
    }

    private CLRBuff(Buff parent, byte[] array) {
        super(parent);

        _array = array;
    }

    public byte[] array() {
        return _array;
    }

    @Override
    Buff duplicateInternals(Buff parent) {
        CLRBuff buff = new CLRBuff(parent, _array);
        buff._position = _position;
        buff._limit = _limit;
        buff._mark = _mark;

        if (Debug.ENABLED) {
            check(false);
            buff.check(false);
        }

        return buff;
    }

    @Override
    final void destroy() {
        // TODO?
    }

    //

    @Override
    public final int capacity() {
        return _array.length;
    }

    @Override
    public final int position() {
        return _position;
    }

    @Override
    final void position(int value) {
        _position = value;
    }

    @Override
    public final int limit() {
        return _limit;
    }

    @Override
    final void limit(int value) {
        _limit = value;
    }

    @Override
    final void mark() {
        _mark = _position;
    }

    @Override
    final void reset() {
        _position = _mark;
    }

    //

    @Override
    final byte getByte() {
        if (Debug.ENABLED)
            Debug.assertion(_position < _limit);

        return _array[_position++];
    }

    @Override
    final void putByte(byte value) {
        if (Debug.ENABLED)
            Debug.assertion(_position < _limit);

        _array[_position++] = value;
    }

    @Override
    final short getShort() {
        int b0 = (getByte() & 0xff);
        int b1 = (getByte() & 0xff) << 8;
        return (short) (b1 | b0);
    }

    @Override
    final void putShort(short value) {
        putByte((byte) (value & 0xff));
        putByte((byte) ((value >>> 8) & 0xff));
    }

    @Override
    final char getChar() {
        return (char) getShort();
    }

    @Override
    final void putChar(char value) {
        putShort((short) value);
    }

    @Override
    final int getInt() {
        int b0 = (getByte() & 0xff);
        int b1 = (getByte() & 0xff) << 8;
        int b2 = (getByte() & 0xff) << 16;
        int b3 = (getByte() & 0xff) << 24;
        return b3 | b2 | b1 | b0;
    }

    @Override
    final void putInt(int value) {
        putByte((byte) ((value >>> 0) & 0xff));
        putByte((byte) ((value >>> 8) & 0xff));
        putByte((byte) ((value >>> 16) & 0xff));
        putByte((byte) ((value >>> 24) & 0xff));
    }

    @Override
    final long getLong() {
        long i0 = getInt() & 0xffffffffL;
        long i1 = getInt() & 0xffffffffL;
        return i0 | (i1 << 32);
    }

    @Override
    final void putLong(long value) {
        putInt((int) value);
        putInt((int) (value >>> 32));
    }

    @Override
    final void getBytes(byte[] bytes, int offset, int length) {
        if (length > remaining())
            throw new RuntimeException();

        System.arraycopy(_array, _position, bytes, offset, length);
        _position += length;
    }

    @Override
    final void putBytes(byte[] bytes, int offset, int length) {
        if (length > remaining())
            throw new RuntimeException();

        System.arraycopy(bytes, offset, _array, _position, length);
        _position += length;
    }

    //

    @Override
    final void putImmutably(Buff source) {
        CLRBuff buff = (CLRBuff) source;
        int length = buff.remaining();

        if (length > remaining())
            throw new RuntimeException();

        System.arraycopy(buff._array, buff._position, _array, _position, length);
        _position += length;
    }

    @Override
    final void putLeftover(Buff source) {
        int remaining = source.remaining();
        position(position() - remaining);
        putImmutably(source);
        source.position(source.position() + remaining);
        position(position() - remaining);
    }
}