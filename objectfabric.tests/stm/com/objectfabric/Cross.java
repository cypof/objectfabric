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

import java.util.ArrayList;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.Limit32;
import com.objectfabric.generated.LimitN;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;

/**
 * Tests transactional classes against each other.
 */
public class Cross extends TestsHelper {

    public static final int DEFAULT_WRITE_COUNT = Debug.ENABLED ? 10 : 1000;

    public static final int FLAG_USE_ABORTS = 1 << 0;

    public static final int FLAG_NO_WRITE = 1 << 1;

    public static final int FLAG_RESETS = 1 << 2;

    public static final int FLAG_MAX_OFFSET = 2;

    public static final int FLAG_ALL = (1 << (FLAG_MAX_OFFSET + 1)) - 1;

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
    public void runUseLast1() {
        run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runUseLast2() {
        run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, FLAG_USE_ABORTS);
    }

    @Test
    public void runUseLast3() {
        run(Granularity.COALESCE, 2, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runUseLast4() {
        run(Granularity.COALESCE, 2, DEFAULT_WRITE_COUNT, FLAG_USE_ABORTS);
    }

    @Test
    public void runUseLast5() {
        run(Granularity.COALESCE, 2, DEFAULT_WRITE_COUNT, FLAG_RESETS);
    }

    @Test
    public void runProcessAll1() {
        run(Granularity.ALL, 1, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runProcessAll2() {
        run(Granularity.ALL, 2, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runProcessAll3() {
        run(Granularity.ALL, 2, DEFAULT_WRITE_COUNT, FLAG_USE_ABORTS);
    }

    @Test
    public void runProcessAll4() {
        run(Granularity.ALL, 2, DEFAULT_WRITE_COUNT, FLAG_NO_WRITE);
    }

    @Test
    public void runProcessAll5() {
        run(Granularity.ALL, 2, DEFAULT_WRITE_COUNT, FLAG_RESETS);
    }

    public void run(final Granularity granularity, final int threadCount, final int writeCount, final int flags) {
        Transaction allGranularityTrunk = null;

        if (granularity == Granularity.ALL) {
            allGranularityTrunk = Site.getLocal().createTrunk(Granularity.ALL);
            Transaction.setDefaultTrunk(allGranularityTrunk);
        }

        final Limit32 c32 = new Limit32();
        final LimitN cN = new LimitN();
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>();
        final TList<Integer> listIndexes = new TList<Integer>();
        final TList<Integer> listCounters = new TList<Integer>();
        final TArrayTObject<Limit32> arrayTObjects = new TArrayTObject<Limit32>(LimitN.FIELD_COUNT);
        final TArrayInteger ref = new TArrayInteger(LimitN.FIELD_COUNT);

        // Listen for commits

        FieldListener listener = null;
        final AtomicInteger listenerCount = new AtomicInteger();
        final AtomicInteger callbackSuccessCount = new AtomicInteger();
        final AtomicInteger callbackConflictCount = new AtomicInteger();
        final int[] last = new int[LimitN.FIELD_COUNT];

        if (granularity != null) {
            listener = new FieldListener() {

                private Transaction _lastTransaction;

                @SuppressWarnings("null")
                public void onFieldChanged(int fieldIndex) {
                    if (granularity == Granularity.ALL) {
                        if ((flags & FLAG_RESETS) != 0) {
                            if (Transaction.getCurrent() != _lastTransaction) {
                                _lastTransaction = Transaction.getCurrent();
                                listenerCount.incrementAndGet();
                            }
                        } else
                            listenerCount.incrementAndGet();
                    }

                    CrossHelper.check(c32, cN, map, listIndexes, listCounters, arrayTObjects, ref, last, fieldIndex, flags);
                }
            };

            cN.addListener(listener);
        }

        System.out.println();
        String message = "Starting " + threadCount + " threads, " + writeCount + " writes, " + "listener: " + granularity + ", ";
        message += "client flags: " + writeFlags(flags);
        System.out.println(message);

        ArrayList<Thread> threads = new ArrayList<Thread>();
        final CyclicBarrier barrier = new CyclicBarrier(threadCount + 1);
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger abortCount = new AtomicInteger();
        final AtomicInteger totalDeltas = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            Thread thread = new Thread("Thread " + t) {

                @Override
                public void run() {
                    try {
                        try {
                            barrier.await();
                        } catch (BrokenBarrierException e) {
                            throw new RuntimeException(e);
                        }

                        Transaction.setDefaultTrunk(c32.getTrunk());

                        for (int i = 0; i < writeCount; i++) {
                            Transaction transaction = Transaction.start();

                            CrossHelper.check(c32, cN, map, listIndexes, listCounters, arrayTObjects, ref, flags);
                            final int delta;

                            if ((flags & FLAG_NO_WRITE) == 0)
                                delta = CrossHelper.update(c32, cN, map, listIndexes, listCounters, arrayTObjects, ref, flags);
                            else
                                delta = 0;

                            // CrossHelper.check(simple32, simple256, simpleN,
                            // map, listIndexes, listCounters, ref, null);

                            if ((flags & FLAG_USE_ABORTS) == 0 || PlatformAdapter.getRandomBoolean()) {
                                transaction.commitAsync(new AsyncCallback<CommitStatus>() {

                                    public void onSuccess(CommitStatus result) {
                                        if (threadCount == 1)
                                            Debug.assertAlways(result == CommitStatus.SUCCESS);

                                        Transaction snapshot = Transaction.start();
                                        CrossHelper.check(c32, cN, map, listIndexes, listCounters, arrayTObjects, ref, flags);
                                        snapshot.abort();

                                        if (result == CommitStatus.SUCCESS) {
                                            callbackSuccessCount.incrementAndGet();
                                            totalDeltas.addAndGet(delta);
                                        } else
                                            callbackConflictCount.incrementAndGet();
                                    }

                                    public void onFailure(Exception _) {
                                    }
                                });

                                commitCount.incrementAndGet();
                            } else {
                                transaction.abort();
                                abortCount.addAndGet(1);
                            }
                        }
                    } catch (java.lang.InterruptedException ex) {
                        throw new RuntimeException(ex);
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

        if (listener != null)
            cN.removeListener(listener);

        if (granularity == Granularity.ALL)
            Transaction.setDefaultTrunk(Site.getLocal().getTrunk());

        OF.flushNotifications();
        PlatformAdapter.reset();

        int total = 0;

        for (int i = 0; i < ref.length(); i++)
            total += ref.get(i);

        if (threadCount == 1)
            if ((flags & FLAG_USE_ABORTS) == 0)
                Debug.assertAlways(callbackSuccessCount.get() == writeCount * threadCount);

        Debug.assertAlways(commitCount.get() == callbackSuccessCount.get() + callbackConflictCount.get());

        if ((flags & FLAG_NO_WRITE) != 0) {
            Debug.assertAlways(listenerCount.get() == 0);
            Debug.assertAlways(total == 0);
        } else {
            Debug.assertAlways(total == totalDeltas.get());

            if (granularity == Granularity.ALL) {
                if ((flags & FLAG_RESETS) == 0)
                    Debug.assertAlways(listenerCount.get() == total);
                else
                    Debug.assertAlways(listenerCount.get() == callbackSuccessCount.get());
            }

            if (Stats.ENABLED) {
                Debug.assertAlways(Stats.getInstance().Committed.get() == callbackSuccessCount.get());
                Debug.assertAlways(Stats.getInstance().LocallyAborted.get() == callbackConflictCount.get());
                long aborts = Stats.getInstance().LocallyAborted.get() + abortCount.get();
                Debug.assertAlways(Stats.getInstance().Committed.get() == threadCount * writeCount - aborts);
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

    public static void main(String[] args) throws Exception {
        Cross test = new Cross();

        for (int i = 0; i < 100; i++) {
            test.before();
            test.runProcessAll1();
            test.after();
        }
    }
}
