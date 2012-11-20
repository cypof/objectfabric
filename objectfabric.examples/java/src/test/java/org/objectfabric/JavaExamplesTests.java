/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

import launchfirst.ExamplesServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import part01.HelloWord;

public class JavaExamplesTests {

    private SeparateCL _server;

    @Before
    public void before() {
        _server = new SeparateCL(ExamplesServer.class.getName());
        _server.start();
    }

    @After
    public void after() {
        _server.interrupt();

        try {
            _server.join();
        } catch (Exception _) {
        }
    }

    @Test
    public void helloworld() {
        if (Debug.ENABLED)
            Helper.instance().ProcessName = "Client";

        HelloWord.main(null);

        if (Debug.ENABLED) {
            Helper.instance().ProcessName = "";
            Helper.instance().assertClassLoaderIdle();
        }

        int todoothers;
    }
}
