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

package part09;

import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Resource;
import org.objectfabric.TSet;
import org.objectfabric.Workspace;

import part05.generated.MyClass;

/**
 * ObjectFabric is based on a Software Transactional Memory. It is used internally, e.g.
 * for change tracking, but is also exposed through the atomic* methods. A STM allows
 * transactions to be executed on in-memory objects, which can simplify thread
 * coordination and exception handling. Transactions can also improve network and
 * persistence performance by batching operations.
 */
public class STM {

    public static void main(String[] args) {
        final Workspace workspace = new JVMWorkspace();
        final Resource local = workspace.open("");
        final MyClass object = new MyClass(local);

        /*
         * By default OF objects behave like usual objects.
         */
        Assert.assertEquals(0, object.field());
        object.field(1);
        Assert.assertEquals(1, object.field());

        /*
         * In Java a transaction is specified as a Runnable. Starting a transaction takes
         * a snapshot of all the workspace objects. If another thread commits a change
         * concurrently, it is not visible to the current transaction.
         */
        workspace.atomic(new Runnable() {

            @Override
            public void run() {
                /*
                 * The current transaction only performs reads (field1) so will not be
                 * restarted. We know this is the first run so we can assert the value.
                 */
                Assert.assertEquals(1, object.field());

                /*
                 * Update the field to 2 on a separate thread.
                 */
                runOnSeparateThread(new Runnable() {

                    @Override
                    public void run() {
                        object.field(2);
                    }
                });

                /*
                 * Field is still 1 for current thread.
                 */
                Assert.assertEquals(1, object.field());
            }
        });

        /*
         * Value is now 2 from the separate thread update.
         */
        Assert.assertEquals(2, object.field());

        /*
         * Updates remain private to a transaction until it commits. If transaction fails,
         * updates are discarded and never became visible to any other thread.
         */
        workspace.atomic(new Runnable() {

            @Override
            public void run() {
                /*
                 * Do an update.
                 */
                object.field(3);

                /*
                 * Read the field from a separate thread. The value is still 2 as the
                 * current transaction has not committed.
                 */
                runOnSeparateThread(new Runnable() {

                    @Override
                    public void run() {
                        Assert.assertEquals(2, object.field());
                    }
                });
            }
        });

        /*
         * Transaction is now committed, new value is 3.
         */
        Assert.assertEquals(3, object.field());

        /*
         * If a transaction aborts, its updates are discarded.
         */
        try {
            workspace.atomic(new Runnable() {

                @Override
                public void run() {
                    /*
                     * Do an update.
                     */
                    object.field(4);

                    /*
                     * Throw an exception to abort transaction.
                     */
                    throw new RuntimeException();
                }
            });
        } catch (RuntimeException ex) {
        }

        /*
         * Value is still 3.
         */
        Assert.assertEquals(3, object.field());

        /*
         * All ObjectFabric objects have shortcuts to their workspace atomic methods. The
         * transaction is still workspace-wide.
         */
        object.atomic(new Runnable() {

            @Override
            public void run() {
            }
        });

        /*
         * Multi-threading example: several threads update transactional objects a and b.
         * Using transactions, each thread is able to update multiple fields on each
         * object atomically. Threads do not see updates made by others while they update
         * variables, instead running in a consistent snapshot of memory where all fields
         * have the same value.
         */
        final MyClass a = new MyClass(local), b = new MyClass(local);
        final TSet<String> set = new TSet<String>(local);
        final int threadCount = 8, writeCount = 100;

        ArrayList<Thread> threads = new ArrayList<Thread>();
        final CyclicBarrier barrier = new CyclicBarrier(threadCount);

        for (int t = 0; t < threadCount; t++) {
            Thread thread = new Thread("Thread " + t) {

                @Override
                public void run() {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    for (int i = 0; i < writeCount; i++) {
                        workspace.atomic(new Runnable() {

                            public void run() {
                                // Assert fields equal despite concurrent updates
                                Assert.assertEquals(a.field2(), a.field());
                                Assert.assertEquals(b.field(), a.field());
                                Assert.assertEquals(b.field2(), a.field());

                                // Increment fields
                                a.field(a.field() + 1);
                                a.field2(a.field2() + 1);
                                b.field(b.field() + 1);
                                b.field2(b.field2() + 1);

                                // OF collections are also stable in a transaction and can
                                // be safely iterated while modified by other threads
                                for (String s : set)
                                    s.length();

                                set.add("value" + a.field());
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

        Assert.assertEquals(threadCount * writeCount, a.field());
        Assert.assertEquals(threadCount * writeCount, a.field2());
        Assert.assertEquals(threadCount * writeCount, b.field());
        Assert.assertEquals(threadCount * writeCount, b.field2());
        Assert.assertEquals(threadCount * writeCount, set.size());

        System.out.println("Done!");
        workspace.close();
    }

    private static void runOnSeparateThread(final Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.start();

        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
