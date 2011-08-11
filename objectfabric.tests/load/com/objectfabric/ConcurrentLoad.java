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

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.Concurrent;
import com.objectfabric.ConcurrentClient;
import com.objectfabric.Privileged;
import com.objectfabric.Transaction.Granularity;

public class ConcurrentLoad extends Privileged {

    @Test
    public void runAllMixed() {
        Concurrent concurrent = new Concurrent();
        runAllMixed(concurrent);
    }

    public static void runAllMixed(Concurrent concurrent) {
        concurrent.before();

        for (int writeCount = 1; writeCount < 10000; writeCount *= 100) {
            for (Granularity granularity : new Granularity[] { null, Granularity.ALL, Granularity.COALESCE }) {
                for (int clientFlags = 0; clientFlags <= ConcurrentClient.USE_ALL; clientFlags++) {
                    if ((clientFlags & ConcurrentClient.MERGE_BY_SOURCE) == 0) { // TODO
                        for (int threadCount = 1; threadCount < 4; threadCount++) {
                            concurrent.reset();

                            int successes = concurrent.run(granularity, threadCount, writeCount, clientFlags);

                            assertIdleAndCleanup();

                            if ((clientFlags & ConcurrentClient.NO_WRITE) == 0)
                                Assert.assertTrue(successes > 0);
                            else if ((clientFlags & ConcurrentClient.TRANSFER) == 0)
                                Assert.assertTrue(successes == 0);
                        }
                    }
                }
            }
        }

        concurrent.after();
    }

    public static void main(String[] args) {
        runAllMixed(new Concurrent());
    }
}
