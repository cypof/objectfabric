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

public class VMLazyMap extends TestsHelper {

    event?
    
    @Test
    public void runAll() {
        for (Granularity granularity : new Granularity[] { Granularity.ALL, Granularity.COALESCE })
            for (Granularity server : new Granularity[] { null, Granularity.ALL, Granularity.COALESCE })
                for (Granularity client : new Granularity[] { null, Granularity.ALL, Granularity.COALESCE })
                    for (int clients = 1; clients <= 8; clients += 7)
                        run(granularity, server, client, clients);
    }

    private final void run(Granularity granularity, Granularity server, Granularity client, int clients) {
        SeparateClassLoader thread = new SeparateClassLoader(VMTest4Server.class.getName());
        thread.setArgTypes(int.class, int.class, int.class, int.class);
        thread.setArgs(granularity.ordinal(), server != null ? server.ordinal() : -1, client != null ? client.ordinal() : -1, clients);
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
            test.run(Granularity.ALL, null, Granularity.ALL, 1);
            test.after();
        }
    }
}
