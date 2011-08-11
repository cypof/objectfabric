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
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.Acknowledger;
import com.objectfabric.AsyncOptions;
import com.objectfabric.FieldListener;
import com.objectfabric.Interceptor;
import com.objectfabric.Notifier;
import com.objectfabric.OF;
import com.objectfabric.Site;
import com.objectfabric.Snapshot;
import com.objectfabric.Stats;
import com.objectfabric.Transaction;
import com.objectfabric.TransactionPublic;
import com.objectfabric.VersionMap;
import com.objectfabric.Visitor;
import com.objectfabric.ConcurrentClient.TestConnectionVersion;
import com.objectfabric.ConcurrentClient.Transfer;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.VersionMap.Source;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformConcurrentMap;
import com.objectfabric.misc.ThreadAssert;

public class Concurrent extends TestsHelper {

    /**
     * If small number, there might be no conflict between threads so use <= instead of <
     * when asserting 'successes'.
     */
    public static final int DEFAULT_WRITE_COUNT = (int) 1e3;

    private volatile SimpleClass _simple;

    private volatile int _intChangeCount, _lastInt, _int2ChangeCount, _lastInt2;

    static final PlatformConcurrentMap<VersionMap, Integer> _nullSources = new PlatformConcurrentMap<VersionMap, Integer>();

    private HashMap<Integer, Byte> _lastSource = new HashMap<Integer, Byte>();

    private HashSet<Integer> _lastSourceNotNull = new HashSet<Integer>();

    private final AtomicInteger _changeCallbackLast = new AtomicInteger();

    private final AtomicInteger _callbackCount = new AtomicInteger();

    private volatile CyclicBarrier _barrier;

