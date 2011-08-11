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

package of4gwt.misc;

public final class SparseArrayHelper {

    public static final int DEFAULT_CAPACITY = 8; // Power of two! TODO: bench

    public static final int TIMES_TWO_SHIFT = 1;

    public static final int REHASH = -1, ALREADY_PRESENT = -2;

    private SparseArrayHelper() {
    }

    // TODO: bench, try with entry count to get real load factor
    public static int attemptsStart(int length) {
        int value = 3;

        // Add 1 for every large resize in case several hashes are equal
        for (int i = 1024; i <= length; i <<= 1)
            value++;

        return value;
    }
}
