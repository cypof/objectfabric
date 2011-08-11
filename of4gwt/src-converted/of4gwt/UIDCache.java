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

package of4gwt;

import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class UIDCache {

    private final Version[] _array = new Version[0xff + 1]; // TODO use shorts ?

    private final int[] _indexes;

    private int _indexesCount;

    public UIDCache(boolean allowResets) {
        if (allowResets)
            _indexes = new int[0xff + 1];
        else
            _indexes = null;
    }

    public final void reset() {
        if (Debug.ENABLED)
            Debug.assertion(_indexes != null);

        for (int i = _indexesCount - 1; i >= 0; i--)
            _array[_indexes[i]] = null;

        _indexesCount = 0;
    }

    public final boolean contains(Version shared, int index) {
        return _array[index] == shared;
    }

    public final void add(Version shared, int index) {
        if (_indexes != null)
            if (_array[index] == null)
                _indexes[_indexesCount++] = index;

        _array[index] = shared;
    }
}
