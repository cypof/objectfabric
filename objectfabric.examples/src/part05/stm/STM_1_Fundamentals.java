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

import junit.framework.Assert;

import org.junit.Test;

import part05.stm.generated.SimpleClass;

import com.objectfabric.Transaction;
import com.objectfabric.misc.PlatformAdapter;

/**
 * ObjectFabric is based on a Software Transactional Memory. A STM allows transactions to
 * be executed on in-memory objects. This example creates and aborts transactions to show
 * when updates are discarded or committed to transactional objects.
 */
public class STM_1_Fundamentals {

    public void run() {
        final SimpleClass object = new SimpleClass();

        /*
         * By default a transactional object behaves like a usual Java or C# object.
         */
        Assert.assertTrue(object.getInt() == 0);
        object.setInt(1);
        Assert.assertTrue(object.getInt() == 1);

        /*
         * Starting a transaction takes a snapshot of all transactional objects.
         */
        Transaction t1 = Transaction.start();

        /*
         * This update will stay in the context of transaction t1 until it commits.
         */
        object.setInt(2);
        Assert.assertTrue(object.getInt() == 2);

        /*
         * If t1 is aborted, update is discarded.
         */
        t1.abort();
        Assert.assertTrue(object.getInt() == 1);

        /*
         * If t1 is committed instead, the update becomes the new shared value.
         */
        t1 = Transaction.start();
        object.setInt(2);
        t1.commit();

        Assert.assertTrue(object.getInt() == 2);

        /*
         * The 'run' method is the default way to execute a transaction. It aborts in case
         * of exception, and retry if there was a conflict with another transaction.
         */
        Transaction.run(new Runnable() {

            public void run() {
                if (object.getInt() == 2)
                    object.setInt2(3);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        STM_1_Fundamentals test = new STM_1_Fundamentals();
        test.run();
    }

    @Test
    public void asTest() {
        run();
        PlatformAdapter.reset();
    }
}
