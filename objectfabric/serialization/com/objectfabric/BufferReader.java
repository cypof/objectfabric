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

package com.objectfabric;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

/**
 * Reads arrays of bytes and keeps the data that remains when switching from an array to
 * the next one. This allows the buffer which are given to the class to be reused by the
 * caller between two reads.
 */
@SingleThreaded
abstract class BufferReader extends Visitor { // TODO try with ByteBuffer

    private byte[] _buffer;

    private int _offset, _limit;

    private byte _flags;

    protected BufferReader() {
    }

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
        return _limit - _offset;
    }

    final byte readByteFromBuffer() {
        if (Debug.ENABLED) {
            Debug.assertion(_offset < _limit);
            Debug.assertion(_flags != 0);
        }

        return _buffer[_offset++];
    }

    final byte peekByteFromBuffer() {
        if (Debug.ENABLED) {
            Debug.assertion(_offset < _limit);
            Debug.assertion(_flags != 0);
        }

        return _buffer[_offset];
    }

    final boolean readBooleanFromBuffer() {
        return readByteFromBuffer() != 0;
    }

    final short readShortFromBuffer() {
        int b0 = (readByteFromBuffer() & 0xff);
        int b1 = (readByteFromBuffer() & 0xff) << 8;
        return (short) (b1 | b0);
    }

    final char readCharacterFromBuffer() {
        return (char) readShortFromBuffer();
    }

    final int readIntegerFromBuffer() {
        int b0 = (readByteFromBuffer() & 0xff);
        int b1 = (readByteFromBuffer() & 0xff) << 8;
        int b2 = (readByteFromBuffer() & 0xff) << 16;
        int b3 = (readByteFromBuffer() & 0xff) << 24;
        return b3 | b2 | b1 | b0;
    }

    final long readLongFromBuffer() {
        long i0 = readIntegerFromBuffer() & 0xffffffffL;
        long i1 = readIntegerFromBuffer() & 0xffffffffL;
        return i0 | (i1 << 32);
    }
}