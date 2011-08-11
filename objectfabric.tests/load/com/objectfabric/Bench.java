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


import com.objectfabric.Concurrent;
import com.objectfabric.Stats;
import com.objectfabric.TestsHelper;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;

public class Bench extends TestsHelper {

    public interface Load {

        void reset();

        void run(int number, int txPerThread, int writeRatio);

        void check();

        int getOpsPerTx();

        long getSuccessfulOps();
    }

    private final AtomicInteger _changeCallbackLast = new AtomicInteger(-1);

    private volatile CyclicBarrier _barrier;

    public static final ArrayList<Integer> ThreadCounts = new ArrayList<Integer>();

    public static final ArrayList<Integer> OpsPerTxs = new ArrayList<Integer>();

    public static final ArrayList<Integer> TxPerSec = new ArrayList<Integer>();

    public static final ArrayList<Integer> OpsPerSec = new ArrayList<Integer>();

    public static final ArrayList<Integer> WriteRatios = new ArrayList<Integer>();

    public static final void reset() {
        ThreadCounts.clear();
        OpsPerTxs.clear();
        TxPerSec.clear();
        OpsPerSec.clear();
        WriteRatios.clear();
    }

    public void run(final Load load, final int threadCount, final int txPerThread, final int writeRatio) {
        if (Stats.ENABLED)
            Log.write("!! STATS ENABLED !!");

        if (Debug.ENABLED)
            Log.write("!! DEBUG ENABLED !!");

        load.reset();

        ArrayList<Thread> threads = new ArrayList<Thread>();

        // Listen for commits

        Log.write("Starting " + threadCount + " threads, " + txPerThread + " tx per thread");

        _changeCallbackLast.set(0);

        _barrier = new CyclicBarrier(threadCount);

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

                        load.run(_i, txPerThread, writeRatio);
                    } catch (java.lang.InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            };

            threads.add(thread);
            thread.start();
        }

        long start = System.nanoTime();
        Concurrent.joinThreads(threads);
        long end = System.nanoTime();

        long tx = threadCount * txPerThread;
        long ops = (long) threadCount * txPerThread * load.getOpsPerTx();
        long success = load.getSuccessfulOps() / 1000;
        double txPerSec = tx * 1e9 / (end - start);
        double opsPerSec = ops * 1e9 / (end - start);
        double successPerSec = success * 1e9 / (end - start);
        Log.write((tx / 1000) + " K transaction, " + (ops / 1000) + " K ops, " + success + " K successful ops");
        Log.write((int) (txPerSec / 1000) + " K transaction/s, " + (int) (opsPerSec / 1000) + " K ops/s, " + (int) successPerSec + " K successful ops/s");
        Log.write("Time: " + (end - start) / 1e9);

        //

        ThreadCounts.add(threadCount);
        OpsPerTxs.add(load.getOpsPerTx());
        TxPerSec.add((int) txPerSec);
        OpsPerSec.add((int) opsPerSec);
        WriteRatios.add(writeRatio);

        if (Stats.ENABLED)
            Stats.getInstance().writeAndReset();
    }

    public static void main(String[] args) throws Exception {
        Bench test = new Bench();

        // Load load = new BenchLoad2(1, 100000);
        // Load load = new BenchLoad1();
        Load load = new BenchLoadMap(1000, true, true);

        for (int i = 0; i < 10; i++) {
            test.before();
            test.run(load, 2, (int) 1e3, 0);
            test.after();
        }
    }
}
