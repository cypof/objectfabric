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

final class OpenMap {

    public static final int CAPACITY = 8; // TODO: bench

    public static final int TIMES_TWO_SHIFT = 1;

    public static final int REHASH = Integer.MAX_VALUE;

    private OpenMap() {
    }

    static {
        if (Debug.ENABLED)
            Debug.assertion(Utils.nextPowerOf2(CAPACITY) == CAPACITY);
    }

    // TODO: bench, try with entry count to get real load factor
    public static int attemptsStart(int length) {
        int value = 8; // TODO tune

        // Increase for large maps in case more than 'value' hashes are equal
        for (int i = 1024; i <= length; i <<= 1)
            value++;

        return value;
    }
}
