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


import com.objectfabric.Transaction;
import com.objectfabric.generated.SimpleClass;

public class BenchLoad4 implements Bench.Load {

    private static final int OBJECT_COUNT = 100;

    private static final int ADD_PER_OBJECT = 100;

    private final ArrayList<SimpleClass> _simples = new ArrayList<SimpleClass>();

    public BenchLoad4() {
        for (int i = 0; i < OBJECT_COUNT; i++)
            _simples.add(new SimpleClass());
    }

    public void reset() {
        for (int i = 0; i < OBJECT_COUNT; i++)
            _simples.get(i).setInt0(0);
    }

    public void run(int number, int txPerThread, int writeRatio) {
        for (int tx = 0; tx < txPerThread; tx++) {
            Transaction transaction = Transaction.start();

            for (int i = 0; i < OBJECT_COUNT; i++) {
                SimpleClass simple = _simples.get(i);

                for (int j = 0; j < ADD_PER_OBJECT; j++)
                    simple.setInt0(simple.getInt0() + 1);
            }

            transaction.commitAsync(null);
        }
    }

    public void check() {
    }

    public int getOpsPerTx() {
        return OBJECT_COUNT * ADD_PER_OBJECT * 2;
    }

    public long getSuccessfulOps() {
        long total = 0;

        for (int i = 0; i < OBJECT_COUNT; i++)
            total += _simples.get(i).getInt0();

        return total * 2;
    }
}
