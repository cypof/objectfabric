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

package com.objectfabric.misc;

import java.util.Arrays;

public final class UID {

    private final byte[] _array;

    private int _hash;

    public UID(byte[] array) {
        if (array == null || array.length != PlatformAdapter.UID_BYTES_COUNT)
            throw new IllegalArgumentException();

        _array = array;
    }

    public byte[] getBytes() {
        return _array;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UID)
            return Arrays.equals(_array, ((UID) o)._array);

        return false;
    }

    @Override
    public int hashCode() {
        int h = _hash;

        if (h == 0) {
            int b0 = (_array[0] & 0xff);
            int b1 = (_array[1] & 0xff) << 8;
            int b2 = (_array[2] & 0xff) << 16;
            int b3 = (_array[3] & 0xff) << 24;
            h = b3 | b2 | b1 | b0;
            _hash = h;
        }

        return h;
    }

    public String toShortString() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        int b0 = (_array[0] & 0xff);
        int b1 = (_array[1] & 0xff) << 8;
        return Utils.padLeft(Integer.toHexString(b1 | b0), 4, '0');
    }

    @Override
    public String toString() {
        return "UID (" + Arrays.toString(_array) + ")";
    }
}
