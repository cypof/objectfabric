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

final class GWTBuff extends Buff {

    private static final boolean _useTypedArrays = WebSocket.isSupported();

    private final byte[] _array;

    private final Uint8Array _typed;

    private int _position, _limit, _mark;

    GWTBuff(int capacity, boolean recycle) {
        super(recycle);

        if (_useTypedArrays) {
            _array = null;
            _typed = ((GWTPlatform) Platform.get()).newUint8Array(capacity);
        } else {
            _array = new byte[capacity];
            _typed = null;
        }

        _limit = capacity;
    }

    GWTBuff(Uint8Array toWrap) {
        super(false);

        _array = null;
        _typed = toWrap;
        _limit = toWrap.length();
    }

    private GWTBuff(Buff parent, byte[] array, Uint8Array typed, int position, int limit, int mark) {
        super(parent);

        _array = array;
        _typed = typed;
        _position = position;
        _limit = limit;
        _mark = mark;
    }

    @Override
    Buff duplicateInternals(Buff parent) {
        return new GWTBuff(parent, _array, _typed, _position, _limit, _mark);
    }

    @Override
    final void destroy() {
    }

    public Uint8Array typed() {
        return _typed;
    }

    public byte[] array() {
        return _array;
    }

    @Override
    public int capacity() {
        if (_useTypedArrays)
            return _typed.length();

        return _array.length;
    }

    @Override
    public int position() {
        return _position;
    }

    @Override
    public void position(int value) {
        if (Debug.ENABLED)
            Debug.assertion(value >= 0 && value <= capacity());

        _position = value;
    }

    @Override
    public int limit() {
        return _limit;
    }

    @Override
    public void limit(int value) {
        if (Debug.ENABLED)
            Debug.assertion(value >= 0 && value <= capacity());

        _limit = value;
    }

    @Override
    public void mark() {
        _mark = _position;
    }

    @Override
    public void reset() {
        _position = _mark;
    }

    @Override
    public void putByte(byte value) {
        if (Debug.ENABLED)
            Debug.assertion(_position < _limit);

        if (_useTypedArrays)
            _typed.set(_position++, value & 0xff);
        else
            _array[_position++] = value;
    }

    @Override
    public byte getByte() {
        if (Debug.ENABLED)
            Debug.assertion(_position < _limit);

        if (_useTypedArrays)
            return (byte) _typed.get(_position++);

        return _array[_position++];
    }

    @Override
    public void putShort(short value) {
        putByte((byte) (value >>> 0));
        putByte((byte) (value >>> 8));
    }

    @Override
    public short getShort() {
        int b0 = (getByte() & 0xff);
        int b1 = (getByte() & 0xff) << 8;
        return (short) (b1 | b0);
    }

    @Override
    public void putChar(char value) {
        putShort((short) value);
    }

    @Override
    public char getChar() {
        return (char) getShort();
    }

    @Override
    public void putInt(int value) {
        putByte((byte) (value >>> 0));
        putByte((byte) (value >>> 8));
        putByte((byte) (value >>> 16));
        putByte((byte) (value >>> 24));
    }

    @Override
    public int getInt() {
        int b0 = (getByte() & 0xff);
        int b1 = (getByte() & 0xff) << 8;
        int b2 = (getByte() & 0xff) << 16;
        int b3 = (getByte() & 0xff) << 24;
        return b3 | b2 | b1 | b0;
    }

    @Override
    public void putLong(long value) {
        putInt((int) (value >>> 0));
        putInt((int) (value >>> 32));
    }

    @Override
    public long getLong() {
        long i0 = getInt() & 0xffffffffL;
        long i1 = getInt() & 0xffffffffL;
        return i0 | (i1 << 32);
    }

    //

    @Override
    public void getImmutably(byte[] bytes, int offset, int length) {
        if (length > remaining())
            throw new RuntimeException();

        if (_useTypedArrays) {
            for (int i = 0; i < length; i++)
                bytes[offset + i] = (byte) _typed.get(_position + i);
        } else
            System.arraycopy(_array, _position, bytes, offset, length);
    }

    @Override
    public void putImmutably(byte[] bytes, int offset, int length) {
        if (length > remaining())
            throw new RuntimeException();

        if (_useTypedArrays) {
            for (int i = 0; i < length; i++)
                _typed.set(_position + i, bytes[offset + i] & 0xff);
        } else
            System.arraycopy(bytes, offset, _array, _position, length);
    }

    @Override
    final void putImmutably(Buff source) {
        GWTBuff buff = (GWTBuff) source;
        int length = buff.remaining();

        if (length > remaining())
            throw new RuntimeException();

        if (_useTypedArrays)
            _typed.set(buff._typed.subarray(buff._position, buff._position + length), _position);
        else
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

    //

    final Uint8Array subarray() {
        return typed().subarray(position(), limit());
    }
}