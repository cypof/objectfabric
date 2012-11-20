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

import org.objectfabric.ThreadAssert.SingleThreaded;

@SingleThreaded
final class IndexedCache {

    private final Object[] _array = new Object[0xff + 1];

    // TODO LRU linked list
    private final int[] _indexes;

    private int _indexesCount;

    public IndexedCache() {
        _indexes = new int[0xff + 1];
    }

    public final void reset() {
        if (Debug.ENABLED)
            Debug.assertion(_indexes != null);

        for (int i = _indexesCount - 1; i >= 0; i--)
            _array[_indexes[i]] = null;

        _indexesCount = 0;
    }

    public final boolean contains(Object object, int index) {
        return _array[index] == object;
    }

    public final void add(Object object, int index) {
        if (_indexes != null)
            if (_array[index] == null)
                _indexes[_indexesCount++] = index;

        _array[index] = object;
    }
}
