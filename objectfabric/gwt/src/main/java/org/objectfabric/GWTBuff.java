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

import com.google.gwt.core.client.JavaScriptObject;

final class GWTBuff extends Buff {

    private static final boolean _useTypedArrays = WebSocketURIHandler.isSupported();

    private final byte[] _array;

    private final JavaScriptObject _typed;

    private int _position, _limit, _mark;

    GWTBuff(int capacity, boolean recycle) {
        super(recycle);

        if (_useTypedArrays) {
            _array = null;
            _typed = createTyped(capacity);
        } else {
            _array = new byte[capacity];
            _typed = null;
        }
    }

    private GWTBuff(Buff parent, byte[] array, JavaScriptObject typed, int position, int limit, int mark) {
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

    private static native JavaScriptObject createTyped(int length) /*-{
		return new Uint8Array(length);
    }-*/;

    public JavaScriptObject getTyped() {
        return _typed;
    }

    public byte[] array() {
        return _array;
    }

    @Override
    public int capacity() {
        if (_useTypedArrays)
            return length(_typed);

        return _array.length;
    }

    private static native int length(JavaScriptObject typed) /*-{
		return typed.length;
    }-*/;

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
            set(_typed, _position++, value & 0xff);
        else
            _array[_position++] = value;
    }

    private static native void set(JavaScriptObject typed, int index, int value) /*-{
		typed[index] = value;
    }-*/;

    @Override
    public byte getByte() {
        if (Debug.ENABLED)
            Debug.assertion(_position < _limit);

        if (_useTypedArrays)
            return (byte) get(_typed, _position++);

        return _array[_position++];
    }

    private static native int get(JavaScriptObject typed, int index) /*-{
		return typed[index];
    }-*/;

    @Override
    public void putShort(short value) {
        putByte((byte) (value & 0xff));
        putByte((byte) ((value >>> 8) & 0xff));
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
        putByte((byte) ((value >>> 0) & 0xff));
        putByte((byte) ((value >>> 8) & 0xff));
        putByte((byte) ((value >>> 16) & 0xff));
        putByte((byte) ((value >>> 24) & 0xff));
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
        putInt((int) value);
        putInt((int) (value >>> 32));
    }

    @Override
    public long getLong() {
        long i0 = getInt() & 0xffffffffL;
        long i1 = getInt() & 0xffffffffL;
        return i0 | (i1 << 32);
    }

    @Override
    public void getBytes(byte[] bytes, int offset, int length) {
        if (length > remaining())
            throw new RuntimeException();

        if (_useTypedArrays) {
            for (int i = 0; i < length; i++)
                bytes[offset + i] = getByte();
        } else {
            System.arraycopy(_array, _position, bytes, offset, length);
            _position += length;
        }
    }

    @Override
    public void putBytes(byte[] bytes, int offset, int length) {
        if (length > remaining())
            throw new RuntimeException();

        if (_useTypedArrays) {
            for (int i = 0; i < length; i++)
                putByte(bytes[offset + i]);
        } else {
            System.arraycopy(bytes, offset, _array, _position, length);
            _position += length;
        }
    }

    //

    @Override
    final void putImmutably(Buff source) {
        GWTBuff buff = (GWTBuff) source;
        int length = buff.remaining();

        if (length > remaining())
            throw new RuntimeException();

        if (_useTypedArrays)
            setTyped(buff._typed, buff._position, _typed, _position, length);
        else
            System.arraycopy(buff._array, buff._position, _array, _position, length);

        _position += length;
    }

    private native void setTyped(JavaScriptObject source, int sourceOffset, JavaScriptObject target, int targetOffset, int length) /*-{
		target.set(source.subarray(sourceOffset, sourceOffset + length),
				targetOffset);
    }-*/;

    @Override
    final void putLeftover(Buff source) {
        int remaining = source.remaining();
        position(position() - remaining);
        putImmutably(source);
        source.position(source.position() + remaining);
        position(position() - remaining);
    }
}