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

import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.vm.VMTest;
import com.objectfabric.vm.VMTest1;
import com.objectfabric.vm.VMTest2;
import com.objectfabric.vm.VMTest3;

public class VMLoad extends TestsHelper {

    @Test
    public void runAll1() {
        runAll(new VMTest1());
    }

    @Test
    public void runAll2() {
        runAll(new VMTest2());
    }

    @Test
    public void runAll3() {
        runAll(new VMTest3());
    }

    private final void runAll(VMTest test) {
        for (Granularity granularity : new Granularity[] { Granularity.ALL, Granularity.COALESCE }) {
            for (int clientCount = 1; clientCount <= 8; clientCount += 7) {
                for (int flags = 1; flags < VMTest.FLAG_ALL_2; flags++) {
                    test.run(granularity, clientCount, flags);
                    assertIdleAndCleanup();
                }
            }
        }
    }
}
