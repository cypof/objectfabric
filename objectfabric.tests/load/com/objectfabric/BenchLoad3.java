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
import java.util.Random;


import com.objectfabric.Transaction;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.misc.Log;

public class BenchLoad3 implements Bench.Load {

    private final ArrayList<SimpleClass> _simples = new ArrayList<SimpleClass>();

    private int _objectCount, _opsPerObject;

    private float _writeRatio;

    public BenchLoad3(int objectCount, int addsPerObject, float writeRatio) {
        _objectCount = objectCount;
        _opsPerObject = addsPerObject;
        _writeRatio = writeRatio;

        for (int i = 0; i < _objectCount; i++)
            _simples.add(new SimpleClass());
    }

    public void reset() {
        for (int i = 0; i < _objectCount; i++)
            _simples.get(i).setInt0(0);
    }

    public void run(int number, int txPerThread, int writeRatio) {
        if (number == 0)
            Log.write("Load: " + _objectCount + " Objs, " + _opsPerObject + " Ops, Read ratio: " + _writeRatio);

        int reads = 0;
        Random rand = new Random();

        for (int tx = 0; tx < txPerThread; tx++) {
            boolean read = rand.nextFloat() > _writeRatio;
            Transaction transaction;

            if (read) {
                reads++;
                transaction = Transaction.start(Transaction.FLAG_NO_READS);
            } else
                transaction = Transaction.start();

            for (int i = 0; i < _objectCount; i++) {
                SimpleClass simple = _simples.get(i);

                for (int j = 0; j < _opsPerObject; j++) {
                    if (read)
                        simple.getInt0();
                    else
                        simple.setInt0(42);
                }
            }

            transaction.commit();
        }

        if (number == 0)
            Log.write("Reads: " + reads);
    }

    public void check() {
    }

    public int getOpsPerTx() {
        return _objectCount * _opsPerObject;
    }

    public long getSuccessfulOps() {
        return 0;
    }
}
