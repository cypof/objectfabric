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

package com.objectfabric.vm;


import com.objectfabric.Cross;
import com.objectfabric.Site;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;

public abstract class VMTest extends TestsHelper {

    public static final int FLAG_INTERCEPT = 1 << 3;

    public static final int FLAG_PROPAGATE = 1 << 4;

    public static final int FLAG_TRANSPARENT_EXECUTOR = 1 << 5;

    public static final int FLAG_ALL_2 = (1 << 6) - 1;

    static {
        Debug.assertAlways(Cross.FLAG_ALL + 1 == FLAG_INTERCEPT);
    }

    public abstract void run(Granularity granularity, int clients, int flags);

    public static void writeStart(int granularity, int clients, int flags, String className) {
        String s = Cross.writeFlags(flags);

        if ((flags & FLAG_INTERCEPT) != 0)
            s += "INTERCEPT, ";

        if ((flags & FLAG_PROPAGATE) != 0)
            s += "PROPAGATE, ";

        Log.write("");
        Log.write("Starting " + className + ": " + Granularity.values()[granularity] + ", clients: " + clients + ", flags: " + s);
    }

    public static Transaction createTrunk(int granularity) {
        Transaction trunk;

        if (granularity == Granularity.ALL.ordinal())
            trunk = Site.getLocal().createTrunk(Granularity.ALL);
        else
            trunk = Site.getLocal().getTrunk();

        Transaction.setDefaultTrunk(trunk);
        return trunk;
    }
}
