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

import com.objectfabric.misc.FileLog;
import com.objectfabric.misc.Log;

public class ScriptMap {

    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private static final int TX_PER_THREAD = (int) 1e6;

    private static final void run(Bench.Load load, int threads, int txPerThread, int readRatio) {
        Bench bench = new Bench();

        bench.before();
        bench.run(load, threads, txPerThread, readRatio);
        bench.after();

        Log.write("");
        Log.write("");
    }

    public static void main(String[] args) throws Exception {
        run(new BenchLoadMap(10, false, false), 1, TX_PER_THREAD, 50);
        run(new BenchLoadMap(10, true, false), 1, TX_PER_THREAD, 50);
        run(new BenchLoadMap(10, false, true), 1, TX_PER_THREAD, 50);
        run(new BenchLoadMap(10, true, true), 1, TX_PER_THREAD, 50);

        for (int run = 0; run < 4; run++) {
            Log.add(new FileLog("log - " + run + ".txt"));

            for (int c = 0; c < 2; c++) {
                boolean conflicting = c == 1;

                for (int t = 0; t < 2; t++) {
                    boolean transactional = t == 0;

                    Bench.reset();

                    for (int txLength = 1; txLength <= 1000; txLength *= 10)
                        for (int r = 0; r <= 100; r += 25)
                            run(new BenchLoadMap(txLength, transactional, conflicting), MAX_THREADS, TX_PER_THREAD / (txLength / 10 + 1), r);

                    Script.writeStats(run, null, Bench.OpsPerTxs, Bench.WriteRatios, Bench.OpsPerSec);
                }
            }
        }
    }
}
