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

package com.objectfabric.vm;

import org.junit.Test;

import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.SeparateClassLoader;

public class VMMethods extends TestsHelper {

    @Test
    public void runAll0() {
        run(Granularity.ALL, 0, 10, 10, 10, 1);
    }

    @Test
    public void runAll1() {
        run(Granularity.ALL, 1, 10, 10, 10, 1);
    }

    @Test
    public void runCoalesce0() {
        run(Granularity.COALESCE, 0, 10, 10, 10, 1);
    }

    @Test
    public void runCoalesce1() {
        run(Granularity.COALESCE, 1, 10, 10, 10, 1);
    }

    @Test
    public void runAll0_2() {
        run(Granularity.ALL, 0, 10, 10, 10, 2);
    }

    @Test
    public void runAll1_2() {
        run(Granularity.ALL, 1, 10, 10, 10, 2);
    }

    @Test
    public void runCoalesce0_2() {
        run(Granularity.COALESCE, 0, 10, 10, 10, 2);
    }

    @Test
    public void runCoalesce1_2() {
        run(Granularity.COALESCE, 1, 10, 10, 10, 2);
    }

    @Test
    public void runException() {
        run(Granularity.COALESCE, 2, 10, 10, 10, 2);
    }

    public final void run(Granularity granularity, int testNumber, int serverCalls, int clientCalls, int objectCount, int clientCount) {
        SeparateClassLoader thread = new SeparateClassLoader(VMMethodsServer.class.getName());
        thread.setArgTypes(int.class, int.class, int.class, int.class, int.class, int.class);
        thread.setArgs(granularity.ordinal(), testNumber, serverCalls, clientCalls, objectCount, clientCount);
        thread.start();

        try {
            thread.join();
        } catch (java.lang.InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        thread.close();
    }

    public static void main(String[] args) throws Exception {
        VMMethods test = new VMMethods();

        for (int i = 0; i < 100; i++) {
            test.before();
            test.run(Granularity.ALL, 2, 0, 1, 1, 1);
            test.after();
        }
    }
}
