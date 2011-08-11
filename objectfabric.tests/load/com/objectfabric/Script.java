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

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import com.objectfabric.misc.FileLog;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.Utils;

public class Script {

    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private static final int TX_PER_THREAD = (int) 1e6;

    private static final void run(Bench.Load load, int threads, int txPerThread, int writeRatio) {
        Bench bench = new Bench();

        bench.before();
        bench.run(load, threads, txPerThread, writeRatio);
        bench.after();

        Log.write("");
        Log.write("");
    }

    public static void main(String[] args) throws Exception {
        for (int run = 0; run < 4; run++) {
            Log.add(new FileLog("runs/log - " + run + ".txt"));

            run(new BenchLoad2(10, 1), 1, TX_PER_THREAD, 12);

            Bench.reset();

            for (int o = 1; o <= 1000; o *= 10)
                for (int i = 1; i <= MAX_THREADS; i++)
                    run(new BenchLoad2(o, 1), i, TX_PER_THREAD / (o / 10 + 1), 12);

            writeStats(run, "Objects - TxPerSec", Bench.OpsPerTxs, Bench.ThreadCounts, Bench.TxPerSec);
            writeStats(run, "Objects - OpsPerSec", Bench.OpsPerTxs, Bench.ThreadCounts, Bench.OpsPerSec);

            Bench.reset();

            for (int o = 1; o <= 1000000; o *= 10)
                for (int i = 1; i <= MAX_THREADS; i++)
                    run(new BenchLoad2(1, o), i, TX_PER_THREAD * 10 / (o / 10 + 1), 12);

            writeStats(run, "Ops - TxPerSec", Bench.OpsPerTxs, Bench.ThreadCounts, Bench.TxPerSec);
            writeStats(run, "Ops - OpsPerSec", Bench.OpsPerTxs, Bench.ThreadCounts, Bench.OpsPerSec);

            // for (float r = 0; r < 1.01; r += 0.1)
            // run(new BenchLoad3(40, 1000, r), MAX_THREADS, TX_PER_THREAD /
            // 1000, r);

            writeStats(run, "Ratios - TxPerSec", Bench.WriteRatios, Bench.ThreadCounts, Bench.TxPerSec);
            writeStats(run, "Ratios - OpsPerSec", Bench.WriteRatios, Bench.ThreadCounts, Bench.OpsPerSec);
        }
    }

    @SuppressWarnings("unchecked")
    static void writeStats(int run, String name, ArrayList<Integer> x, ArrayList<Integer> y, ArrayList<Integer> z) throws Exception {
        File file = new File("runs/stats - " + run + ".csv");
        FileWriter writer = new FileWriter(file, true);
        final String SEP = "\t";

        if (name != null)
            writer.write(Utils.NEW_LINE + name + SEP + Utils.NEW_LINE + Utils.NEW_LINE);

        HashMap<Integer, HashMap<Object, Integer>> map = getMap(x, y, z);
        ArrayList<Integer> columns = new ArrayList<Integer>(map.keySet());
        ArrayList rows = new ArrayList(map.get(columns.get(0)).keySet());

        Collections.sort(columns);
        Collections.sort(rows);

        writer.write(SEP);

        for (int c = 0; c < columns.size(); c++)
            writer.write(columns.get(c) + SEP);

        writer.write(Utils.NEW_LINE);

        for (int r = 0; r < rows.size(); r++) {
            writer.write(rows.get(r) + SEP);

            for (int c = 0; c < columns.size(); c++)
                writer.write(map.get(columns.get(c)).get(rows.get(r)) + SEP);

            writer.write(Utils.NEW_LINE);
        }

        writer.close();
    }

    static HashMap<Integer, HashMap<Object, Integer>> getMap(ArrayList<Integer> x, ArrayList<Integer> y, ArrayList<Integer> z) {
        HashMap<Integer, HashMap<Object, Integer>> map = new HashMap<Integer, HashMap<Object, Integer>>();

        for (int i = 0; i < y.size(); i++) {
            HashMap<Object, Integer> column = map.get(y.get(i));

            if (column == null)
                map.put(y.get(i), column = new HashMap<Object, Integer>());

            column.put(x.get(i), z.get(i));
        }

        return map;
    }
}
