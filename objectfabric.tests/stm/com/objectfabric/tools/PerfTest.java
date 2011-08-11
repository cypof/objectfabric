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

package com.objectfabric.tools;


import com.objectfabric.TestsHelper;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.misc.PlatformAdapter;

public class PerfTest extends TestsHelper {

    public static void main(String[] args) {
        PerfTest test = new PerfTest();
        test.run();
    }

    public void run() {
        int total = 0;

        for (int i = 0; i < (int) 1e6; i++)
            total += i / 1024;

        long start = System.nanoTime();

        for (int i = 0; i < (int) 1e8; i++)
            // total += i / 1024;
            total += i >> 10;

        System.out.println(total);
        System.out.println("Perf test : " + (System.nanoTime() - start) / 1e6 + " ms");
    }

    protected static final class TestClass {

        public TestClass() {
        }
    }

    protected static void run(SimpleClass entity) {
        for (int i = 0; i < (int) 1e6; i++) {
            entity.setText("A");

            // if (!entity.getText().equals("A"))
            // throw new RuntimeException();
        }
    }

    public static void runUID() {
        for (int i = 0; i < 1e6; i++) {
            // UUID.randomUUID();
            PlatformAdapter.createUID();
            // System.out.println(id);
        }
    }
}
