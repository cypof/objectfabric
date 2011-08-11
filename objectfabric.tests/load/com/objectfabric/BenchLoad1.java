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


import com.objectfabric.Transaction;
import com.objectfabric.generated.SimpleClass;

public class BenchLoad1 implements Bench.Load {

    private final SimpleClass _simple = new SimpleClass();

    public void reset() {
        _simple.setInt0(0);
    }

    public void run(int number, int txPerThread, int ratio) {
        for (int tx = 0; tx < txPerThread; tx++) {
            Transaction transaction = Transaction.start();
            _simple.setInt0(_simple.getInt0() + 1);
            transaction.commitAsync(null);
        }
    }

    public void check() {
    }

    public int getOpsPerTx() {
        return 2;
    }

    public long getSuccessfulOps() {
        return _simple.getInt0() * 2;
    }
}
