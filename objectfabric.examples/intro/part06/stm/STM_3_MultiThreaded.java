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

package part06.stm;

import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;

import part06.stm.generated.SimpleClass;

import com.objectfabric.Transaction;
import com.objectfabric.misc.PlatformAdapter;

/**
 * Launches several threads updating the same two transactional objects a and b. Using
 * transactions, each thread is able to update several fields on each object at once while
 * seeing a consistent view where all fields have the same value before they are updated.
 * Each thread does not see updates made by other threads while they update variables.
 */
public class STM_3_MultiThreaded {

    public static final int THREAD_COUNT = 20;

    public static final int WRITE_COUNT = (int) 1e3;

    private final SimpleClass a = new SimpleClass(), b = new SimpleClass();

    public void run() {
        ArrayList<Thread> threads = new ArrayList<Thread>();
        final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            Thread thread = new Thread("Thread " + t) {

                @Override
                public void run() {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    for (int i = 0; i < WRITE_COUNT; i++) {
                        Transaction.run(new Runnable() {

                            public void run() {
                                /*
                                 * A transaction is a consistent snapshot of memory. The
                                 * three fields are always updated together in a
                                 * transaction, so they are equal even while other threads
                                 * are updating them concurrently.
                                 */
                                Assert.assertTrue(a.getInt2() == a.getInt());
                                Assert.assertTrue(b.getInt() == a.getInt());
                                Assert.assertTrue(b.getInt2() == a.getInt());

                                a.setInt(a.getInt() + 1);
                                a.setInt2(a.getInt2() + 1);
                                b.setInt(b.getInt() + 1);
                                b.setInt2(b.getInt2() + 1);
                            }
                        });
                    }
                }
            };

            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
        }

        Assert.assertTrue(a.getInt() > 0);
        Assert.assertTrue(a.getInt2() == a.getInt());
        Assert.assertTrue(b.getInt() == a.getInt());
        Assert.assertTrue(b.getInt2() == a.getInt());
    }

    public static void main(String[] args) throws Exception {
        STM_3_MultiThreaded test = new STM_3_MultiThreaded();
        test.run();
    }

    @Test
    public void asTest() {
        run();
        PlatformAdapter.reset();
    }
}