    public Concurrent() {
        // Assert.assertTrue(Debug.ENABLED && Stats.ENABLED);
        reset();
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

    public void reset() {
        _simple = null;
        _intChangeCount = 0;
        _lastInt = 0;
        _int2ChangeCount = 0;
        _lastInt2 = 0;
        _lastSource.clear();
        _lastSourceNotNull.clear();
        _changeCallbackLast.set(0);
        _callbackCount.set(0);
    }

    @Test
    public void runSimple1() {
        int successes = run(null, 1, DEFAULT_WRITE_COUNT, 0);
        Assert.assertEquals(DEFAULT_WRITE_COUNT, successes);
    }

    @Test
    public void runSimple2() {
        int threadCount = 2;
        int successes = run(null, threadCount, DEFAULT_WRITE_COUNT, 0);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runSimple3() {
        int successes = run(null, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT);
    }

    @Test
    public void runSimple4() {
        int threadCount = 2;
        int successes = run(null, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runSimple5() {
        int threadCount = 2;
        int successes = run(null, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.INTERCEPT);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runUseLast1() {
        int successes = run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, 0);
        Assert.assertEquals(DEFAULT_WRITE_COUNT, successes);
    }

    @Test
    public void runUseLast2() {
        int successes = run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT);
    }

    @Test
    public void runUseLast3() {
        int threadCount = 2;
        int successes = run(Granularity.COALESCE, threadCount, DEFAULT_WRITE_COUNT, 0);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runUseLast4() {
        int threadCount = 2;
        int successes = run(Granularity.COALESCE, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runUseLast5() {
        int threadCount = 2;
        int successes = run(Granularity.COALESCE, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.INTERCEPT);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runProcessAll1() {
        int successes = run(Granularity.ALL, 1, 1, ConcurrentClient.USE_TWO_INTEGERS);
        Assert.assertEquals(1, successes);
    }

    @Test
    public void runProcessAll2() {
        int successes = run(Granularity.ALL, 1, DEFAULT_WRITE_COUNT, 0);
        Assert.assertEquals(DEFAULT_WRITE_COUNT, successes);
    }

    @Test
    public void runProcessAll3() {
        int threadCount = 2;
        int successes = run(Granularity.ALL, threadCount, DEFAULT_WRITE_COUNT, 0);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runProcessAll4() {
        int threadCount = 2;
        int successes = run(Granularity.ALL, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ABORTS);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runProcessAll5() {
        int threadCount = 2;
        int successes = run(Granularity.ALL, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.INTERCEPT);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runTwoIntegers1() {
        int successes = run(Granularity.ALL, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_TWO_INTEGERS);
        Assert.assertEquals(successes, DEFAULT_WRITE_COUNT);
    }

    @Test
    public void runTwoIntegers2() {
        int successes = run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_TWO_INTEGERS);
        Assert.assertEquals(successes, DEFAULT_WRITE_COUNT);
    }

    @Test
    public void runTwoIntegers3() {
        int threadCount = 2;
        int successes = run(Granularity.ALL, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_TWO_INTEGERS);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runTwoIntegers4() {
        int threadCount = 2;
        int successes = run(null, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.USE_ONE_INTEGER_PER_CLIENT);
        Assert.assertEquals(successes, DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runVoid1() {
        int successes = run(null, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.NO_WRITE);
        Assert.assertEquals(0, successes);
    }

    @Test
    public void runVoid2() {
        int successes = run(null, 8, DEFAULT_WRITE_COUNT, ConcurrentClient.NO_WRITE);
        Assert.assertEquals(0, successes);
    }

    @Test
    public void runCross1() {
        int threadCount = 2;
        int successes = run(null, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.CROSS);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runCross2() {
        int successes = run(Granularity.ALL, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.CROSS);
        Assert.assertTrue(successes == DEFAULT_WRITE_COUNT);
    }

    // @Test TODO
    public void runSource1() {
        int successes = run(Granularity.COALESCE, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.MERGE_BY_SOURCE);
        Assert.assertTrue(successes == DEFAULT_WRITE_COUNT);
    }

    // @Test TODO
    public void runSource2() {
        int threadCount = 2;
        int successes = run(Granularity.COALESCE, threadCount, DEFAULT_WRITE_COUNT, ConcurrentClient.MERGE_BY_SOURCE);
        Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);
    }

    @Test
    public void runTransfer1() {
        int successes = run(Granularity.ALL, 1, DEFAULT_WRITE_COUNT, ConcurrentClient.TRANSFER);
        Assert.assertTrue(successes == Transfer.TOTAL);
    }

    public static final class TestNotifier extends Notifier {

        private final boolean _bySource;

        private int _mapIndex;

        public TestNotifier(boolean bySource) {
            super(new AsyncOptions());

            _bySource = bySource;
        }

        @Override
        protected void onVisitingBranch(Visitor visitor) {
            super.onVisitingBranch(visitor);

            Transaction branch = visitor.getBranch();

            if (getGranularity(branch) == Granularity.COALESCE) {
                Snapshot snapshot = visitor.getSnapshot();
                Transaction transaction = branch.getOrCreateChild();
                snapshot = TransactionPublic.trim(snapshot, snapshot.getAcknowledgedIndex());
                branch.startFromPublic(transaction, snapshot);
                Transaction.setCurrentUnsafe(transaction);
            }
        }

        @Override
        protected void onVisitedBranch(Visitor visitor) {
            super.onVisitedBranch(visitor);

            if (getGranularity(visitor.getBranch()) == Granularity.COALESCE) {
                Transaction transaction = Transaction.getCurrent();
                Transaction.setCurrentUnsafe(null);
                transaction.reset();
                visitor.getBranch().recycleToPublic(transaction);
            }
        }

        public int getMapIndex() {
            return _mapIndex;
        }

        @Override
        protected boolean flushOnSourceChangeAndReturnIfDelayMerge(Visitor visitor) {
            super.flushOnSourceChangeAndReturnIfDelayMerge(visitor);
            // Skip, flush done before committing transaction
            return true;
        }

        @Override
        protected Action onVisitingMap(Visitor visitor, int mapIndex) {
            _mapIndex = mapIndex;
            return super.onVisitingMap(visitor, mapIndex);
        }
    }

    /**
     * Makes sure there are a few speculative maps in the queue.
     */
    public static final class TestAcknowledger extends Acknowledger {

        private boolean _started;

        private Transaction _trunk;

        public TestAcknowledger(Transaction trunk) {
            _trunk = trunk;
        }

        public void start() {
            _started = true;
        }

        @Override
        protected boolean requestRun() {
            if (_started) {
                Snapshot snapshot = _trunk.getSharedSnapshot();
                int speculatives = snapshot.getVersionMaps().length - snapshot.getAcknowledgedIndex();

                if (speculatives - 1 > 0) {
                    if (speculatives > 10 || PlatformAdapter.getRandomDouble() < 0.1) {
                        int newAck = snapshot.getAcknowledgedIndex() + 1 + (speculatives - 1) / 2;
                        Interceptor.ack(_trunk, snapshot.getVersionMaps()[newAck].getInterception().getId(), false);
                    }
                }
            }

            return true;
        }

        public static void flush(Transaction trunk) {
            Snapshot snapshot = trunk.getSharedSnapshot();
            Interceptor.ack(trunk, snapshot.getLast().getInterception().getId(), false);
        }
    }

    public int run(final Granularity granularity, final int threadCount, final int writeCount, final int clientFlags) {
        Transaction trunk = Site.getLocal().getTrunk();

        if (granularity == Granularity.ALL) {
            trunk = Site.getLocal().createTrunk(Granularity.ALL);
            Transaction.setDefaultTrunk(trunk);
        }

        setSimpleClass(new SimpleClass());

        if ((clientFlags & ConcurrentClient.TRANSFER) != 0)
            _simple.setInt0(Transfer.TOTAL);

        TestAcknowledger acknowledger = null;

        if ((clientFlags & ConcurrentClient.INTERCEPT) != 0) {
            Interceptor.intercept(trunk);
            acknowledger = new TestAcknowledger(trunk);
            acknowledger.register(trunk);

            // Create a few speculative maps
            for (int i = 0; i < 4; i++) {
                Transaction.runAsync(new Runnable() {

                    public void run() {
                        getSimpleClass().setText("");
                    }
                }, null);
            }
        }

        TestNotifier notifier = null;

        if (granularity != null) {
            boolean bySource = granularity == Granularity.COALESCE ? (clientFlags & ConcurrentClient.MERGE_BY_SOURCE) != 0 : false;
            notifier = new TestNotifier(bySource);
            OF.forceChangeNotifier(notifier);
        }

        final TestNotifier notifier_ = notifier;

        // Listen for commits

        FieldListener listener = null;

        if (granularity != null) {
            listener = new FieldListener() {

                private ThreadAssert _context;

                public void onFieldChanged(int fieldIndex) {
                    if (Debug.ENABLED) {
                        if (_context == null)
                            _context = ThreadAssert.getOrCreateCurrent();
                        else
                            Debug.assertion(_context == ThreadAssert.getOrCreateCurrent());
                    }

                    if ((clientFlags & ConcurrentClient.TRANSFER) != 0)
                        Transfer.assertTotal(_simple, granularity == Granularity.COALESCE);
                    else if ((clientFlags & ConcurrentClient.CROSS) == 0) {
                        if (fieldIndex == SimpleClass.INT0_INDEX) {
                            if (granularity == Granularity.ALL)
                                Assert.assertEquals(++_intChangeCount, _simple.getInt0());
                            else {
                                int value = _simple.getInt0();

                                if ((clientFlags & ConcurrentClient.MERGE_BY_SOURCE) != 0)
                                    Assert.assertTrue(value >= _lastInt);
                                else
                                    Assert.assertTrue(value > _lastInt);

                                _lastInt = value;
                            }
                        }

                        if (fieldIndex == SimpleClass.INT1_INDEX) {
                            if (granularity == Granularity.ALL)
                                Assert.assertEquals(++_int2ChangeCount, _simple.getInt1());
                            else {
                                int value = _simple.getInt1();

                                if ((clientFlags & ConcurrentClient.MERGE_BY_SOURCE) != 0)
                                    Assert.assertTrue(value >= _lastInt2);
                                else
                                    Assert.assertTrue(value > _lastInt2);

                                _lastInt2 = value;
                            }
                        }
                    }

                    if ((clientFlags & ConcurrentClient.MERGE_BY_SOURCE) != 0) {
                        Visitor visitor = notifier_.getVisitor();
                        VersionMap map = visitor.getSnapshot().getVersionMaps()[notifier_.getMapIndex()];
                        Source source = map.getSource();
                        int number;

                        if (source != null)
                            number = ((TestConnectionVersion) source.Connection).getValue();
                        else
                            number = _nullSources.remove(map);

                        Byte value = _lastSource.get(number);
                        byte lastId = value != null ? value : (byte) 0;
                        byte currentId;

                        if (source != null) {
                            currentId = source.InterceptionId;
                            _lastSourceNotNull.add(number);
                        } else {
                            currentId = _lastSourceNotNull.contains(number) ? (byte) (lastId + 1) : lastId;
                            _lastSourceNotNull.remove(number);
                        }

                        // System.out.println(number + ": " + lastId + " -> " +
                        // currentId);
                        Assert.assertTrue(currentId == lastId || currentId == (byte) (lastId + 1));
                        _lastSource.put(number, currentId);
                    }

                    _changeCallbackLast.set(_simple.getInt0() + _simple.getInt1() + _simple.getInt2());
                    _callbackCount.incrementAndGet();
                }
            };

            _simple.addListener(listener);
        }

        if (acknowledger != null)
            acknowledger.start();

        Log.write("");
        String message = "Starting " + threadCount + " threads, " + writeCount + " writes, " + "listener: " + granularity + ", ";
        message += "client flags: " + ConcurrentClient.writeClientFlags(clientFlags);
        Log.write(message);

        ArrayList<Thread> threads = startCommits(threadCount, writeCount, clientFlags);
        int successes = finish(notifier, threads, writeCount, listener, _simple, clientFlags);

        //

        if (acknowledger != null)
            acknowledger.unregister(trunk, null);

        if ((clientFlags & ConcurrentClient.INTERCEPT) != 0)
            Interceptor.reset(trunk);

        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
        _nullSources.clear();
        TestsHelper.assertIdleAndCleanup();
        return successes;
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

    public int finish(TestNotifier notifier, ArrayList<Thread> threads, int writeCount, FieldListener listener, SimpleClass object, int flags) {
        long start = System.nanoTime();
        joinThreads(threads);
        long time = System.nanoTime() - start;
        return finish(notifier, threads, time, writeCount, listener, object, _changeCallbackLast, false, 0, flags);
    }

    public static void joinThreads(ArrayList<Thread> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (java.lang.InterruptedException e) {
            }
        }
    }

    public static int finish(TestNotifier notifier, ArrayList<Thread> threads, long time, int writeCount, FieldListener listener, SimpleClass object, AtomicInteger changeCallbackLast, boolean distributed, int clientSideAborts,
            int clientFlags) {
        if ((clientFlags & ConcurrentClient.INTERCEPT) != 0)
            TestAcknowledger.flush(object.getTrunk());

        Granularity granularity = object.getTrunk().getGranularity();

        if (notifier != null) {
            notifier.flush();

            if (listener != null)
                object.removeListener(listener);

            OF.reset();
        }

        int successes;

        /*
         * If mode == all and multiple threads, it is possible that a snapshot with not
         * all integers at max arrive last, so we would have less than expected total.
         */
        if (listener == null || (granularity == Granularity.ALL && threads.size() > 1))
            successes = object.getInt0() + object.getInt1() + object.getInt2() + object.getInt3();
        else
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
                Assert.assertTrue(object.getInt2() > 0);

            Transfer.assertTotal(object, false);
        }

        if ((clientFlags & ConcurrentClient.NO_WRITE) == 0) {
            if (notifier == null || notifier.getGranularity(Site.getLocal().getTrunk()) == Granularity.ALL) {
                if (Stats.ENABLED && (!distributed || granularity == Granularity.ALL)) {
                    Assert.assertTrue(successes == Stats.getInstance().Committed.get());
                    long aborts = Stats.getInstance().LocallyAborted.get() + Stats.getInstance().UserAborted.get() + clientSideAborts;
                    Assert.assertTrue(threads.size() * writeCount - successes == aborts);
                }
            }
        }

        return successes;
    }

    public static void main(String[] args) throws Exception {
        Concurrent test = new Concurrent();

        for (int i = 0; i < 100; i++) {
            test.before();

            int threadCount = 2, writeCount = 1000;
            int flags = ConcurrentClient.INTERCEPT;
            int successes = test.run(Granularity.COALESCE, threadCount, writeCount, flags);
            // Assert.assertEquals(successes, writeCount * threadCount);
            // Assert.assertEquals(successes, Transfer.TOTAL);
            Assert.assertTrue(successes > 0 && successes < DEFAULT_WRITE_COUNT * threadCount);

            test.after();
            test.reset();
        }
    }
}
