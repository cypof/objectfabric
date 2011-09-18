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

package com.objectfabric.transports;

import com.objectfabric.Reader;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.Queue;

public final class RandomSplitter {

    private final Queue<byte[]> _received = new Queue<byte[]>();

    private int _firstReceivedOffset;

    private int _totalLength;

    public int getRemaining() {
        return _totalLength;
    }

    public byte[] read(byte[] buffer, int offset, int limit, int maxResultLength) {
        if (!Debug.RANDOMIZE_TRANSFER_LENGTHS) {
            byte[] temp = new byte[Reader.LARGEST_UNSPLITABLE + limit - offset];
            System.arraycopy(buffer, offset, temp, Reader.LARGEST_UNSPLITABLE, limit - offset);
            return temp;
        }

        if (limit > offset) {
            byte[] copy = new byte[limit - offset];
            System.arraycopy(buffer, offset, copy, 0, copy.length);
            _received.add(copy);
            _totalLength += limit - offset;
        }

        int length = Math.min(Debug.RANDOMIZED_TRANSFER_LIMIT, _totalLength + 1);
        length = PlatformAdapter.getRandomInt(length);

        if (maxResultLength != 0)
            length = Math.min(length, maxResultLength);

        return get(length);
    }

    private byte[] get(int length) {
        byte[] temp = new byte[Reader.LARGEST_UNSPLITABLE + length];

        if (length > 0) {
            _totalLength -= length;
            int offset = Reader.LARGEST_UNSPLITABLE;

            for (;;) {
                byte[] current = _received.peek();
                int min = Math.min(current.length - _firstReceivedOffset, temp.length - offset);
                System.arraycopy(current, _firstReceivedOffset, temp, offset, min);
                offset += min;

                if (_firstReceivedOffset + min == current.length) {
                    _received.poll();
                    _firstReceivedOffset = 0;
                } else
                    _firstReceivedOffset += min;

                if (offset == temp.length)
                    break;
            }
        }

        return temp;
    }

    public void clear() {
        _received.clear();
        _firstReceivedOffset = 0;
        _totalLength = 0;
    }
}
