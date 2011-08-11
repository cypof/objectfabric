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

import org.junit.After;
import org.junit.Before;

import com.objectfabric.ExpectedExceptionThrower;
import com.objectfabric.Helper;
import com.objectfabric.Privileged;
import com.objectfabric.Stats;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;

public class TestsHelper extends Privileged {

    private static final int MAX_MEMORY = (int) 10e6;

    private static volatile long _memory;

    public static int getLocalAborts() {
        return (int) Stats.getInstance().LocallyAborted.get();
    }

    @Before
    public void before() {
        Debug.assertAlways(Transaction.getCurrent() == null);

        if (!skipMemory())
            _memory = getMemory();
    }

    public static void assertMemory(String message) {
        long memory = getMemory();
        Log.write("Memory (" + message + "): " + memory / 1024 + " KB");
        memoryWarning(memory);
    }

    private static void memoryWarning(long memory) {
        if (memory > MAX_MEMORY)
            Log.write("WARNING: " + memory / 1024 + " KB memory used, leak?");
    }

    protected boolean skipMemory() {
        return false;
    }

    @After
    public void after() {
        Debug.assertAlways(Transaction.getCurrent() == null);

        if (!skipMemory()) {
            long memory = getMemory();
            Log.write("Memory delta: " + _memory / 1024 + " KB -> " + memory / 1024 + " KB");
            memoryWarning(memory);
        }

        assertIdleAndCleanup();
    }

    public static void assertIdleAndCleanup() {
        Privileged.assertIdleAndCleanup();
    }

    private static long getMemory() {
        long used = 0;

        /*
         * Retries a few times because memory could get allocated by another thread
         * between GC and reads.
         */
        for (int i = 0; i < 10; i++) {
            System.gc();

            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            used = total - free;

            if (used < MAX_MEMORY)
                return used;
        }

        return used;
    }

    public static void setConflictRandom() {
        Helper.getInstance().ConflictRandom = true;
    }

    public static void disableExpectedExceptionThrowerCounter() {
        ExpectedExceptionThrower.disableCounter();
    }
}
