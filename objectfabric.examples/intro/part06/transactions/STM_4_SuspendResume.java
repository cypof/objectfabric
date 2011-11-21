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

package part06.transactions;

import org.junit.Assert;
import org.junit.Test;

import part06.transactions.generated.SimpleClass;

import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.misc.PlatformAdapter;

/**
 * A transaction can be suspended and resumed, possibly by another thread which is
 * relevant for asynchronous IO. This example also shows that if a transaction is in
 * conflict with another one, its commit fails. By default conflicts are detected between
 * fields read by the transaction and fields written by transactions committed since it
 * started.
 */
public class STM_4_SuspendResume {

    public void run() {
        SimpleClass object = new SimpleClass();

        /*
         * Start value is 0.
         */
        Assert.assertTrue(object.getInt() == 0);

        /*
         * Start a transaction and make an update.
         */
        Transaction t1 = Transaction.start();
        object.setInt(object.getInt() + 1);
        Assert.assertTrue(object.getInt() == 1);

        /*
         * Suspend the transaction.
         */
        Transaction.setCurrent(null);

        /*
         * Transactional objects are restored to their previous state.
         */
        Assert.assertTrue(object.getInt() == 0);

        /*
         * Make an update outside of t1.
         */
        object.setInt(2);

        /*
         * Resume t1.
         */
        Transaction.setCurrent(t1);

        /*
         * Transactional objects are restored to their state in t1.
         */
        Assert.assertTrue(object.getInt() == 1);

        /*
         * If t1 tries to commit, it will fail since the read done during field increment
         * has been invalidated by a write outside of t1.
         */
        CommitStatus result = t1.commit();
        Assert.assertTrue(result == CommitStatus.CONFLICT);

        /*
         * T1 commit failed, so objects show last successful write.
         */
        Assert.assertTrue(object.getInt() == 2);
    }

    public static void main(String[] args) throws Exception {
        STM_4_SuspendResume test = new STM_4_SuspendResume();
        test.run();
    }

    @Test
    public void asTest() {
        run();
        PlatformAdapter.reset();
    }
}
