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

package of4gwt.transports;

import of4gwt.misc.Debug;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.Queue;

public final class RandomSplitter {

    private final Queue<byte[]> _received = new Queue<byte[]>();

    private int _firstReceivedOffset;

    private int _totalLength;

    private int _minLength;

    public void setMinLength(int value) {
        _minLength = value;
    }

    public int getRemaining() {
        return _totalLength;
    }

    public byte[] read(byte[] buffer, int offset, int limit, int maxResultLength) {
        if (!Debug.RANDOMIZE_TRANSFER_LENGTHS) {
            byte[] temp = new byte[limit - offset];
            System.arraycopy(buffer, offset, temp, 0, temp.length);
            return temp;
        }

        if (limit > offset) {
            byte[] copy = new byte[limit - offset];
            System.arraycopy(buffer, offset, copy, 0, copy.length);
            _received.add(copy);
            _totalLength += limit - offset;
        }

        byte[] temp;

        if (_totalLength + 1 <= _minLength)
            temp = new byte[_totalLength];
        else {
            int length = Math.min(Debug.RANDOMIZED_TRANSFER_LIMIT, _totalLength + 1);
            int rand = PlatformAdapter.getRandomInt(length - _minLength) + _minLength;

            if (maxResultLength != 0)
                rand = Math.min(rand, maxResultLength);

            temp = new byte[rand];
        }

        if (temp.length > 0) {
            _totalLength -= temp.length;
            int written = 0;

            for (;;) {
                byte[] current = _received.peek();
                int min = Math.min(current.length - _firstReceivedOffset, temp.length - written);
                System.arraycopy(current, _firstReceivedOffset, temp, written, min);
                written += min;

                if (_firstReceivedOffset + min == current.length) {
                    _received.poll();
                    _firstReceivedOffset = 0;
                } else
                    _firstReceivedOffset += min;

                if (written == temp.length)
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
