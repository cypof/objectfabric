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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.objectfabric.ConcurrentClient.Transfer;
import org.objectfabric.Workspace.Granularity;
import org.objectfabric.generated.SimpleClass;

public class Concurrent extends TestsHelper {

    /**
     * If small number, there might be no conflict between threads so use <= instead of <
     * when asserting 'successes'.
     */
    public static final int DEFAULT_WRITE_COUNT = (int) 1e3;

    private volatile SimpleClass _simple;

    private volatile int _intChangeCount, _lastInt, _int2ChangeCount, _lastInt2;

    private final AtomicInteger _changeCallbackLast = new AtomicInteger();

    private volatile CyclicBarrier _barrier;

    static {
        JVMPlatform.loadClass();
    }

    protected SimpleClass getSimpleClass() {
        return _simple;
    }

    // Overriden
    protected void setSimpleClass(SimpleClass value) {
        _simple = value;
    }

    protected AtomicInteger getChangeCallbackLast() {
        return _changeCallbackLast;
    }

    @Test
    public void runSimple1() {
        run(1, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runSimple2() {
        run(2, DEFAULT_WRITE_COUNT, 0);
    }

    @Test
    public void runSimple3() {
        run(1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS);
    }

    @Test
    public void runSimple4() {
        run(2, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS);
    }

    @Test
    public void runUseLast1() {
        run(1, DEFAULT_WRITE_COUNT, 0, Granularity.COALESCE);
    }

    @Test
    public void runUseLast2() {
        run(1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS, Granularity.COALESCE);
    }

    @Test
    public void runUseLast3() {
        run(2, DEFAULT_WRITE_COUNT, 0, Granularity.COALESCE);
    }

    @Test
    public void runUseLast4() {
        run(2, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS, Granularity.COALESCE);
    }

    @Test
    public void runProcessAll1() {
        run(1, 1, ConcurrentClient.USE_TWO_INTEGERS, Granularity.ALL);
    }

    @Test
    public void runProcessAll2() {
        run(1, DEFAULT_WRITE_COUNT, 0, Granularity.ALL);
    }

    @Test
    public void runProcessAll3() {
        run(2, DEFAULT_WRITE_COUNT, 0, Granularity.ALL);
    }

    @Test
    public void runProcessAll4() {
        run(2, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS, Granularity.ALL);
    }

    @Test
    public void runTwoIntegers1() {
        run(1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_TWO_INTEGERS, Granularity.ALL);
    }

    @Test
    public void runTwoIntegers2() {
        run(1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_TWO_INTEGERS, Granularity.COALESCE);
    }

    @Test
    public void runTwoIntegers3() {
        run(2, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_TWO_INTEGERS, Granularity.ALL);
    }

    @Test
    public void runTwoIntegers4() {
        run(2, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ONE_INTEGER_PER_CLIENT);
    }

    @Test
    public void runVoid1() {
        run(1, DEFAULT_WRITE_COUNT, ConcurrentClient.NO_WRITE);
    }

    @Test
    public void runVoid2() {
        run(8, DEFAULT_WRITE_COUNT, ConcurrentClient.NO_WRITE);
    }

    @Test
    public void runCross1() {
        run(2, DEFAULT_WRITE_COUNT, ConcurrentClient.CROSS);
    }

    @Test
    public void runCross2() {
        run(1, DEFAULT_WRITE_COUNT, ConcurrentClient.CROSS, Granularity.ALL);
    }

    @Test
    public void runTransfer1() {
        run(1, DEFAULT_WRITE_COUNT, ConcurrentClient.TRANSFER, Granularity.ALL);
    }

    @Test
    public void runAborts1() {
        run(2, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS, Granularity.COALESCE);
    }

    public void run(int threadCount, int writeCount, int clientFlags) {
        run(threadCount, writeCount, clientFlags, null);
    }

    public void run(final int threadCount, final int writeCount, final int clientFlags, final Granularity granularity) {
        final Workspace workspace = Platform.get().newTestWorkspace(granularity);
        setSimpleClass(new SimpleClass(workspace.resolve("")));

        if ((clientFlags & ConcurrentClient.TRANSFER) != 0)
            _simple.int0(Transfer.TOTAL);

        TestNotifier notifier = null;

        if (granularity != null) {
            notifier = new TestNotifier(workspace);
            workspace.forceChangeNotifier(notifier);
        }

        // Listen for commits

        IndexListener listener = null;

        if (granularity != null) {
            listener = new IndexListener() {

                private ThreadAssert _context;

                public void onSet(int fieldIndex) {
                    if (Debug.ENABLED) {
                        if (_context == null)
                            _context = ThreadAssert.getOrCreateCurrent();
                        else
                            Debug.assertion(_context == ThreadAssert.getOrCreateCurrent());
                    }

                    if ((clientFlags & ConcurrentClient.TRANSFER) != 0)
                        Transfer.assertTotal(_simple);
                    else if ((clientFlags & ConcurrentClient.CROSS) == 0) {
                        if (fieldIndex == SimpleClass.INT0_INDEX) {
                            if (granularity == Granularity.ALL)
                                Assert.assertEquals(++_intChangeCount, _simple.int0());
                            else {
                                int value = _simple.int0();
                                Assert.assertTrue(value >= _lastInt);
                                _lastInt = value;
                            }
                        }

                        if (fieldIndex == SimpleClass.INT1_INDEX) {
                            if (granularity == Granularity.ALL)
                                Assert.assertEquals(++_int2ChangeCount, _simple.int1());
                            else {
                                int value = _simple.int1();
                                Assert.assertTrue(value >= _lastInt2);
                                _lastInt2 = value;
                            }
                        }
                    }

                    _changeCallbackLast.set(_simple.int0() + _simple.int1() + _simple.int2());
                }
            };

            _simple.addListener(listener);
        }

        Log.write("");
        String message = "Starting " + threadCount + " threads, " + writeCount + " writes, " + "listener: ";
        message += granularity + ", client flags: " + ConcurrentClient.writeClientFlags(clientFlags);
        Log.write(message);

        ArrayList<Thread> threads = startCommits(threadCount, writeCount, clientFlags);
        finish(workspace, threads, writeCount, listener, _simple, clientFlags);

        if (Debug.ENABLED)
            Debug.assertion(!workspace.registered(workspace.watcher()));

        workspace.close();
    }

    protected ArrayList<Thread> startCommits(int threadCount, final int writeCount, final int flags) {
        ArrayList<Thread> threads = new ArrayList<Thread>();

        _barrier = new CyclicBarrier(threadCount + 1);

        for (int i = 0; i < threadCount; i++) {
            final int _i = i;

            Thread thread = new Thread("Thread " + i) {

                @Override
                public void run() {
                    try {
                        try {
                            _barrier.await();
                        } catch (BrokenBarrierException e) {
                            throw new RuntimeException(e);
                        }

                        ConcurrentClient.loop(_simple, _i, writeCount, flags);
                    } catch (java.lang.InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            };

            threads.add(thread);
            thread.start();
        }

        try {
            _barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return threads;
    }

    public void finish(Workspace workspace, ArrayList<Thread> threads, int writeCount, IndexListener listener, SimpleClass object, int flags) {
        long start = System.nanoTime();
        joinThreads(threads);
        long time = System.nanoTime() - start;
        finish(workspace, threads, time, writeCount, listener, object, _changeCallbackLast, flags);
    }

    public static void joinThreads(ArrayList<Thread> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (java.lang.InterruptedException e) {
            }
        }
    }

    public static void finish(Workspace workspace, ArrayList<Thread> threads, long time, int writeCount, IndexListener listener, SimpleClass object, AtomicInteger changeCallbackLast, int clientFlags) {
        Granularity granularity = workspace.granularity();
        workspace.flushNotifications();
        int successes = object.int0() + object.int1() + object.int2() + object.int3();

        /*
         * If mode == all and multiple threads, it is possible that a snapshot with not
         * all integers at max arrive last, so we would have less than expected total.
         */
        if (listener != null && !(granularity == Granularity.ALL && threads.size() > 1))
            successes = changeCallbackLast.get();

        if (HelperTest.getAbortAlways() || (clientFlags & ConcurrentClient.NO_WRITE) != 0) {
            if ((clientFlags & ConcurrentClient.TRANSFER) == 0)
                Assert.assertTrue(successes == 0);
        } else if (writeCount > 10)
            Assert.assertTrue(successes > 0);

        System.out.println(successes + " successes or callback");

        double writePerSec = (threads.size() * writeCount) * 1e9 / time;
        double successPerSec = successes * 1e9 / time;
        System.out.println((int) writePerSec + " writes/s, " + (int) successPerSec + " successes or callback/s");

        if ((clientFlags & ConcurrentClient.TRANSFER) != 0) {
            if (writeCount > 100)
                Assert.assertTrue(object.int2() > 0);

            Transfer.assertTotal(object);
        }

        if ((clientFlags & ConcurrentClient.NO_WRITE) == 0) {
            if (Stats.ENABLED) {
                if (granularity == Granularity.ALL) {
                    // Queue + cache blocks for a few threads
                    Assert.assertTrue(Stats.Instance.Created.get() <= 200);
                } else {
                    int mainThreadTx = 1;
                    int count = threads.size() + mainThreadTx;

                    if (granularity == Granularity.COALESCE) {
                        int notifierThread = ThreadPool.getLargestPoolSize();
                        count += notifierThread;
                    }

                    Assert.assertTrue(Stats.Instance.Created.get() <= count);
                }

                long vRetries = Stats.Instance.ValidationRetries.get();
                long tRetries = Stats.Instance.TransactionRetries.get();
                long started = Stats.Instance.Started.get();

                Assert.assertEquals(started, Stats.Instance.Committed.get() + Stats.Instance.Aborted.get());

                if (threads.size() == 1)
                    Assert.assertEquals(0, tRetries);
                else
                    Assert.assertTrue(tRetries < writeCount * threads.size());

                if (threads.size() == 1 && granularity == null)
                    Assert.assertEquals(0, vRetries);
                else {
                    int participants = threads.size() + (granularity != null ? 1 : 0);
                    Assert.assertTrue(vRetries < writeCount * participants * 2); // margin?
                }

                if ((clientFlags & ConcurrentClient.TRANSFER) == 0) {
                    Assert.assertEquals(successes, started - Stats.Instance.Aborted.get());

                    if ((clientFlags & ConcurrentClient.USE_ABORTS) == 0)
                        if (granularity != Granularity.COALESCE)
                            Assert.assertEquals(tRetries + 4, Stats.Instance.Aborted.get());

                    long merged = Stats.Instance.Merged.get();
                    Assert.assertTrue(merged == successes);
                }

                if ((clientFlags & ConcurrentClient.USE_ONE_INTEGER_PER_CLIENT) != 0)
                    Assert.assertEquals(0, tRetries);
            }

            long total = threads.size() * writeCount;

            if ((clientFlags & ConcurrentClient.USE_ABORTS) == 0)
                Assert.assertEquals(total, successes);
            else
                Assert.assertTrue(successes > 0 && successes < total);
        } else
            Assert.assertEquals(0, successes);
    }

    public static final class TestNotifier extends Notifier {

        private int _mapIndex;

        public TestNotifier(Workspace workspace) {
            super(workspace);
        }

        public int getMapIndex() {
            return _mapIndex;
        }

        @Override
        Action onVisitingMap(int mapIndex) {
            _mapIndex = mapIndex;

            return super.onVisitingMap(mapIndex);
        }
    }
}
