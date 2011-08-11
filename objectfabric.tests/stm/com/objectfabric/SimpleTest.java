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

import com.objectfabric.Site;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.extensions.Logger;
import com.objectfabric.generated.SimpleClass;

public class SimpleTest extends TestsHelper {

    @Test
    public void run() {
        Logger logger = new Logger();
        logger.log(Site.getLocal().getTrunk());

        Transaction a = Transaction.start();

        SimpleClass object = new SimpleClass();

        object.setText("A");
        object.setInt0(1);

        Assert.assertEquals("A", object.getText());
        Assert.assertEquals(1, object.getInt0());

        //

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();

        Assert.assertEquals(null, object.getText());
        Assert.assertEquals(0, object.getInt0());

        object.setText("B");
        object.setInt0(2);

        Assert.assertEquals("B", object.getText());
        Assert.assertEquals(2, object.getInt0());

        Transaction.setCurrent(a);

        Assert.assertEquals("A", object.getText());
        Assert.assertEquals(1, object.getInt0());

        object.setInt1(10);

        Assert.assertEquals(CommitStatus.SUCCESS, a.commit());

        Assert.assertEquals(10, object.getInt1());

        Transaction.setCurrent(b);

        Assert.assertEquals("B", object.getText());
        Assert.assertEquals(2, object.getInt0());

        Assert.assertEquals(CommitStatus.CONFLICT, b.commit());

        Assert.assertEquals(10, object.getInt1());

        Transaction c = Transaction.start();

        Assert.assertEquals("A", object.getText());
        Assert.assertEquals(1, object.getInt0());

        c.abort();

        object.setInt1(100);

        logger.stop();
    }

    public static void main(String[] args) throws Exception {
        SimpleTest test = new SimpleTest();
        test.before();
        test.run();
        test.after();
    }
}
