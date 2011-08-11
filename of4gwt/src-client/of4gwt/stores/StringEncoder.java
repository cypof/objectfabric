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

package of4gwt.stores;

/**
 * Encodes the given byte array into SQLite3 blob notation, i.e. X'..'
 */
public final class StringEncoder {

    private static final char[] xdigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    private static char[] _buffer = new char[64];

    static {
        initBuffer();
    }

    private static void initBuffer() {
        _buffer[0] = 'X';
        _buffer[1] = '\'';
    }

    private StringEncoder() {
    }

    public static String encode(byte[] array, int arrayLength) {
        if (arrayLength == 0)
            return "X''";

        int length = array.length + 3;
        int index = 2;

        if (_buffer.length < length) {
            _buffer = new char[_buffer.length << 1];
            initBuffer();
        }

        for (int i = 0; i < arrayLength; i++) {
            _buffer[index++] = xdigits[array[i] >> 4];
            _buffer[index++] = xdigits[array[i] & 0x0F];
        }

        _buffer[index++] = '\'';

        return new String(_buffer, 0, index);
    }
}
