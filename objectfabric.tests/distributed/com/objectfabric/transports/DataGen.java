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

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Utils;

public final class DataGen {

    private final int _readLimit, _writeLimit;

    private boolean _started;

    private int _readTotal, _writtenTotal;

    private Counter _read = new Counter(), _write = new Counter();

    public DataGen(int readLimit, int writeLimit) {
        _readLimit = readLimit;
        _writeLimit = writeLimit;
    }

    public void start() {
        _started = true;
    }

    public boolean isDone() {
        return _readTotal == _readLimit && _writtenTotal == _writeLimit;
    }

    public void read(byte[] buffer, int offset, int limit) {
        for (int i = offset; i < limit; i++) {
            byte val = buffer[i];
            byte ref = _read.next();
            Debug.assertAlways(val == ref);
        }

        int length = limit - offset;
        _readTotal += length;
    }

    public int write(byte[] buffer, int offset, int limit) {
        if (!_started)
            return -1;

        int written = 0;
        boolean done = false;

        while (offset + written < limit) {
            if (_writtenTotal == _writeLimit) {
                done = true;
                break;
            }

            buffer[offset + written] = _write.next();
            written++;
            _writtenTotal++;
        }

        return done ? (-written - 1) : written;
    }

    private static final class Counter {

        private int _value;

        private byte[] _bytes = new byte[4];

        private int _index;

        public byte next() {
            if (_index == 4) {
                _value++;
                // Log.write("" + _value);
                Utils.writeInt(_bytes, 0, _value);
                _index = 0;
            }

            return _bytes[_index++];
        }
    }
}
