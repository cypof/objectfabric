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

import org.junit.Assert;
import org.junit.Test;

public class FutureTest extends TestsHelper {

    private volatile String _test;

    static {
        JVMPlatform.loadClass();
    }

    @Test
    public void run() throws java.lang.InterruptedException {
        _test = null;

        final TestFuture future = new TestFuture();

        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    _test = (String) future.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();

        Thread.sleep(100);

        Assert.assertNull(_test);
        Assert.assertTrue(thread.isAlive());

        future.setResult("Bla");

        Thread.sleep(100);

        Assert.assertEquals("Bla", _test);
        Assert.assertTrue(!thread.isAlive());
    }

    private static final class TestFuture extends PlatformFuture {

        @Override
        public void run() {
        }

        @SuppressWarnings("unchecked")
        public void setResult(String value) {
            super.set(value);
        }
    }
}
