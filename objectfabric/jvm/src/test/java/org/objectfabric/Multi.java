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

import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;
import org.objectfabric.Workspace.Granularity;
import org.objectfabric.generated.LimitN;

@Ignore
public class Multi extends TestsHelper {

    private int todo;

    public static final int DEFAULT_WRITE_COUNT = Debug.ENABLED ? 10 : 1000;

    public static final int FLAG_USE_ABORTS = 1 << 0;

    public static final int FLAG_NO_WRITE = 1 << 1;

    public static final int FLAG_RESETS = 1 << 2;

    public static final int MAX_FLAG = 2;

    public static final int ALL_FLAGS = (1 << (MAX_FLAG + 1)) - 1;

    @Test
    public void runSimple1() {
        run(null, 1, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runSimple2() {
        run(null, 2, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runSimple3() {
        run(null, 1, DEFAULT_WRITE_COUNT, FLAG_USE_ABORTS);
    }

    @Test
    public void runSimple4() {
        run(null, 2, DEFAULT_WRITE_COUNT, FLAG_USE_ABORTS);
    }

    @Test
    public void runSimple5() {
        run(null, 1, DEFAULT_WRITE_COUNT, FLAG_RESETS);
    }

    @Test
    public void runSimple6() {
        run(null, 2, DEFAULT_WRITE_COUNT, FLAG_RESETS);
    }

    @Test
    public void runCoalesce1() {
        run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runCoalesce2() {
        run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, FLAG_USE_ABORTS);
    }

    @Test
    public void runCoalesce3() {
        run(Granularity.COALESCE, 2, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runCoalesce4() {
        run(Granularity.COALESCE, 2, DEFAULT_WRITE_COUNT, FLAG_USE_ABORTS);
    }

    @Test
    public void runCoalesce5() {
        run(Granularity.COALESCE, 2, DEFAULT_WRITE_COUNT, FLAG_RESETS);
    }

    Resource getResource(Workspace workspace) {
        return workspace.open("");
    }

    public void run(final Granularity granularity, final int writers, final int writeCount, final int flags) {
        final Workspace workspace = Platform.get().newTestWorkspace(granularity);
        Resource resource = getResource(workspace);
        final All all = new All(resource, writers, flags);

        System.out.println();
        String message = "Starting " + writers + " threads, " + writeCount + " writes, " + "listener: " + granularity + ", ";
        message += "client flags: " + writeFlags(flags);
        System.out.println(message);

        final ArrayList<Thread> threads = new ArrayList<Thread>();
        final CyclicBarrier barrier = new CyclicBarrier(writers + 1);
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger attemptCount = new AtomicInteger();
        final AtomicInteger abortCount = new AtomicInteger();
        final AtomicInteger totalDeltas = new AtomicInteger();

        for (int t = 0; t < writers; t++) {
            Thread thread = new Thread("Thread " + t) {

                @Override
                public void run() {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    for (int i = 0; i < writeCount; i++) {
                        final AtomicInteger delta = new AtomicInteger();

                        try {
                            workspace.atomic(new Runnable() {

                                @Override
                                public void run() {
                                    All.check(all.Root, flags);

                                    if ((flags & FLAG_NO_WRITE) == 0)
                                        delta.set(All.update(all.Root, flags));
                                    else
                                        delta.set(0);

                                    if ((flags & FLAG_USE_ABORTS) != 0 && Platform.get().randomBoolean()) {
                                        abortCount.incrementAndGet();
                                        ExpectedExceptionThrower.throwAbortException();
                                    }

                                    attemptCount.incrementAndGet();
                                }
                            });

                            commitCount.incrementAndGet();
                            totalDeltas.addAndGet(delta.get());
                        } catch (AbortException ex) {
                            // Ignore, for aborts
                        }
                    }
                }
            };

            threads.add(thread);
            thread.start();
        }

        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (java.lang.InterruptedException e) {
            }
        }

        All.check(all.Root, flags);
        workspace.flushNotifications();
        LimitN limitN = (LimitN) all.Root.get("" + 0);
        int total = 0;

        for (int i = 0; i < limitN.getFieldCount(); i++)
            total += (Integer) limitN.getField(i);

        workspace.close();

        if (writers == 1)
            if ((flags & FLAG_USE_ABORTS) == 0)
                Debug.assertAlways(commitCount.get() == writeCount * writers);

        Debug.assertAlways(attemptCount.get() <= writeCount * writers * writers);

        if ((flags & FLAG_NO_WRITE) != 0)
            Debug.assertAlways(total == 0);
        else {
            Debug.assertAlways(total == totalDeltas.get());

            if (Stats.ENABLED) {
                long committed = Stats.Instance.Committed.get();
                long retries = Stats.Instance.TransactionRetries.get();
                Debug.assertAlways(committed == limitN.getFieldCount() + commitCount.get());
                Debug.assertAlways(committed + retries == limitN.getFieldCount() + attemptCount.get());
                Debug.assertAlways(Stats.Instance.ValidationRetriesMax.get() <= writers);
                Debug.assertAlways(writers * writeCount - abortCount.get() == commitCount.get());
            }
        }
    }

    public static String writeFlags(int flags) {
        StringBuilder sb = new StringBuilder();

        if ((flags & FLAG_USE_ABORTS) != 0)
            sb.append("USE_ABORTS, ");

        if ((flags & FLAG_NO_WRITE) != 0)
            sb.append("NO_WRITE, ");

        if ((flags & FLAG_RESETS) != 0)
            sb.append("RESETS, ");

        return sb.toString();
    }
}
