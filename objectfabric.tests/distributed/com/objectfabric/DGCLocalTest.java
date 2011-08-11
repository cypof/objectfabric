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
import org.junit.Ignore;
import org.junit.Test;

import com.objectfabric.Extension;
import com.objectfabric.Interception;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Status;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.tests.TestsHelper;

@Ignore
public class DGCLocalTest extends TestsHelper {

    @Test
    public void run1() {
        SimpleClass a = new SimpleClass();

        final TestExtension extension = new TestExtension(false);
        extension.register();
        extension.connect(a);

        Transaction ta = Transaction.start();
        a.setInt0(1);
        ta.beginCommit(null);

        ThreadAssert.exchangeTake(ta.getInterception());
        Assert.assertEquals(Status.LOCKED, ta.getStatusUnsafe());
        Assert.assertTrue(a.getInt0() == 1);

        Transaction.setCurrentUnsynchronized(ta);
        extension.onRequestingCommit(ta);
        Transaction.setCurrentUnsynchronized(null);
        ThreadAssert.exchangeGive(ta.getInterception(), ta);
        extension.ack(ta, false);

        Assert.assertEquals(Status.ABORTED, ta.getStatusUnsafe());
        Assert.assertTrue(a.getInt0() == 0);

        // Updates on other objects must not be affected

        SimpleClass b = new SimpleClass();

        Transaction tb = Transaction.start();
        b.setInt0(1);
        tb.beginCommit(null);

        Assert.assertTrue(b.getInt0() == 1);

        Transaction.setCurrentUnsynchronized(tb);
        Assert.assertEquals(Status.COMMITTED, tb.getStatus());
        extension.done(tb);
        Transaction.setCurrentUnsynchronized(null);

        Assert.assertTrue(b.getInt0() == 1);

        extension.unregister();
        ThreadAssert.remove(extension);
        ThreadAssert.remove(extension.getConnectionMap());
    }

    private final class TestExtension extends Extension {

        public TestExtension(boolean useAborts) {
            super(Granularity.ALL, new ConnectionMap());
        }

        @Override
        protected boolean interceptsCommits() {
            return true;
        }

        public void ack(Transaction transaction, boolean value) {
            _success = value;

            Interception interception = transaction.getInterception();

            if (value)
                interception.getExtension().onReceivedCommitAcknowledgment(transaction, null);
            else
                interception.getExtension().onReceivedCommitReject(transaction);
        }

        public boolean randomAck(Transaction transaction) {
            boolean value = true;

            if (USE_ABORTS)
                if (PlatformAdapter.getRandom().nextBoolean())
                    value = false;

            ack(transaction, value);
            return value;
        }
    }
}
