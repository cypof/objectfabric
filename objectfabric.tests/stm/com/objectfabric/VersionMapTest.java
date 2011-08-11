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

import com.objectfabric.Transaction;
import com.objectfabric.generated.SimpleClass;

public class VersionMapTest {

    @Test
    public void merge() {
        Transaction barrier1 = Transaction.start();
        Transaction.setCurrent(null);

        Transaction t1 = Transaction.start();
        SimpleClass a = new SimpleClass();
        a.setInt0(1);
        t1.commit();

        Transaction barrier2 = Transaction.start();
        Transaction.setCurrent(null);

        Transaction t2 = Transaction.start();
        SimpleClass b = new SimpleClass();
        b.setInt0(2);
        t2.commit();

        Assert.assertEquals(1, a.getInt0());
        Assert.assertEquals(2, b.getInt0());

        Transaction.setCurrent(barrier2);
        barrier2.abort();

        Assert.assertEquals(1, a.getInt0());
        Assert.assertEquals(2, b.getInt0());

        Transaction.setCurrent(barrier1);
        barrier1.abort();

        Assert.assertEquals(1, a.getInt0());
        Assert.assertEquals(2, b.getInt0());
    }

    public static void main(String[] args) {
        VersionMapTest test = new VersionMapTest();
        test.merge();
    }
}
