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

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.objectfabric.TMap;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.misc.Log;

public class BenchLoadMap implements Bench.Load {

    private final TMap<Integer, Integer> _tMap = new TMap<Integer, Integer>();

    private final ConcurrentHashMap<Integer, Integer> _concurrentMap = new ConcurrentHashMap<Integer, Integer>();

    private final int _txLength;

    private final boolean _transactional;

    private final boolean _conflicting;

    public BenchLoadMap(int txLength, boolean transactional, boolean conflicting) {
        _txLength = txLength;
        _transactional = transactional;
        _conflicting = conflicting;
    }

    public void reset() {
        _tMap.clear();
        _concurrentMap.clear();
    }

    @SuppressWarnings("null")
    public void run(int number, int txPerThread, int ratio) {
        Log.write("Load: " + _txLength + " Ops, Ratio: " + ratio + (_transactional ? ", Transactional" : "") + (_conflicting ? ", Conflicting, " : ""));

        int reads = 0, retries = 0, offset = number * 56485;
        Random rand = new Random();

        for (int tx = 0; tx < txPerThread; tx++) {
            int key = (tx + offset) & 1023;

            if (_conflicting && ratio != 0) {
                key = (tx + offset) & 7;
                key = (int) (key * ((100 - ratio) / 100.0));
            }

            // Log.write("" + key);

            for (;;) {
                boolean read = rand.nextInt(100) >= ratio;

                if (read)
                    reads++;

                Transaction transaction = null;

                if (_transactional) {
                    // if (read && !_conflicting)
                    // transaction =
                    // Transaction.start(Transaction.FLAG_NO_READS);
                    // else
                    transaction = Transaction.start();
                }

                for (int j = 0; j < _txLength; j++) {
                    if (_conflicting || read) {
                        if (_transactional)
                            _tMap.get(key);
                        else
                            _concurrentMap.get(key);
                    }

                    if (_conflicting || !read) {
                        if (_transactional)
                            _tMap.putOnly(key, 78);
                        else
                            _concurrentMap.put(key, 78);
                    }
                }

                CommitStatus status = CommitStatus.SUCCESS;

                if (_transactional)
                    status = transaction.commit();

                if (status == CommitStatus.SUCCESS)
                    break;

                retries++;
            }
        }

        Log.write("Reads: " + reads + ", Retries: " + retries);
    }

    public void check() {
    }

    public int getOpsPerTx() {
        return _txLength;
    }

    public long getSuccessfulOps() {
        return 0;
    }
}
