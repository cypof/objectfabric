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

import com.objectfabric.transports.NIOTest;
import com.objectfabric.transports.NIOTestHTTP;

public class SocketLoad {

    @Test
    public void test1() throws Exception {
        NIOTest test = new NIOTest();

        for (int i = 0; i < 4; i++) {
            test.before();
            test.run(10);
            test.after();
        }
    }

    @Test
    public void test2() throws Exception {
        NIOTestHTTP test = new NIOTestHTTP();

        for (int i = 0; i < 4; i++) {
            test.before();
            test.run(10, true, false);
            test.after();
        }
    }

    public static void main(String[] args) throws Exception {
        SocketLoad test = new SocketLoad();
        test.test1();
    }
}
