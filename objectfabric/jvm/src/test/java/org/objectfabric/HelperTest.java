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

public class HelperTest extends TestsHelper {

    private final VersionMap _0 = new VersionMap();

    private final VersionMap _1 = new VersionMap();

    private final VersionMap _2 = new VersionMap();

    private final VersionMap _3 = new VersionMap();

    private final VersionMap _4 = new VersionMap();

    private final VersionMap[] _cs = new VersionMap[] { _0, _1, _2, _3, _4 };

    @Test
    public void run1() {
        VersionMap[] a = Helper.removeVersionMap(_cs, 2);
        Assert.assertTrue(a.length == 4);
        Assert.assertTrue(a[0] == _0);
        Assert.assertTrue(a[1] == _1);
        Assert.assertTrue(a[2] == _3);
        Assert.assertTrue(a[3] == _4);
        clearThreadContext();
    }

    @Test
    public void run2() {
        VersionMap[] a = Helper.removeVersionMap(_cs, 0);
        Assert.assertTrue(a.length == 4);
        Assert.assertTrue(a[0] == _1);
        Assert.assertTrue(a[1] == _2);
        Assert.assertTrue(a[2] == _3);
        Assert.assertTrue(a[3] == _4);
        clearThreadContext();
    }

    @Test
    public void run3() {
        VersionMap[] a = Helper.removeVersionMap(_cs, 4);
        Assert.assertTrue(a.length == 4);
        Assert.assertTrue(a[0] == _0);
        Assert.assertTrue(a[1] == _1);
        Assert.assertTrue(a[2] == _2);
        Assert.assertTrue(a[3] == _3);
        clearThreadContext();
    }

    public static boolean getAbortAlways() {
        if (Helper.instance() != null)
            return Helper.instance().ConflictAlways;

        return false;
    }

    public static void setAbortAlways(boolean value) {
        Helper.instance().ConflictAlways = value;
    }

    public static void setAbortRandom(boolean value) {
        Helper.instance().ConflictRandom = value;
    }

    private final void clearThreadContext() {
        if (Debug.THREADS) {
            ThreadAssert.removePrivate(_0);
            ThreadAssert.removePrivate(_1);
            ThreadAssert.removePrivate(_2);
            ThreadAssert.removePrivate(_3);
            ThreadAssert.removePrivate(_4);
        }
    }
}
