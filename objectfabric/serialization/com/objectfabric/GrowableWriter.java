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

package com.objectfabric;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
abstract class GrowableWriter extends Writer {

    protected GrowableWriter(byte[] buffer) {
        super(true);

        setBuffer(buffer);
        setLimit(buffer.length);

        byte flags = (byte) (PlatformAdapter.SUPPORTED_SERIALIZATION_FLAGS | CompileTimeSettings.SERIALIZATION_VERSION);
        setFlags(flags);
        getBuffer()[0] = flags;
    }

    @Override
    void reset() {
        super.reset();

        // 1 byte for flags
        setOffset(1);
    }

    final void grow() {
        doubleBufferLength();
        setLimit(getBuffer().length);
        getBuffer()[0] = getFlags();
    }

    public final void write(Object object) {
        if (Debug.ENABLED)
            Debug.assertion(getLimit() == getBuffer().length);

        reset();

        for (;;) {
            writeObject(object);

            if (!interrupted())
                break;

            grow();
        }
    }
}
