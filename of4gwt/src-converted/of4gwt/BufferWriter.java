/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package of4gwt;

import of4gwt.misc.Debug;
import of4gwt.misc.ThreadAssert.SingleThreaded;
import of4gwt.misc.Utils;

@SingleThreaded
abstract class BufferWriter extends Visitor {

    private byte[] _buffer;

    private int _offset, _limit;

    private byte _flags;

    protected BufferWriter() {
    }

    //

    final byte[] getBuffer() {
        return _buffer;
    }

    final void setBuffer(byte[] buffer) {
        _buffer = buffer;
    }

    final int getOffset() {
        return _offset;
    }

    final void setOffset(int value) {
        if (Debug.ENABLED)
            Debug.assertion(value <= _buffer.length);

        _offset = value;
    }

    final int getLimit() {
        return _limit;
    }

    final void setLimit(int value) {
        if (Debug.ENABLED)
            Debug.assertion(value <= _buffer.length);

        _limit = value;
    }

    final byte getFlags() {
        return _flags;
    }

    final void setFlags(byte value) {
        _flags = value;
    }

    //

    final int remaining() {
        /*
         * TODO: always say no the first time to test all interruption sites.
         */
        return _limit - _offset;
    }

    final void doubleBufferLength() {
        _buffer = Utils.extend(_buffer);
    }

    final void writeByteToBuffer(byte value) {
        if (Debug.ENABLED) {
            Debug.assertion(_flags != 0);
            Debug.assertion(_offset < _limit);
        }

        _buffer[_offset++] = value;
    }

    final void writeBooleanToBuffer(boolean value) {
        writeByteToBuffer(value ? (byte) 1 : (byte) 0);
    }

    final void writeShortToBuffer(short value) {
        writeByteToBuffer((byte) (value & 0xff));
        writeByteToBuffer((byte) ((value >>> 8) & 0xff));
    }

    final void writeCharacterToBuffer(char value) {
        writeShortToBuffer((short) value);
    }

    final void writeIntegerToBuffer(int value) {
        writeByteToBuffer((byte) (value & 0xff));
        writeByteToBuffer((byte) ((value >>> 8) & 0xff));
        writeByteToBuffer((byte) ((value >>> 16) & 0xff));
        writeByteToBuffer((byte) ((value >>> 24) & 0xff));
    }

    final void writeLongToBuffer(long value) {
        writeIntegerToBuffer((int) value);
        writeIntegerToBuffer((int) (value >>> 32));
    }
}