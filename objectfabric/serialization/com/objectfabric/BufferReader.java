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
import com.objectfabric.misc.Utils;

/**
 * Reads arrays of bytes and keeps the data that remains when switching from an array to
 * the next one. This allows the buffer which are given to the class to be reused by the
 * caller between two reads.
 */
@SingleThreaded
abstract class BufferReader extends Visitor {

    // Current buffer

    private byte[] _buffer;

    private int _offset, _limit;

    private byte _flags;

    // Data kept from last read

    private static final int CAPACITY = ImmutableWriter.LARGEST_UNSPLITABLE;

    private final byte[] _queue;

    private int _queueFirst = 0;

    private int _queueLast = 0;

    private int _queueSize = 0;

    protected BufferReader() {
        int size = CAPACITY;

        if (Debug.COMMUNICATIONS) {
            if (ImmutableWriter.getCheckCommunications()) {
                size += ImmutableWriter.DEBUG_OVERHEAD;
                size = Utils.nextPowerOf2(size);
            }
        }

        if (Debug.ENABLED)
            Debug.assertion(size == Utils.nextPowerOf2(size));

        _queue = new byte[size];
    }

    final byte[] getBuffer() {
        return _buffer;
    }

    final void saveRemaining() {
        for (; _offset < _limit; _offset++)
            push(_buffer[_offset]);
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
        return _queueSize + _limit - _offset;
    }

    final byte readByteFromBuffer() {
        if (Debug.ENABLED) {
            Debug.assertion(_queueSize > 0 || _offset < _limit);
            Debug.assertion(_flags != 0);
        }

        if (_queueSize > 0)
            return pop();

        return _buffer[_offset++];
    }

    final byte peekByteFromBuffer() {
        if (Debug.ENABLED) {
            Debug.assertion(_queueSize > 0 || _offset < _limit);
            Debug.assertion(_flags != 0);
        }

        if (_queueSize > 0)
            return peek();

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

    //

    private final byte peek() {
        return _queue[_queueFirst];
    }

    private final byte pop() {
        byte value = _queue[_queueFirst];
        _queueFirst = (_queueFirst + 1) & (_queue.length - 1);
        _queueSize--;
        return value;
    }

    private final void push(byte item) {
        _queue[_queueLast] = item;
        _queueLast = (_queueLast + 1) & (_queue.length - 1);
        _queueSize++;

        if (Debug.ENABLED)
            Debug.assertion(_queueSize <= _queue.length);
    }

    final void transferSavedFrom(BufferReader reader) {
        while (reader._queueSize > 0)
            push(reader.pop());
    }
}