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

import java.io.IOException;

import com.objectfabric.Cross;
import com.objectfabric.FileStore;
import com.objectfabric.JdbmTest;
import com.objectfabric.Site;
import com.objectfabric.TestsHelper;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.ConflictDetection;
import com.objectfabric.Transaction.Consistency;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformFile;

public abstract class VMTest extends TestsHelper {

    public static final int FLAG_INTERCEPT = 1 << (Cross.FLAG_MAX_OFFSET + 1);

    public static final int FLAG_PROPAGATE = 1 << (Cross.FLAG_MAX_OFFSET + 2);

    public static final int FLAG_TRANSPARENT_EXECUTOR = 1 << (Cross.FLAG_MAX_OFFSET + 3);

    public static final int FLAG_PERSIST = 1 << (Cross.FLAG_MAX_OFFSET + 4);

    public static final int FLAG_MAX_OFFSET_2 = Cross.FLAG_MAX_OFFSET + 4;

    public static final int FLAG_ALL_2 = (1 << (FLAG_MAX_OFFSET_2 + 1)) - 1;

    public abstract void run(Granularity granularity, int clients, int flags);

    public static void writeStart(int granularity, int clients, int flags, String className) {
        String s = Cross.writeFlags(flags);

        if ((flags & FLAG_INTERCEPT) != 0)
            s += "INTERCEPT, ";

        if ((flags & FLAG_PROPAGATE) != 0)
            s += "PROPAGATE, ";

        if ((flags & FLAG_TRANSPARENT_EXECUTOR) != 0)
            s += "TRANSPARENT_EXECUTOR, ";

        if ((flags & FLAG_PERSIST) != 0)
            s += "PERSIST, ";

        Log.write("");
        Log.write("Starting " + className + ": " + Granularity.values()[granularity] + ", clients: " + clients + ", flags: " + s);
    }

    public static Transaction createTrunk(int granularity, int flags) {
        Transaction trunk;
        FileStore store = null;

        if ((flags & VMTest.FLAG_PERSIST) != 0) {
            PlatformFile.mkdir(JdbmTest.TEMP);

            PlatformFile.deleteFileIfExists(JdbmTest.FILE);
            PlatformFile.deleteFileIfExists(JdbmTest.FILE + FileStore.LOG_EXTENSION);

            try {
                store = new FileStore(JdbmTest.FILE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (granularity == Transaction.DEFAULT_GRANULARITY.ordinal() && store == null)
            trunk = Site.getLocal().getTrunk();
        else {
            ConflictDetection conflict = Transaction.DEFAULT_CONFLICT_DETECTION;
            Consistency consistency = Transaction.DEFAULT_CONSISTENCY;
            trunk = Site.getLocal().createTrunk(conflict, consistency, Granularity.values()[granularity], store);
        }

        Transaction.setDefaultTrunk(trunk);
        return trunk;
    }
}
