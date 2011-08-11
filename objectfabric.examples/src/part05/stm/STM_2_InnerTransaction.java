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

package part05.stm;

import org.junit.Assert;
import org.junit.Test;

import part05.stm.generated.SimpleClass;

import com.objectfabric.Transaction;
import com.objectfabric.misc.PlatformAdapter;

/**
 * Children transactions are closed, which means children transactions can be aborted and
 * retried inside their parent transactions without affecting transactional objects shared
 * values as long as the parent transaction is not committed.
 */
public class STM_2_InnerTransaction {

    public void run() {
        SimpleClass object = new SimpleClass();

        /*
         * Start value is 0.
         */
        Assert.assertTrue(object.getInt() == 0);

        /*
         * Start a transaction and make an update.
         */
        Transaction parent = Transaction.start();
        object.setInt(1);

        /*
         * Start a inner transaction inside the current one.
         */
        Transaction child = Transaction.start();

        /*
         * A child transaction sees updates from its parent transaction.
         */
        Assert.assertTrue(object.getInt() == 1);

        /*
         * Update the object in the inner transaction.
         */
        object.setInt(2);

        /*
         * If the inner is aborted, its updates are discarded but parent transaction's
         * values are still visible.
         */
        child.abort();
        Assert.assertTrue(object.getInt() == 1);

        /*
         * If the inner is committed instead, its updates become part of the parent
         * transaction.
         */
        child = Transaction.start();
        object.setInt(2);
        child.commit();
        Assert.assertTrue(object.getInt() == 2);

        /*
         * If the parent is aborted, all updates are discarded.
         */
        parent.abort();

        Assert.assertTrue(object.getInt() == 0);
    }

    public static void main(String[] args) throws Exception {
        STM_2_InnerTransaction test = new STM_2_InnerTransaction();
        test.run();
    }

    @Test
    public void asTest() {
        run();
        PlatformAdapter.reset();
    }
}
