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

import org.junit.Ignore;
import org.junit.Test;

import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.SeparateClassLoader;

@Ignore
public class VMLazyMap extends VMTest {

    public static final String SERVER_KEY = "0";

    public static final String CLIENT_KEY = "1";

    public static final String MIXED_KEY = "2";

    @Test
    public void runClientServer() {
        run(Granularity.ALL, 1, FLAG_INTERCEPT);
    }

    @Test
    public void runServerClient() {
        run(Granularity.ALL, 1, FLAG_PROPAGATE);
    }

    @Test
    public void runConflict() {
        run(Granularity.ALL, 1, FLAG_INTERCEPT | FLAG_PROPAGATE);
    }

    @Test
    public void runClientServer2() {
        run(Granularity.ALL, 2, FLAG_INTERCEPT);
    }

    @Test
    public void runServerClient2() {
        run(Granularity.ALL, 2, FLAG_PROPAGATE);
    }

    @Test
    public void runConflict2() {
        run(Granularity.ALL, 2, FLAG_INTERCEPT | FLAG_PROPAGATE);
    }

    @Test
    public void runClientServerCoalesce() {
        run(Granularity.COALESCE, 1, FLAG_INTERCEPT);
    }

    @Test
    public void runServerClientCoalesce() {
        run(Granularity.COALESCE, 1, FLAG_PROPAGATE);
    }

    @Test
    public void runConflictCoalesce() {
        run(Granularity.COALESCE, 1, FLAG_INTERCEPT | FLAG_PROPAGATE);
    }

    @Test
    public void runClientServer2Coalesce() {
        run(Granularity.COALESCE, 2, FLAG_INTERCEPT);
    }

    @Test
    public void runServerClient2Coalesce() {
        run(Granularity.COALESCE, 2, FLAG_PROPAGATE);
    }

    @Test
    public void runConflict2Coalesce() {
        run(Granularity.COALESCE, 2, FLAG_INTERCEPT | FLAG_PROPAGATE);
    }

    @Override
    public void run(Granularity granularity, int clients, int flags) {
        SeparateClassLoader thread = new SeparateClassLoader(VMLazyMapServer.class.getName());
        thread.setArgTypes(int.class, int.class, int.class);
        thread.setArgs(granularity.ordinal(), clients, flags);
        thread.start();

        try {
            thread.join();
        } catch (java.lang.InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        thread.close();
    }

    public static void main(String[] args) throws Exception {
        VMLazyMap test = new VMLazyMap();

        for (int i = 0; i < 100; i++) {
            test.before();
            test.run(Granularity.COALESCE, 1, FLAG_INTERCEPT);
            test.after();
        }
    }
}
