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

import org.junit.Test;

import com.objectfabric.Cross;
import com.objectfabric.Transaction.Granularity;

public class CrossLoad extends Cross {

    @Test
    public void runAll() {
        for (Granularity granularity : new Granularity[] { null, Granularity.ALL, Granularity.COALESCE }) {
            for (int flags = 0; flags <= FLAG_ALL; flags++) {
                for (int threadCount = 1; threadCount <= 4; threadCount += 3) {
                    run(granularity, threadCount, DEFAULT_WRITE_COUNT, flags);
                    assertIdleAndCleanup();
                }
            }
        }
    }
}
