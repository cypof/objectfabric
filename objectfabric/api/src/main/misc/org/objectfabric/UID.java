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

/*
 * TODO: move to integers? specialized immutable?
 */
final class UID {

    static final int LENGTH = 16;

    private byte[] _array;

    private int _hash;

    public UID(byte[] array) {
        if (Debug.ENABLED)
            Debug.assertion(array.length == LENGTH);

        _array = array;

        int b0 = (_array[0] & 0xff);
        int b1 = (_array[1] & 0xff) << 8;
        int b2 = (_array[2] & 0xff) << 16;
        int b3 = (_array[3] & 0xff) << 24;
        _hash = b3 | b2 | b1 | b0;
    }

    final byte[] getBytes() {
        return _array;
    }

    @Override
    public boolean equals(Object o) {
        UID uid = (UID) o;

        if (_array.length != uid._array.length)
            throw new IllegalStateException();

        for (int i = _array.length - 1; i >= 0; i--)
            if (_array[i] != uid._array[i])
                return false;

        return true;
    }

    @Override
    public int hashCode() {
        return _hash;
    }

    final int compare(UID uid) {
        return compare(_array, uid._array);
    }

    static int compare(byte[] a, byte[] b) {
        if (a.length != b.length)
            throw new IllegalStateException();

        for (int i = a.length - 1; i >= 0; i--) {
            if (a[i] < b[i])
                return -1;

            if (a[i] > b[i])
                return 1;
        }

        return 0;
    }

    @Override
    public String toString() {
        char[] chars = new char[10];
        Utils.getTimeHex(_hash, chars);
        return "UID (" + new String(chars, 0, 4) + ")";
    }
}
