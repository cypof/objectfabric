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

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.Helper;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Debug;

public class TransactionTest {

    @Test
    public void testReset() {
        if (Debug.ENABLED) { // TODO
            Transaction a = Transaction.start();
            Helper.getInstance().FailReset = true;
            a.commit();
            Assert.assertTrue(Helper.getInstance().LastResetFailed);

            Transaction b = Transaction.start();
            Helper.getInstance().FailReset = false;
            b.commit();
            Assert.assertFalse(Helper.getInstance().LastResetFailed);
        }
    }
}
